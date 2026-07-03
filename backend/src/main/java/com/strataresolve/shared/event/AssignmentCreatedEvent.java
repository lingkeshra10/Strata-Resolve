package com.strataresolve.shared.event;

import java.util.UUID;

/**
 * Published when a ticket is assigned to a technician or vendor.
 * Triggers: notification, audit.
 */
public class AssignmentCreatedEvent extends DomainEvent {

    private final UUID ticketId;
    private final UUID assigneeId;
    private final String assignmentType;

    public AssignmentCreatedEvent(UUID actingUserId, UUID propertyId, UUID ticketId,
                                  UUID assigneeId, String assignmentType) {
        super(actingUserId, propertyId);
        this.ticketId = ticketId;
        this.assigneeId = assigneeId;
        this.assignmentType = assignmentType;
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public UUID getAssigneeId() {
        return assigneeId;
    }

    public String getAssignmentType() {
        return assignmentType;
    }
}
