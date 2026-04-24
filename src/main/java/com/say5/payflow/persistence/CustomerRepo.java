package com.say5.payflow.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CustomerRepo extends JpaRepository<Customer, UUID> {
    List<Customer> findByMerchantId(UUID merchantId);
}
