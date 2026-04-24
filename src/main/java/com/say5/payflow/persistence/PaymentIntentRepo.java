package com.say5.payflow.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface PaymentIntentRepo extends JpaRepository<PaymentIntent, UUID> {
    Page<PaymentIntent> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId, Pageable p);
    Optional<PaymentIntent> findByProviderId(String providerId);
    long countByMerchantIdAndStatus(UUID merchantId, String status);

    @Query("select coalesce(sum(pi.amountCents), 0) from PaymentIntent pi " +
           "where pi.merchantId = :merchantId and pi.status = 'succeeded'")
    long sumSucceededAmount(UUID merchantId);
}
