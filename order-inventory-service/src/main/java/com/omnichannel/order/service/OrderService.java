package com.omnichannel.order.service;

import com.omnichannel.common.api.PageResponse;
import com.omnichannel.common.event.EventNames;
import com.omnichannel.common.event.OrderCreatedEvent;
import com.omnichannel.common.event.OrderItemEvent;
import com.omnichannel.common.event.OrderStatus;
import com.omnichannel.common.event.OrderStatusChangedEvent;
import com.omnichannel.common.exception.AppException;
import com.omnichannel.common.exception.ErrorCode;
import com.omnichannel.common.security.Roles;
import com.omnichannel.order.dto.OrderDtos.ItemRequest;
import com.omnichannel.order.dto.OrderDtos.OrderResponse;
import com.omnichannel.order.dto.OrderDtos.PlaceOrderRequest;
import com.omnichannel.order.entity.Order;
import com.omnichannel.order.entity.OrderItem;
import com.omnichannel.order.entity.StockReservation;
import com.omnichannel.order.grpc.ProductClient;
import com.omnichannel.order.outbox.OutboxService;
import com.omnichannel.order.repository.InventoryRepository;
import com.omnichannel.order.repository.OrderRepository;
import com.omnichannel.order.repository.StockReservationRepository;
import com.omnichannel.product.grpc.ProductReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final Set<String> PAYMENT_METHODS = Set.of("VNPAY", "MOMO", "MOCK");

    private final OrderRepository orders;
    private final InventoryRepository inventory;
    private final StockReservationRepository reservations;
    private final ProductClient products;
    private final StockGuard stockGuard;
    private final OutboxService outbox;
    private final TransactionTemplate tx;

    public OrderService(OrderRepository orders, InventoryRepository inventory,
                        StockReservationRepository reservations, ProductClient products, StockGuard stockGuard,
                        OutboxService outbox, PlatformTransactionManager txManager) {
        this.orders = orders;
        this.inventory = inventory;
        this.reservations = reservations;
        this.products = products;
        this.stockGuard = stockGuard;
        this.outbox = outbox;
        this.tx = new TransactionTemplate(txManager);
    }

    /**
     * Saga step 1: snapshot product data over gRPC, guard stock in Redis, reserve it in PostgreSQL, store the order
     * as PENDING and write OrderCreated to the outbox in the same transaction.
     * Replays with the same Idempotency-Key return the original order.
     */
    public OrderResponse placeOrder(String userId, String email, String idempotencyKey, PlaceOrderRequest req) {
        var existing = findExisting(userId, idempotencyKey);
        if (existing != null) {
            return existing;
        }

        String method = req.paymentMethod() == null ? "VNPAY" : req.paymentMethod().toUpperCase();
        if (!PAYMENT_METHODS.contains(method)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Unsupported payment method " + method);
        }

        Map<String, Integer> wanted = new TreeMap<>();
        for (ItemRequest i : req.items()) {
            wanted.merge(i.sku(), i.quantity(), Integer::sum);
        }
        Map<String, ProductReply> catalog = products.getProducts(wanted.keySet());
        for (String sku : wanted.keySet()) {
            ProductReply p = catalog.get(sku);
            if (p == null || !p.getActive()) {
                throw new AppException(ErrorCode.PRODUCT_NOT_FOUND, "Unknown or inactive SKU " + sku);
            }
        }

        boolean guarded = stockGuard.tryReserve(wanted);
        try {
            return tx.execute(status -> persist(userId, email, idempotencyKey, method, req, wanted, catalog));
        } catch (RuntimeException e) {
            if (guarded) {
                stockGuard.release(wanted);
            }
            if (e instanceof DataIntegrityViolationException) {
                var raced = findExisting(userId, idempotencyKey); // concurrent retry won the unique constraint
                if (raced != null) {
                    return raced;
                }
            }
            throw e;
        }
    }

    private OrderResponse persist(String userId, String email, String idempotencyKey, String method,
                                  PlaceOrderRequest req, Map<String, Integer> wanted,
                                  Map<String, ProductReply> catalog) {
        // sorted SKU order everywhere => consistent row locking order => no deadlocks
        for (var e : wanted.entrySet()) {
            if (inventory.reserve(e.getKey(), e.getValue()) == 0) {
                throw new AppException(ErrorCode.OUT_OF_STOCK, "Insufficient stock for " + e.getKey());
            }
        }

        UUID orderId = UUID.randomUUID();
        BigDecimal total = BigDecimal.ZERO;
        List<OrderItem> items = new java.util.ArrayList<>();
        for (var e : wanted.entrySet()) {
            ProductReply p = catalog.get(e.getKey());
            BigDecimal price = new BigDecimal(p.getPrice());
            total = total.add(price.multiply(BigDecimal.valueOf(e.getValue())));
            items.add(new OrderItem(e.getKey(), p.getName(), price, e.getValue()));
        }

        Order order = new Order(orderId, userId, email, idempotencyKey, total, method,
                req.shipping().receiverName(), req.shipping().phone(), req.shipping().address());
        items.forEach(order::addItem);
        orders.saveAndFlush(order);

        wanted.forEach((sku, qty) -> reservations.save(new StockReservation(orderId, sku, qty)));

        outbox.add(orderId.toString(), EventNames.ORDER_CREATED, new OrderCreatedEvent(
                UUID.randomUUID(), orderId, userId, email, total, order.getCurrency(), method,
                items.stream().map(i -> new OrderItemEvent(i.getSku(), i.getProductName(), i.getQuantity(),
                        i.getUnitPrice())).toList(),
                Instant.now()));
        publishStatus(order, null, OrderStatus.PENDING);
        return OrderResponse.of(order);
    }

    /** Saga: payment succeeded -> CONFIRMED and the reserved stock is permanently deducted. Idempotent. */
    @Transactional
    public void onPaymentSucceeded(UUID orderId) {
        Order order = orders.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING) {
            log.warn("Ignoring PaymentSucceeded for order {} (status {}); refund may be needed", orderId,
                    order == null ? "missing" : order.getStatus());
            return;
        }
        for (StockReservation r : reservations.findByOrderId(orderId)) {
            if (r.getStatus() == StockReservation.Status.RESERVED) {
                inventory.commit(r.getSku(), r.getQuantity());
                r.setStatus(StockReservation.Status.COMMITTED);
            }
        }
        transition(order, OrderStatus.CONFIRMED);
    }

    /** Saga compensation: payment failed / timeout / user cancel -> CANCELLED and the stock is released. Idempotent. */
    @Transactional
    public void cancel(UUID orderId, String reason) {
        Order order = orders.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING) {
            return;
        }
        Map<String, Integer> released = new LinkedHashMap<>();
        for (StockReservation r : reservations.findByOrderId(orderId)) {
            if (r.getStatus() == StockReservation.Status.RESERVED) {
                inventory.release(r.getSku(), r.getQuantity());
                r.setStatus(StockReservation.Status.RELEASED);
                released.merge(r.getSku(), r.getQuantity(), Integer::sum);
            }
        }
        order.setCancelReason(reason);
        transition(order, OrderStatus.CANCELLED);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                stockGuard.release(released);
            }
        });
        log.info("Order {} cancelled: {}", orderId, reason);
    }

    @Transactional
    public OrderResponse cancelByUser(String userId, String roles, UUID orderId) {
        Order order = loadAuthorised(userId, roles, orderId);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATE, "Only PENDING orders can be cancelled");
        }
        cancel(orderId, "Cancelled by customer");
        return OrderResponse.of(orders.findById(orderId).orElseThrow());
    }

    /** Back-office progress: CONFIRMED -> SHIPPING -> COMPLETED. */
    @Transactional
    public OrderResponse advance(UUID orderId, OrderStatus next) {
        if (next != OrderStatus.SHIPPING && next != OrderStatus.COMPLETED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Only SHIPPING or COMPLETED can be set manually");
        }
        Order order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        transition(order, next);
        return OrderResponse.of(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(String userId, String roles, UUID orderId) {
        return OrderResponse.of(loadAuthorised(userId, roles, orderId));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listMine(String userId, int page, int size) {
        var result = orders.findByUserIdOrderByCreatedAtDesc(userId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        return new PageResponse<>(result.map(OrderResponse::of).getContent(), result.getNumber(),
                result.getSize(), result.getTotalElements());
    }

    public List<UUID> findExpiredPending(Instant cutoff, int limit) {
        return orders.findIdsByStatusOlderThan(OrderStatus.PENDING, cutoff, PageRequest.of(0, limit));
    }

    private OrderResponse findExisting(String userId, String idempotencyKey) {
        return tx.execute(s -> orders.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .map(OrderResponse::of).orElse(null));
    }

    private Order loadAuthorised(String userId, String roles, UUID orderId) {
        Order order = orders.findById(orderId).orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.getUserId().equals(userId) && !Roles.isAdmin(roles)) {
            throw new AppException(ErrorCode.ORDER_NOT_FOUND); // do not leak other users' order ids
        }
        return order;
    }

    private void transition(Order order, OrderStatus next) {
        OrderStatus old = order.getStatus();
        OrderStateMachine.assertTransition(old, next);
        order.changeStatus(next);
        publishStatus(order, old, next);
    }

    private void publishStatus(Order order, OrderStatus old, OrderStatus next) {
        outbox.add(order.getId().toString(), EventNames.ORDER_STATUS_CHANGED, new OrderStatusChangedEvent(
                UUID.randomUUID(), order.getId(), order.getUserId(), order.getUserEmail(), old, next, Instant.now()));
    }
}
