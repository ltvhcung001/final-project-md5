package com.omnichannel.payment.service;

import com.omnichannel.common.event.EventNames;
import com.omnichannel.common.event.OrderCreatedEvent;
import com.omnichannel.common.event.PaymentFailedEvent;
import com.omnichannel.common.event.PaymentSucceededEvent;
import com.omnichannel.common.exception.AppException;
import com.omnichannel.common.exception.ErrorCode;
import com.omnichannel.common.security.Roles;
import com.omnichannel.payment.dto.PaymentDtos.PaymentResponse;
import com.omnichannel.payment.dto.PaymentDtos.ReconciliationRow;
import com.omnichannel.payment.entity.PaymentTransaction;
import com.omnichannel.payment.entity.PaymentTransaction.Status;
import com.omnichannel.payment.gateway.MockProvider;
import com.omnichannel.payment.gateway.MomoProvider;
import com.omnichannel.payment.gateway.PaymentProvider;
import com.omnichannel.payment.gateway.VnPayProvider;
import com.omnichannel.payment.outbox.OutboxService;
import com.omnichannel.payment.repository.PaymentTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    public enum Outcome { APPLIED, DUPLICATE, NOT_FOUND, AMOUNT_MISMATCH }

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final PaymentTransactionRepository txs;
    private final OutboxService outbox;
    private final Map<String, PaymentProvider> providers;
    private final MockProvider mock;

    public PaymentService(PaymentTransactionRepository txs, OutboxService outbox, List<PaymentProvider> providers,
                          MockProvider mock) {
        this.txs = txs;
        this.outbox = outbox;
        this.providers = providers.stream().collect(Collectors.toMap(PaymentProvider::name, Function.identity()));
        this.mock = mock;
    }

    /** Saga step 2: OrderCreated -> create a transaction and the payment URL. Idempotent per order. */
    @Transactional
    public void createForOrder(OrderCreatedEvent event) {
        if (txs.findByOrderId(event.orderId()).isPresent()) {
            return;
        }
        String method = event.paymentMethod() == null ? "VNPAY" : event.paymentMethod();
        PaymentTransaction tx = new PaymentTransaction(event.orderId(), event.userId(), event.totalAmount(), method);
        try {
            PaymentProvider provider = providers.get(method);
            if (provider == null) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Unsupported payment method " + method);
            }
            tx.setPaymentUrl(provider.createPaymentUrl(tx));
        } catch (RuntimeException e) {
            // cannot collect money for this order => fail fast so the order service releases the stock
            log.warn("Cannot create {} payment for order {}: {}", method, event.orderId(), e.getMessage());
            tx.complete(Status.FAILED, null, e.getMessage());
            txs.save(tx);
            publishFailed(tx);
            return;
        }
        txs.save(tx);
    }

    /** Applies a verified provider result. Duplicate callbacks are acknowledged but change nothing. */
    @Transactional
    public Outcome applyResult(UUID orderId, boolean success, String providerTxnId, BigDecimal amount, String reason) {
        PaymentTransaction tx = txs.findByOrderIdForUpdate(orderId).orElse(null);
        if (tx == null) {
            return Outcome.NOT_FOUND;
        }
        if (amount != null && tx.getAmount().compareTo(amount) != 0) {
            log.error("Amount mismatch for order {}: expected {} got {}", orderId, tx.getAmount(), amount);
            return Outcome.AMOUNT_MISMATCH;
        }
        if (!tx.complete(success ? Status.SUCCEEDED : Status.FAILED, providerTxnId, success ? null : reason)) {
            return Outcome.DUPLICATE;
        }
        if (success) {
            outbox.add(orderId.toString(), EventNames.PAYMENT_SUCCEEDED, new PaymentSucceededEvent(
                    UUID.randomUUID(), orderId, tx.getId(), tx.getProvider(), providerTxnId, tx.getAmount(),
                    Instant.now()));
        } else {
            publishFailed(tx);
        }
        return Outcome.APPLIED;
    }

    /** Sandbox shortcut: the customer "pays" on our own mock page. */
    @Transactional
    public Outcome completeMock(String userId, String roles, UUID orderId, boolean success) {
        if (!mock.isEnabled()) {
            throw new AppException(ErrorCode.NOT_FOUND);
        }
        PaymentTransaction tx = authorised(userId, roles, orderId);
        if (!"MOCK".equals(tx.getProvider())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Order is not paid with the MOCK provider");
        }
        return applyResult(orderId, success, "MOCK-" + UUID.randomUUID(), tx.getAmount(),
                success ? null : "Declined in mock provider");
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(String userId, String roles, UUID orderId) {
        return PaymentResponse.of(authorised(userId, roles, orderId));
    }

    /** Back-office refund. Records the refund; calling the provider's refund API is a manual step in the sandbox. */
    @Transactional
    public PaymentResponse refund(UUID orderId) {
        PaymentTransaction tx = txs.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
        if (!tx.refund()) {
            throw new AppException(ErrorCode.CONFLICT, "Only SUCCEEDED payments can be refunded");
        }
        log.info("Refunded payment of order {} ({} {})", orderId, tx.getAmount(), tx.getProvider());
        return PaymentResponse.of(tx);
    }

    /** Daily reconciliation report: counts and sums by status, to compare with the provider's statement. */
    @Transactional(readOnly = true)
    public List<ReconciliationRow> reconcile(LocalDate day) {
        Instant from = day.atStartOfDay(VN).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(VN).toInstant();
        return txs.summarise(from, to).stream()
                .map(r -> new ReconciliationRow(r[0].toString(), ((Number) r[1]).longValue(), (BigDecimal) r[2]))
                .toList();
    }

    private PaymentTransaction authorised(String userId, String roles, UUID orderId) {
        PaymentTransaction tx = txs.findByOrderId(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
        if (!tx.getUserId().equals(userId) && !Roles.isAdmin(roles)) {
            throw new AppException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        return tx;
    }

    private void publishFailed(PaymentTransaction tx) {
        outbox.add(tx.getOrderId().toString(), EventNames.PAYMENT_FAILED, new PaymentFailedEvent(
                UUID.randomUUID(), tx.getOrderId(), tx.getId(), tx.getFailureReason(), Instant.now()));
    }
}
