package com.say5.payflow.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRepo extends JpaRepository<IdempotencyKey, IdempotencyKey.Id> {
    Optional<IdempotencyKey> findByMerchantIdAndKey(UUID merchantId, String key);
}
