package com.strataresolve.vendor.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for creating a new work order assigned to a vendor for a ticket.
 */
public record CreateWorkOrderRequest(
        @NotNull(message = "Ticket ID is required")
        UUID ticketId,

        @NotNull(message = "Vendor ID is required")
        UUID vendorId
) {
}
