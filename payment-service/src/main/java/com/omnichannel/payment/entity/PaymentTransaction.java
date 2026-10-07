package com.omnichannel.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_transaction")
public class PaymentTransaction {

    public enum Status { PENDING, SUCCEEDED, FAILED, REFUNDED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Unique: a redelivered OrderCreated event must not create a second transaction. */
    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String provider;

    @Column(name = "provider_txn_id")
    private String providerTxnId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected PaymentTransaction() {
    }

    public PaymentTransaction(UUID orderId, BigDecimal amount, String provider) {
        this.orderId = orderId;
        this.amount = amount;
        this.provider = provider;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public BigDecimal getAmount() { return amount; }
    public String getProvider() { return provider; }
    public String getProviderTxnId() { return providerTxnId; }
    public Status getStatus() { return status; }

    /** Idempotent: webhooks may be delivered several times, only the first result counts. */
    public boolean complete(Status result, String providerTxnId) {
        if (status != Status.PENDING) {
            return false;
        }
        this.status = result;
        this.providerTxnId = providerTxnId;
        return true;
    }
}
