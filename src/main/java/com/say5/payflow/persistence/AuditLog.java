package com.say5.payflow.persistence;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "audit_log")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_type", nullable = false)
    private String actorType;

    @Column(name = "actor_id")
    private String actorId;

    @Column(nullable = false)
    private String action;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "resource_id")
    private String resourceId;

    @Column(name = "request_hash")
    private String requestHash;

    @Column(nullable = false)
    private String result;

    @Column(columnDefinition = "jsonb")
    private String detail;

    @Column(nullable = false)
    private OffsetDateTime at = OffsetDateTime.now();

    protected AuditLog() {}

    public AuditLog(String actorType, String actorId, String action,
                    String resourceType, String resourceId,
                    String requestHash, String result, String detail) {
        this.actorType = actorType;
        this.actorId = actorId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.requestHash = requestHash;
        this.result = result;
        this.detail = detail;
    }

    public Long getId() { return id; }
    public String getActorType() { return actorType; }
    public String getActorId() { return actorId; }
    public String getAction() { return action; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public String getRequestHash() { return requestHash; }
    public String getResult() { return result; }
    public String getDetail() { return detail; }
    public OffsetDateTime getAt() { return at; }
}
