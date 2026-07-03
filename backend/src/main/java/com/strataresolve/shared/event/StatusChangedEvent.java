package com.strataresolve.shared.event;

import java.util.UUID;

/**
 * Published when a ticket's status transitions to a new state.
 * Triggers: notification, audit.
 */
public class StatusChangedEvent extends DomainEvent {

    private final UUID ticketId;
    private final String previousStatus;
    private final String newStatus;
    private final String reason;

    public StatusChangedEvent(UUID actingUserId, UUID propertyId, UUID ticketId,
                              String previousStatus, String newStatus, String reason) {
        super(actingUserId, propertyId);
        this.ticketId = ticketId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.reason = reason;
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public String getPreviousStatus() {
        return previousStatus;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public String getReason() {
        return reason;
    }
}
