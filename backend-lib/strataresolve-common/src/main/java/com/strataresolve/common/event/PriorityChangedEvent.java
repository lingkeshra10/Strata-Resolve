package com.strataresolve.common.event;

import java.util.UUID;

/**
 * Published when a ticket's priority is changed.
 * Triggers: SLA recalculation, audit.
 */
public class PriorityChangedEvent extends DomainEvent {

    private final UUID ticketId;
    private final String previousPriority;
    private final String newPriority;

    public PriorityChangedEvent(UUID actingUserId, UUID propertyId, UUID ticketId,
                                String previousPriority, String newPriority) {
        super(actingUserId, propertyId);
        this.ticketId = ticketId;
        this.previousPriority = previousPriority;
        this.newPriority = newPriority;
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public String getPreviousPriority() {
        return previousPriority;
    }

    public String getNewPriority() {
        return newPriority;
    }
}
