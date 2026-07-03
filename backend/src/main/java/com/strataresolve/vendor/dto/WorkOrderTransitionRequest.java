package com.strataresolve.vendor.dto;

import com.strataresolve.vendor.domain.WorkOrderStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for transitioning a work order to a new status.
 */
public record WorkOrderTransitionRequest(
        @NotNull(message = "Target status is required")
        WorkOrderStatus targetStatus
) {
}
