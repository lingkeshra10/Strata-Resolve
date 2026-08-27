package com.strataresolve.audit.service;

import com.strataresolve.common.event.TicketCreatedEvent;
import com.strataresolve.common.event.StatusChangedEvent;
import com.strataresolve.common.event.AssignmentCreatedEvent;
import com.strataresolve.common.event.PriorityChangedEvent;
import com.strataresolve.common.event.PropertyConfigChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listens to all domain events and creates corresponding audit records.
 *
 * <p>This listener handles:
 * <ul>
 *   <li>{@link TicketCreatedEvent} — Ticket creation</li>
 *   <li>{@link StatusChangedEvent} — Ticket status changes</li>
 *   <li>{@link AssignmentCreatedEvent} — Ticket assignments</li>
 *   <li>{@link PriorityChangedEvent} — Priority changes</li>
 *   <li>{@link PropertyConfigChangedEvent} — Property, block, unit, vendor, SLA, and membership changes</li>
 * </ul>
 *
 * <p>Each handler extracts the relevant audit data from the event and delegates
 * to {@link AuditService} for persistence. Previous and new values are stored
 * as JSON strings for structured data, or plain strings for simple values.
 */
@Component
public class AuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);

    private final AuditService auditService;

    public AuditEventListener(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Handles ticket creation events.
     * Records the new ticket details (category, priority, reference number).
     */
    @EventListener
    public void onTicketCreated(TicketCreatedEvent event) {
        log.debug("Audit: handling TicketCreatedEvent for ticket={}", event.getTicketId());

        String newValue = String.format(
                "{\"referenceNumber\":\"%s\",\"category\":\"%s\",\"priority\":\"%s\"}",
                event.getReferenceNumber(), event.getCategory(), event.getPriority());

        auditService.createAuditEvent(
                event.getPropertyId(),
                "TICKET_CREATED",
                event.getActingUserId(),
                "Ticket",
                event.getTicketId(),
                null,
                newValue
        );
    }

    /**
     * Handles ticket status change events.
     * Records the previous and new status values.
     */
    @EventListener
    public void onStatusChanged(StatusChangedEvent event) {
        log.debug("Audit: handling StatusChangedEvent for ticket={}, {} -> {}",
                event.getTicketId(), event.getPreviousStatus(), event.getNewStatus());

        String previousValue = String.format("{\"status\":\"%s\"}", event.getPreviousStatus());
        String newValue = String.format("{\"status\":\"%s\"%s}",
                event.getNewStatus(),
                event.getReason() != null ? ",\"reason\":\"" + escapeJson(event.getReason()) + "\"" : "");

        auditService.createAuditEvent(
                event.getPropertyId(),
                "STATUS_CHANGED",
                event.getActingUserId(),
                "Ticket",
                event.getTicketId(),
                previousValue,
                newValue
        );
    }

    /**
     * Handles ticket assignment events.
     * Records the assignment type and assignee.
     */
    @EventListener
    public void onAssignmentCreated(AssignmentCreatedEvent event) {
        log.debug("Audit: handling AssignmentCreatedEvent for ticket={}, assignee={}",
                event.getTicketId(), event.getAssigneeId());

        String newValue = String.format(
                "{\"assigneeId\":\"%s\",\"assignmentType\":\"%s\"}",
                event.getAssigneeId(), event.getAssignmentType());

        auditService.createAuditEvent(
                event.getPropertyId(),
                "ASSIGNMENT_CREATED",
                event.getActingUserId(),
                "Ticket",
                event.getTicketId(),
                null,
                newValue
        );
    }

    /**
     * Handles priority change events.
     * Records the previous and new priority values.
     */
    @EventListener
    public void onPriorityChanged(PriorityChangedEvent event) {
        log.debug("Audit: handling PriorityChangedEvent for ticket={}, {} -> {}",
                event.getTicketId(), event.getPreviousPriority(), event.getNewPriority());

        String previousValue = String.format("{\"priority\":\"%s\"}", event.getPreviousPriority());
        String newValue = String.format("{\"priority\":\"%s\"}", event.getNewPriority());

        auditService.createAuditEvent(
                event.getPropertyId(),
                "PRIORITY_CHANGED",
                event.getActingUserId(),
                "Ticket",
                event.getTicketId(),
                previousValue,
                newValue
        );
    }

    /**
     * Handles property configuration change events.
     * This covers property, block, unit, vendor, SLA policy, and membership changes.
     */
    @EventListener
    public void onPropertyConfigChanged(PropertyConfigChangedEvent event) {
        log.debug("Audit: handling PropertyConfigChangedEvent for entity={}/{}",
                event.getEntityType(), event.getEntityId());

        String eventType = "PROPERTY_CONFIG_CHANGED_" + event.getAction().toUpperCase();

        auditService.createAuditEvent(
                event.getPropertyId(),
                eventType,
                event.getActingUserId(),
                event.getEntityType(),
                event.getEntityId(),
                event.getPreviousValue(),
                event.getNewValue()
        );
    }

    /**
     * Escapes special characters in a string for safe JSON embedding.
     */
    private String escapeJson(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
