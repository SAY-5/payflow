package com.say5.payflow.persistence;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
@IdClass(IdempotencyKey.Id.class)
public class IdempotencyKey {
    @jakarta.persistence.Id
    @Column(name = "merchant_id")
    private UUID merchantId;

    @jakarta.persistence.Id
    @Column(name = "key")
    private String key;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body", columnDefinition = "jsonb")
    private String responseBody;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    /**
     * Set when the row is reserved; if completion never lands by this
     * deadline the row is considered abandoned and the next caller is
     * allowed to reclaim it. Without this, a crash between reserve and
     * complete would leave the (merchant, key) pair permanently in
     * 409-Conflict state.
     */
    @Column(name = "processing_expires_at")
    private OffsetDateTime processingExpiresAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected IdempotencyKey() {}

    public IdempotencyKey(UUID merchantId, String key, String requestHash,
                          OffsetDateTime processingExpiresAt) {
        this.merchantId = merchantId;
        this.key = key;
        this.requestHash = requestHash;
        this.processingExpiresAt = processingExpiresAt;
    }

    public void complete(int status, String body) {
        this.responseStatus = status;
        this.responseBody = body;
        this.completedAt = OffsetDateTime.now();
        this.processingExpiresAt = null;
    }

    /** Reset the fence + request hash so a stuck key can be retried. */
    public void resetFence(String requestHash, OffsetDateTime newDeadline) {
        this.requestHash = requestHash;
        this.processingExpiresAt = newDeadline;
        this.responseStatus = null;
        this.responseBody = null;
    }

    public UUID getMerchantId() { return merchantId; }
    public String getKey() { return key; }
    public String getRequestHash() { return requestHash; }
    public Integer getResponseStatus() { return responseStatus; }
    public String getResponseBody() { return responseBody; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public OffsetDateTime getProcessingExpiresAt() { return processingExpiresAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public boolean isComplete() { return completedAt != null; }

    /** True once the in-flight reservation should be considered abandoned. */
    public boolean isAbandoned(OffsetDateTime now) {
        return !isComplete()
            && processingExpiresAt != null
            && now.isAfter(processingExpiresAt);
    }

    public static class Id implements Serializable {
        private UUID merchantId;
        private String key;

        public Id() {}
        public Id(UUID merchantId, String key) {
            this.merchantId = merchantId;
            this.key = key;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Id other)) return false;
            return Objects.equals(merchantId, other.merchantId)
                && Objects.equals(key, other.key);
        }

        @Override
        public int hashCode() { return Objects.hash(merchantId, key); }
    }
}
