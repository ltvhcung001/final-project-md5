package com.omnichannel.notification.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("delivery_logs")
public class DeliveryLog {

    public enum Status { SENT, FAILED }

    @Id
    private String id;
    private String eventId;
    private String recipient;
    private String subject;
    private Status status;
    private String error;
    private Instant createdAt = Instant.now();

    public DeliveryLog() {
    }

    public DeliveryLog(String eventId, String recipient, String subject, Status status, String error) {
        this.eventId = eventId;
        this.recipient = recipient;
        this.subject = subject;
        this.status = status;
        this.error = error;
    }

    public String getId() { return id; }
    public String getEventId() { return eventId; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public Status getStatus() { return status; }
    public String getError() { return error; }
    public Instant getCreatedAt() { return createdAt; }
}
