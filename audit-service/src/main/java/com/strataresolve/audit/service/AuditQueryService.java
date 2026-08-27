package com.strataresolve.audit.service;

import com.strataresolve.audit.domain.AuditEvent;
import com.strataresolve.audit.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for querying audit events with flexible filtering.
 *
 * <p>Supports filtering by:
 * <ul>
 *   <li>Date range (from/to)</li>
 *   <li>Event type</li>
 *   <li>Acting user</li>
 *   <li>Target entity (type and ID)</li>
 * </ul>
 *
 * <p>Access is restricted to Property Managers, Committee Members, and Platform Admins
 * (enforced at the controller level).
 */
@Service
public class AuditQueryService {

    private static final Logger log = LoggerFactory.getLogger(AuditQueryService.class);

    private final AuditEventRepository auditEventRepository;

    public AuditQueryService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * Retrieves all audit events for a property, ordered by creation time descending.
     *
     * @param propertyId the property to query
     * @return list of audit events
     */
    @Transactional(readOnly = true)
    public List<AuditEvent> findByPropertyId(UUID propertyId) {
        log.debug("Querying audit events for property={}", propertyId);
        return auditEventRepository.findByPropertyIdOrderByCreatedAtDesc(propertyId);
    }

    /**
     * Retrieves audit events for a property within a date range.
     *
     * @param propertyId the property to query
     * @param from       start of date range (inclusive)
     * @param to         end of date range (inclusive)
     * @return list of audit events within the date range
     */
    @Transactional(readOnly = true)
    public List<AuditEvent> findByDateRange(UUID propertyId, Instant from, Instant to) {
        log.debug("Querying audit events for property={} dateRange=[{}, {}]", propertyId, from, to);
        return auditEventRepository.findByPropertyIdAndDateRange(propertyId, from, to);
    }

    /**
     * Retrieves audit events for a property filtered by event type.
     *
     * @param propertyId the property to query
     * @param eventType  the event type to filter by (e.g., TICKET_CREATED, STATUS_CHANGED)
     * @return list of audit events matching the event type
     */
    @Transactional(readOnly = true)
    public List<AuditEvent> findByEventType(UUID propertyId, String eventType) {
        log.debug("Querying audit events for property={} eventType={}", propertyId, eventType);
        return auditEventRepository.findByPropertyIdAndEventTypeOrderByCreatedAtDesc(propertyId, eventType);
    }

    /**
     * Retrieves audit events for a property filtered by acting user.
     *
     * @param propertyId  the property to query
     * @param actingUserId the user who performed the actions
     * @return list of audit events by the specified user
     */
    @Transactional(readOnly = true)
    public List<AuditEvent> findByActingUser(UUID propertyId, UUID actingUserId) {
        log.debug("Querying audit events for property={} actingUser={}", propertyId, actingUserId);
        return auditEventRepository.findByPropertyIdAndActingUserIdOrderByCreatedAtDesc(propertyId, actingUserId);
    }

    /**
     * Retrieves audit events for a property filtered by target entity.
     *
     * @param propertyId       the property to query
     * @param targetEntityType the type of target entity (e.g., Ticket, Property)
     * @param targetEntityId   the ID of the target entity
     * @return list of audit events targeting the specified entity
     */
    @Transactional(readOnly = true)
    public List<AuditEvent> findByTargetEntity(UUID propertyId, String targetEntityType, UUID targetEntityId) {
        log.debug("Querying audit events for property={} target={}/{}", propertyId, targetEntityType, targetEntityId);
        return auditEventRepository.findByPropertyIdAndTargetEntityTypeAndTargetEntityIdOrderByCreatedAtDesc(
                propertyId, targetEntityType, targetEntityId);
    }
}
