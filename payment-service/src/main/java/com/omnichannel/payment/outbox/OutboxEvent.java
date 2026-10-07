package com.omnichannel.payment.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Same outbox pattern as the order service: stored with the payment result, published by a scheduler. */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "routing_key", nullable = false)
    private String routingKey;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(String aggregateId, String routingKey, String payload) {
        this.aggregateId = aggregateId;
        this.routingKey = routingKey;
        this.payload = payload;
    }

    public UUID getId() { return id; }
    public String getRoutingKey() { return routingKey; }
    public String getPayload() { return payload; }
    public void markPublished() { this.publishedAt = Instant.now(); }
}
