package com.say5.payflow.persistence;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "charges")
public class Charge {
    @Id
    private UUID id;

    @Column(name = "intent_id", nullable = false)
    private UUID intentId;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Column(nullable = false)
    private String status; // pending | succeeded | failed

    @Column(name = "provider_charge_id", unique = true)
    private String providerChargeId;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "failure_message")
    private String failureMessage;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected Charge() {}

    public Charge(UUID id, UUID intentId, int attemptNo, String status) {
        this.id = id;
        this.intentId = intentId;
        this.attemptNo = attemptNo;
        this.status = status;
    }

    public UUID getId() { return id; }
    public UUID getIntentId() { return intentId; }
    public int getAttemptNo() { return attemptNo; }
    public String getStatus() { return status; }
    public String getProviderChargeId() { return providerChargeId; }
    public String getFailureCode() { return failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void markSucceeded(String providerChargeId) {
        this.status = "succeeded";
        this.providerChargeId = providerChargeId;
    }

    public void markFailed(String code, String message) {
        this.status = "failed";
        this.failureCode = code;
        this.failureMessage = message;
    }
}
