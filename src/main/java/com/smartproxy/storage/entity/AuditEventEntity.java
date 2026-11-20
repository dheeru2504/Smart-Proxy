package com.smartproxy.storage.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Instant timestamp = Instant.now();

    @Column(nullable = false, length = 100)
    private String principal;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(nullable = false, length = 255)
    private String resource;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(columnDefinition = "TEXT")
    private String details;

    public AuditEventEntity() {}

    public AuditEventEntity(String principal, String action, String resource, String status, String ipAddress, String details) {
        this.timestamp = Instant.now();
        this.principal = principal;
        this.action = action;
        this.resource = resource;
        this.status = status;
        this.ipAddress = ipAddress;
        this.details = details;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public String getPrincipal() { return principal; }
    public void setPrincipal(String principal) { this.principal = principal; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
