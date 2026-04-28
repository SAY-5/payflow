package com.say5.payflow.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface MerchantRepo extends JpaRepository<Merchant, UUID> {
    Optional<Merchant> findByApiKeyHash(String apiKeyHash);
    Optional<Merchant> findByApiKeyFingerprint(String apiKeyFingerprint);
}
