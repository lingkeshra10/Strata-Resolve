package com.strataresolve.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for reopening a ticket.
 * The reason field is mandatory and must describe why the ticket needs to be reopened.
 */
public record ReopenTicketRequest(
        @NotBlank(message = "Reason for reopening is required")
        @Size(max = 1000, message = "Reason must not exceed 1000 characters")
        String reason
) {
}
