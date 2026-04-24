package com.say5.payflow.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChargeRepo extends JpaRepository<Charge, UUID> {
    List<Charge> findByIntentIdOrderByAttemptNoAsc(UUID intentId);
    Optional<Charge> findByProviderChargeId(String providerChargeId);
    long countByIntentId(UUID intentId);
}
