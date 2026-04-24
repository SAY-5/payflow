package com.say5.payflow.persistence;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "webhook_events",
       uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_event_id"}))
public class WebhookEvent {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String provider;

    @Column(name = "provider_event_id", nullable = false)
    private String providerEventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(nullable = false)
    private String status;

    private String error;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt = OffsetDateTime.now();

    protected WebhookEvent() {}

    public WebhookEvent(UUID id, String provider, String providerEventId, String eventType,
                        String payload, String status) {
        this.id = id;
        this.provider = provider;
        this.providerEventId = providerEventId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status;
    }

    public UUID getId() { return id; }
    public String getProvider() { return provider; }
    public String getProviderEventId() { return providerEventId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public String getStatus() { return status; }
    public String getError() { return error; }
    public OffsetDateTime getReceivedAt() { return receivedAt; }

    public void markProcessed() { this.status = "processed"; }
    public void markError(String err) { this.status = "error"; this.error = err; }
    public void markDuplicate() { this.status = "duplicate"; }
}
