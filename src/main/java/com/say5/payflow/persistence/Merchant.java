package com.say5.payflow.persistence;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "merchants")
public class Merchant {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "api_key_hash", nullable = false, unique = true)
    private String apiKeyHash;

    /**
     * Indexable, non-secret prefix of SHA-256(rawKey) so /auth/token can
     * find a candidate merchant in O(1) before doing the real PBKDF2
     * comparison. Storing it adds no security risk because it's not
     * brute-forceable to the raw key (it's a 24-hex prefix of a SHA-256,
     * not a full hash).
     */
    @Column(name = "api_key_fingerprint")
    private String apiKeyFingerprint;

    @Column(name = "webhook_secret", nullable = false)
    private String webhookSecret;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected Merchant() {}

    public Merchant(UUID id, String name, String apiKeyHash, String apiKeyFingerprint,
                    String webhookSecret) {
        this.id = id;
        this.name = name;
        this.apiKeyHash = apiKeyHash;
        this.apiKeyFingerprint = apiKeyFingerprint;
        this.webhookSecret = webhookSecret;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getApiKeyHash() { return apiKeyHash; }
    public String getApiKeyFingerprint() { return apiKeyFingerprint; }
    public String getWebhookSecret() { return webhookSecret; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
