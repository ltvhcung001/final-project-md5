package com.omnichannel.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_transaction", indexes = @Index(name = "idx_payment_created", columnList = "created_at"))
public class PaymentTransaction {

    public enum Status { PENDING, SUCCEEDED, FAILED, REFUNDED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Unique: a redelivered OrderCreated event must not create a second transaction. */
    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_txn_id")
    private String providerTxnId;

    @Column(name = "payment_url", length = 2000)
    private String paymentUrl;

    @Column(name = "failure_reason")
    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private long version;

    protected PaymentTransaction() {
    }

    public PaymentTransaction(UUID orderId, String userId, BigDecimal amount, String provider) {
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
        this.provider = provider;
    }

    /** Idempotent: webhooks may be delivered several times, only the first result counts. */
    public boolean complete(Status result, String providerTxnId, String failureReason) {
        if (status != Status.PENDING) {
            return false;
        }
        this.status = result;
        this.providerTxnId = providerTxnId;
        this.failureReason = failureReason;
        this.updatedAt = Instant.now();
        return true;
    }

    public boolean refund() {
        if (status != Status.SUCCEEDED) {
            return false;
        }
        this.status = Status.REFUNDED;
        this.updatedAt = Instant.now();
        return true;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public String getUserId() { return userId; }
    public BigDecimal getAmount() { return amount; }
    public String getProvider() { return provider; }
    public String getProviderTxnId() { return providerTxnId; }
    public String getPaymentUrl() { return paymentUrl; }
    public void setPaymentUrl(String paymentUrl) { this.paymentUrl = paymentUrl; }
    public String getFailureReason() { return failureReason; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
