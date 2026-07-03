package com.strataresolve.ticket.dto;

import com.strataresolve.ticket.domain.Priority;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for changing a ticket's priority.
 * Restricted to Property Managers.
 *
 * @param priority the new priority for the ticket
 */
public record ChangePriorityRequest(
        @NotNull(message = "Priority is required")
        Priority priority
) {
}
