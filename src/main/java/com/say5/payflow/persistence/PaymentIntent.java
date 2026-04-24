package com.say5.payflow.persistence;

import com.say5.payflow.domain.PaymentStatus;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_intents")
public class PaymentIntent {
    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String provider = "stripe";

    @Column(name = "provider_id", unique = true)
    private String providerId;

    private String description;

    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected PaymentIntent() {}

    public PaymentIntent(UUID id, UUID merchantId, UUID customerId, long amountCents,
                         String currency, PaymentStatus status, String description,
                         String metadataJson) {
        this.id = id;
        this.merchantId = merchantId;
        this.customerId = customerId;
        this.amountCents = amountCents;
        this.currency = currency;
        this.status = status.wire();
        this.description = description;
        if (metadataJson != null && !metadataJson.isBlank()) this.metadataJson = metadataJson;
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public UUID getCustomerId() { return customerId; }
    public long getAmountCents() { return amountCents; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return PaymentStatus.fromWire(status); }
    public String getProvider() { return provider; }
    public String getProviderId() { return providerId; }
    public String getDescription() { return description; }
    public String getMetadataJson() { return metadataJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setStatus(PaymentStatus s) {
        if (getStatus().isTerminal() && getStatus() != s) {
            throw new IllegalStateException(
                "payment intent " + id + " is already in terminal status " + status);
        }
        this.status = s.wire();
        this.updatedAt = OffsetDateTime.now();
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
        this.updatedAt = OffsetDateTime.now();
    }
}
