package com.say5.payflow.persistence;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class Customer {
    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    private String email;
    private String name;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected Customer() {}

    public Customer(UUID id, UUID merchantId, String email, String name) {
        this.id = id;
        this.merchantId = merchantId;
        this.email = email;
        this.name = name;
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
