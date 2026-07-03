package com.strataresolve.vendor.dto;

import com.strataresolve.vendor.domain.WorkOrder;
import com.strataresolve.vendor.domain.WorkOrderStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO representing a work order with its current status and timestamps.
 */
public record WorkOrderResponse(
        UUID id,
        UUID ticketId,
        UUID vendorId,
        UUID propertyId,
        WorkOrderStatus status,
        Instant createdAt,
        Instant completedAt
) {
    public static WorkOrderResponse from(WorkOrder workOrder) {
        return new WorkOrderResponse(
                workOrder.getId(),
                workOrder.getTicketId(),
                workOrder.getVendorId(),
                workOrder.getPropertyId(),
                workOrder.getStatus(),
                workOrder.getCreatedAt(),
                workOrder.getCompletedAt()
        );
    }
}
