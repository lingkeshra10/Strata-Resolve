package com.strataresolve.ticket.dto;

import com.strataresolve.ticket.domain.TicketStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for transitioning a ticket's status.
 *
 * @param targetStatus the desired new status for the ticket
 * @param reason       optional reason for the transition (required for reject, cancel, reopen)
 */
public record TransitionTicketStatusRequest(
        @NotNull(message = "Target status is required")
        TicketStatus targetStatus,

        @Size(max = 1000, message = "Reason must not exceed 1000 characters")
        String reason
) {
}
