package com.say5.payflow.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepo extends JpaRepository<Refund, UUID> {
    List<Refund> findByIntentIdOrderByCreatedAtDesc(UUID intentId);
    Optional<Refund> findByProviderRefundId(String providerRefundId);

    @Query("select coalesce(sum(r.amountCents), 0) from Refund r " +
           "where r.intentId = :intentId and r.status = 'succeeded'")
    long sumSucceededAmountForIntent(UUID intentId);
}
