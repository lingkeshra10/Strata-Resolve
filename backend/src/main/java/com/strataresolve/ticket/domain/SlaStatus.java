package com.strataresolve.ticket.domain;

/**
 * SLA compliance status for a ticket.
 */
public enum SlaStatus {
    ON_TRACK,
    ACK_BREACHED,
    RESOLUTION_BREACHED,
    BOTH_BREACHED
}
