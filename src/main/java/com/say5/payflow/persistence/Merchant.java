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

    @Column(name = "webhook_secret", nullable = false)
    private String webhookSecret;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected Merchant() {}

    public Merchant(UUID id, String name, String apiKeyHash, String webhookSecret) {
        this.id = id;
        this.name = name;
        this.apiKeyHash = apiKeyHash;
        this.webhookSecret = webhookSecret;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getApiKeyHash() { return apiKeyHash; }
    public String getWebhookSecret() { return webhookSecret; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
