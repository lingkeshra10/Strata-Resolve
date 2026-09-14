package com.strataresolve.ticket.domain;

/**
 * Enumeration of all possible ticket statuses in the StrataResolve workflow.
 */
public enum TicketStatus {
    SUBMITTED,
    ACKNOWLEDGED,
    UNDER_REVIEW,
    ASSIGNED,
    IN_PROGRESS,
    AWAITING_VENDOR,
    AWAITING_RESIDENT,
    READY_FOR_VERIFICATION,
    RESOLVED,
    CLOSED,
    REOPENED,
    REJECTED,
    CANCELLED
}
