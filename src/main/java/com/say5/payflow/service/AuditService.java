package com.say5.payflow.service;

import com.say5.payflow.persistence.AuditLog;
import com.say5.payflow.persistence.AuditLogRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final AuditLogRepo repo;

    public AuditService(AuditLogRepo repo) {
        this.repo = repo;
    }

    /** Append-only. REQUIRES_NEW so the audit row survives even if the
     * calling transaction rolls back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String actorType, String actorId, String action,
                       String resourceType, String resourceId,
                       String requestHash, String result, String detailJson) {
        repo.save(new AuditLog(actorType, actorId, action,
            resourceType, resourceId, requestHash, result, detailJson));
    }
}
