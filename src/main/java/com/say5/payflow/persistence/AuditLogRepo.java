package com.say5.payflow.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuditLogRepo extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByResourceTypeAndResourceIdOrderByAtDesc(String resourceType, String resourceId);
}
