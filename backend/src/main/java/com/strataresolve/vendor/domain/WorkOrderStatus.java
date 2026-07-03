package com.strataresolve.vendor.domain;

/**
 * Enumeration of possible work order statuses, representing the lifecycle of
 * a work order from creation through completion or cancellation.
 */
public enum WorkOrderStatus {
    CREATED,
    ACCEPTED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    /**
     * Validates whether a transition from this status to the target status is allowed.
     * Allowed transitions:
     * <ul>
     *   <li>CREATED → ACCEPTED, CANCELLED</li>
     *   <li>ACCEPTED → IN_PROGRESS, CANCELLED</li>
     *   <li>IN_PROGRESS → COMPLETED, CANCELLED</li>
     *   <li>COMPLETED → (terminal)</li>
     *   <li>CANCELLED → (terminal)</li>
     * </ul>
     */
    public boolean canTransitionTo(WorkOrderStatus target) {
        return switch (this) {
            case CREATED -> target == ACCEPTED || target == CANCELLED;
            case ACCEPTED -> target == IN_PROGRESS || target == CANCELLED;
            case IN_PROGRESS -> target == COMPLETED || target == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
