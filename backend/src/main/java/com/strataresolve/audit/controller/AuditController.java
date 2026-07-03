package com.strataresolve.audit.controller;

import com.strataresolve.audit.dto.AuditEventResponse;
import com.strataresolve.audit.service.AuditQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for querying the audit trail.
 *
 * <p>Access is restricted to Property Managers, Committee Members, and Platform Admins.
 * Supports filtering by date range, event type, acting user, or target entity.
 */
@RestController
@RequestMapping("/api/properties/{propertyId}/audit-trail")
public class AuditController {

    private final AuditQueryService auditQueryService;

    public AuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    /**
     * Queries the audit trail for a property with optional filters.
     *
     * <p>Supported query parameters:
     * <ul>
     *   <li>{@code from} - Start of date range (ISO-8601 instant)</li>
     *   <li>{@code to} - End of date range (ISO-8601 instant)</li>
     *   <li>{@code eventType} - Filter by event type (e.g., TICKET_CREATED, STATUS_CHANGED)</li>
     *   <li>{@code actingUserId} - Filter by the user who performed the action</li>
     *   <li>{@code targetEntityType} - Filter by target entity type (used with targetEntityId)</li>
     *   <li>{@code targetEntityId} - Filter by target entity ID (used with targetEntityType)</li>
     * </ul>
     *
     * <p>If no filters are provided, returns all audit events for the property.
     * Filters are mutually exclusive — if multiple are provided, precedence is:
     * date range > event type > acting user > target entity.
     *
     * @param propertyId       the property to query audit events for
     * @param from             optional start of date range
     * @param to               optional end of date range
     * @param eventType        optional event type filter
     * @param actingUserId     optional acting user filter
     * @param targetEntityType optional target entity type filter
     * @param targetEntityId   optional target entity ID filter
     * @return list of matching audit events ordered by creation time descending
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('PROPERTY_MANAGER', 'COMMITTEE_MEMBER', 'PLATFORM_ADMIN')")
    public ResponseEntity<List<AuditEventResponse>> queryAuditTrail(
            @PathVariable UUID propertyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) UUID actingUserId,
            @RequestParam(required = false) String targetEntityType,
            @RequestParam(required = false) UUID targetEntityId) {

        List<AuditEventResponse> response;

        if (from != null && to != null) {
            response = auditQueryService.findByDateRange(propertyId, from, to).stream()
                    .map(AuditEventResponse::from)
                    .toList();
        } else if (eventType != null) {
            response = auditQueryService.findByEventType(propertyId, eventType).stream()
                    .map(AuditEventResponse::from)
                    .toList();
        } else if (actingUserId != null) {
            response = auditQueryService.findByActingUser(propertyId, actingUserId).stream()
                    .map(AuditEventResponse::from)
                    .toList();
        } else if (targetEntityType != null && targetEntityId != null) {
            response = auditQueryService.findByTargetEntity(propertyId, targetEntityType, targetEntityId).stream()
                    .map(AuditEventResponse::from)
                    .toList();
        } else {
            response = auditQueryService.findByPropertyId(propertyId).stream()
                    .map(AuditEventResponse::from)
                    .toList();
        }

        return ResponseEntity.ok(response);
    }
}
