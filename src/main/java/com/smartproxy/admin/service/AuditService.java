package com.smartproxy.admin.service;

import com.smartproxy.storage.entity.AuditEventEntity;
import com.smartproxy.storage.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service managing audit trails for administrative mutations and security actions.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional
    public void recordEvent(String principal, String action, String resource, String status, String ipAddress, String details) {
        try {
            AuditEventEntity event = new AuditEventEntity(principal, action, resource, status, ipAddress, details);
            auditEventRepository.save(event);
            log.info("AUDIT EVENT: [{}] Principal='{}' Action='{}' Resource='{}' Status='{}' IP='{}'",
                    event.getTimestamp(), principal, action, resource, status, ipAddress);
        } catch (Exception e) {
            log.warn("Failed to persist audit event: {}", e.getMessage());
        }
    }

    public List<AuditEventEntity> getRecentEvents() {
        return auditEventRepository.findTop50ByOrderByTimestampDesc();
    }
}
