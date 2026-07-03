package com.strataresolve.vendor.dto;

import com.strataresolve.ticket.domain.Category;
import com.strataresolve.ticket.domain.Ticket;
import com.strataresolve.ticket.domain.TicketStatus;

/**
 * Minimal ticket information DTO exposed to vendor users.
 * Vendors should only see reference number, title, category, and status
 * for tickets associated with their assigned work orders.
 * They should NOT see full ticket details like description, location,
 * resident info, or SLA data.
 *
 * <p>Validates: Requirements 13.5, 18.4
 */
public record VendorTicketSummaryResponse(
        String referenceNumber,
        String title,
        Category category,
        TicketStatus status
) {
    /**
     * Creates a VendorTicketSummaryResponse from a Ticket entity,
     * exposing only the minimal fields a vendor needs.
     */
    public static VendorTicketSummaryResponse from(Ticket ticket) {
        return new VendorTicketSummaryResponse(
                ticket.getReferenceNumber(),
                ticket.getTitle(),
                ticket.getCategory(),
                ticket.getStatus()
        );
    }
}
