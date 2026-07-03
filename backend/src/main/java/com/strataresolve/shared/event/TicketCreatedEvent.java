package com.strataresolve.shared.event;

import java.util.UUID;

/**
 * Published when a new ticket is submitted.
 * Triggers: notification, audit, SLA calculation.
 */
public class TicketCreatedEvent extends DomainEvent {

    private final UUID ticketId;
    private final UUID unitId;
    private final String referenceNumber;
    private final String category;
    private final String priority;

    public TicketCreatedEvent(UUID actingUserId, UUID propertyId, UUID ticketId,
                              UUID unitId, String referenceNumber,
                              String category, String priority) {
        super(actingUserId, propertyId);
        this.ticketId = ticketId;
        this.unitId = unitId;
        this.referenceNumber = referenceNumber;
        this.category = category;
        this.priority = priority;
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public UUID getUnitId() {
        return unitId;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public String getCategory() {
        return category;
    }

    public String getPriority() {
        return priority;
    }
}
