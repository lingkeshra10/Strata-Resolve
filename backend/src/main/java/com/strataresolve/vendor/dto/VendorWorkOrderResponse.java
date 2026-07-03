package com.strataresolve.vendor.dto;

import com.strataresolve.vendor.domain.WorkOrder;
import com.strataresolve.vendor.domain.WorkOrderStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO representing a work order as seen by a vendor user.
 * Includes work order details and minimal ticket summary information.
 *
 * <p>Validates: Requirements 13.5, 18.4
 */
public record VendorWorkOrderResponse(
        UUID id,
        UUID vendorId,
        WorkOrderStatus status,
        Instant createdAt,
        Instant completedAt,
        VendorTicketSummaryResponse ticket
) {
    /**
     * Creates a VendorWorkOrderResponse from a WorkOrder entity and its associated Ticket summary.
     */
    public static VendorWorkOrderResponse from(WorkOrder workOrder, VendorTicketSummaryResponse ticketSummary) {
        return new VendorWorkOrderResponse(
                workOrder.getId(),
                workOrder.getVendorId(),
                workOrder.getStatus(),
                workOrder.getCreatedAt(),
                workOrder.getCompletedAt(),
                ticketSummary
        );
    }
}
