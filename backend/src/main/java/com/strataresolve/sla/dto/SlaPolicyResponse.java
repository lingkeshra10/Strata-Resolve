package com.strataresolve.sla.dto;

import com.strataresolve.sla.domain.SlaPolicy;
import com.strataresolve.ticket.domain.Category;
import com.strataresolve.ticket.domain.Priority;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO representing an SLA policy.
 */
public record SlaPolicyResponse(
        UUID id,
        UUID propertyId,
        Category category,
        Priority priority,
        Integer acknowledgementHours,
        Integer resolutionHours,
        Boolean isDefault,
        Instant createdAt,
        Instant updatedAt
) {
    public static SlaPolicyResponse from(SlaPolicy policy) {
        return new SlaPolicyResponse(
                policy.getId(),
                policy.getPropertyId(),
                policy.getCategory(),
                policy.getPriority(),
                policy.getAcknowledgementHours(),
                policy.getResolutionHours(),
                policy.getIsDefault(),
                policy.getCreatedAt(),
                policy.getUpdatedAt()
        );
    }
}
