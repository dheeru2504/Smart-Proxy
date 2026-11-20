package com.smartproxy.admin.controller;

import com.smartproxy.admin.service.AuditService;
import com.smartproxy.storage.entity.AuditEventEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * Endpoint for querying administrative and security audit logs.
 */
@RestController
@RequestMapping("/api/admin/audit-events")
public class AdminAuditEventController {

    private final AuditService auditService;

    public AdminAuditEventController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public Mono<ResponseEntity<List<AuditEventEntity>>> getRecentAuditEvents() {
        return Mono.fromCallable(auditService::getRecentEvents)
                .subscribeOn(Schedulers.boundedElastic())
                .map(ResponseEntity::ok);
    }
}
