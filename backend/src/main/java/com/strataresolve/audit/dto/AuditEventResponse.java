package com.strataresolve.audit.dto;

import com.strataresolve.audit.domain.AuditEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO representing an audit event.
 */
public record AuditEventResponse(
        UUID id,
        UUID propertyId,
        String eventType,
        UUID actingUserId,
        String targetEntityType,
        UUID targetEntityId,
        String previousValue,
        String newValue,
        Instant createdAt
) {
    /**
     * Creates an AuditEventResponse from an AuditEvent entity.
     */
    public static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(
                event.getId(),
                event.getPropertyId(),
                event.getEventType(),
                event.getActingUserId(),
                event.getTargetEntityType(),
                event.getTargetEntityId(),
                event.getPreviousValue(),
                event.getNewValue(),
                event.getCreatedAt()
        );
    }
}
