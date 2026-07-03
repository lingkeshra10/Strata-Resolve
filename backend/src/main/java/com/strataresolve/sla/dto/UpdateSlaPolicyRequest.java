package com.strataresolve.sla.dto;

import com.strataresolve.ticket.domain.Category;
import com.strataresolve.ticket.domain.Priority;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for updating an existing SLA policy.
 */
public record UpdateSlaPolicyRequest(
        Category category,

        Priority priority,

        @NotNull(message = "Acknowledgement hours is required")
        @Min(value = 1, message = "Acknowledgement hours must be at least 1")
        Integer acknowledgementHours,

        @NotNull(message = "Resolution hours is required")
        @Min(value = 1, message = "Resolution hours must be at least 1")
        Integer resolutionHours,

        @NotNull(message = "isDefault flag is required")
        Boolean isDefault
) {
}
