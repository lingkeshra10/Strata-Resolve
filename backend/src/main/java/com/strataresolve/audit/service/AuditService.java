package com.strataresolve.audit.service;

import com.strataresolve.audit.domain.AuditEvent;
import com.strataresolve.audit.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for creating audit event records.
 *
 * <p>This service only exposes creation operations, enforcing the append-only
 * nature of the audit trail. No update or delete operations are provided.
 *
 * <p>Audit events are created within the same transaction as the triggering
 * domain event, ensuring consistency between the business operation and its
 * audit record.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * Creates a new audit event record. This is the only mutation operation
     * available for audit events—no update or delete is permitted.
     *
     * @param propertyId       the property context (tenant)
     * @param eventType        the type of event (e.g., TICKET_CREATED, STATUS_CHANGED)
     * @param actingUserId     the user who performed the action
     * @param targetEntityType the type of entity affected (e.g., Ticket, Property)
     * @param targetEntityId   the ID of the affected entity
     * @param previousValue    the previous state as JSON (nullable, e.g., for creation events)
     * @param newValue         the new state as JSON (nullable, e.g., for deletion events)
     * @return the persisted audit event
     */
    @Transactional
    public AuditEvent createAuditEvent(UUID propertyId, String eventType, UUID actingUserId,
                                       String targetEntityType, UUID targetEntityId,
                                       String previousValue, String newValue) {
        AuditEvent auditEvent = new AuditEvent(
                propertyId, eventType, actingUserId,
                targetEntityType, targetEntityId,
                previousValue, newValue
        );

        AuditEvent saved = auditEventRepository.save(auditEvent);

        log.info("Audit event created: type={}, actor={}, target={}/{}, property={}",
                eventType, actingUserId, targetEntityType, targetEntityId, propertyId);

        return saved;
    }
}
