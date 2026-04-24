package com.say5.payflow.persistence;

import com.say5.payflow.domain.RefundStatus;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "refunds")
public class Refund {
    @Id
    private UUID id;

    @Column(name = "intent_id", nullable = false)
    private UUID intentId;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(nullable = false)
    private String status;

    private String reason;

    @Column(name = "provider_refund_id", unique = true)
    private String providerRefundId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected Refund() {}

    public Refund(UUID id, UUID intentId, long amountCents, RefundStatus status, String reason) {
        this.id = id;
        this.intentId = intentId;
        this.amountCents = amountCents;
        this.status = status.wire();
        this.reason = reason;
    }

    public UUID getId() { return id; }
    public UUID getIntentId() { return intentId; }
    public long getAmountCents() { return amountCents; }
    public RefundStatus getStatus() { return RefundStatus.fromWire(status); }
    public String getReason() { return reason; }
    public String getProviderRefundId() { return providerRefundId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setStatus(RefundStatus s) {
        this.status = s.wire();
        this.updatedAt = OffsetDateTime.now();
    }

    public void setProviderRefundId(String providerRefundId) {
        this.providerRefundId = providerRefundId;
        this.updatedAt = OffsetDateTime.now();
    }
}
