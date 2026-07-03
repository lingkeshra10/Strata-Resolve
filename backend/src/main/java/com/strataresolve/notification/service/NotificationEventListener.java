package com.strataresolve.notification.service;

import com.strataresolve.shared.event.AssignmentCreatedEvent;
import com.strataresolve.shared.event.StatusChangedEvent;
import com.strataresolve.shared.event.TicketCreatedEvent;
import com.strataresolve.user.domain.Membership;
import com.strataresolve.user.domain.Role;
import com.strataresolve.user.repository.MembershipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Listens to domain events and creates notification records for relevant recipients.
 * Notifications are created within the same transaction as the triggering event
 * (outbox pattern), ensuring reliable delivery.
 *
 * <p>Recipient determination:
 * <ul>
 *   <li>TicketCreatedEvent → notify Property Managers for the property</li>
 *   <li>StatusChangedEvent → notify the ticket submitter (acting user is excluded since they triggered it)</li>
 *   <li>AssignmentCreatedEvent → notify the assignee</li>
 * </ul>
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationOutboxService outboxService;
    private final MembershipRepository membershipRepository;

    public NotificationEventListener(NotificationOutboxService outboxService,
                                     MembershipRepository membershipRepository) {
        this.outboxService = outboxService;
        this.membershipRepository = membershipRepository;
    }

    /**
     * Handles ticket creation events.
     * Notifies all active Property Managers for the property about the new ticket.
     */
    @EventListener
    public void onTicketCreated(TicketCreatedEvent event) {
        log.debug("Handling TicketCreatedEvent: ticket={}, property={}",
                event.getTicketId(), event.getPropertyId());

        List<UUID> propertyManagerIds = findPropertyManagerIds(event.getPropertyId());

        String subject = String.format("New ticket submitted: %s", event.getReferenceNumber());
        String body = String.format(
                "A new maintenance ticket %s has been submitted.\n\nCategory: %s\nPriority: %s",
                event.getReferenceNumber(), event.getCategory(), event.getPriority());

        for (UUID managerId : propertyManagerIds) {
            // Don't notify the acting user if they are also a property manager
            if (!managerId.equals(event.getActingUserId())) {
                outboxService.createNotification(
                        event.getPropertyId(),
                        managerId,
                        event.getTicketId(),
                        "TICKET_CREATED",
                        subject,
                        body
                );
            }
        }

        log.info("Created {} notification(s) for TicketCreatedEvent: ticket={}",
                propertyManagerIds.size(), event.getTicketId());
    }

    /**
     * Handles status change events.
     * Notifies the ticket submitter about status changes (the acting user is excluded).
     */
    @EventListener
    public void onStatusChanged(StatusChangedEvent event) {
        log.debug("Handling StatusChangedEvent: ticket={}, {} -> {}",
                event.getTicketId(), event.getPreviousStatus(), event.getNewStatus());

        // Notify property managers about status changes
        List<UUID> propertyManagerIds = findPropertyManagerIds(event.getPropertyId());

        String subject = String.format("Ticket status changed to %s", event.getNewStatus());
        String body = String.format(
                "Ticket status has been updated.\n\nPrevious status: %s\nNew status: %s%s",
                event.getPreviousStatus(),
                event.getNewStatus(),
                event.getReason() != null ? "\nReason: " + event.getReason() : "");

        for (UUID managerId : propertyManagerIds) {
            if (!managerId.equals(event.getActingUserId())) {
                outboxService.createNotification(
                        event.getPropertyId(),
                        managerId,
                        event.getTicketId(),
                        "STATUS_CHANGED",
                        subject,
                        body
                );
            }
        }

        log.info("Created notification(s) for StatusChangedEvent: ticket={}, newStatus={}",
                event.getTicketId(), event.getNewStatus());
    }

    /**
     * Handles assignment creation events.
     * Notifies the assignee about the new assignment.
     */
    @EventListener
    public void onAssignmentCreated(AssignmentCreatedEvent event) {
        log.debug("Handling AssignmentCreatedEvent: ticket={}, assignee={}",
                event.getTicketId(), event.getAssigneeId());

        String subject = "You have been assigned a new ticket";
        String body = String.format(
                "You have been assigned a new maintenance ticket.\n\nAssignment type: %s",
                event.getAssignmentType());

        outboxService.createNotification(
                event.getPropertyId(),
                event.getAssigneeId(),
                event.getTicketId(),
                "ASSIGNMENT_CREATED",
                subject,
                body
        );

        log.info("Created notification for AssignmentCreatedEvent: ticket={}, assignee={}",
                event.getTicketId(), event.getAssigneeId());
    }

    /**
     * Finds all active Property Manager user IDs for a given property.
     */
    private List<UUID> findPropertyManagerIds(UUID propertyId) {
        List<Membership> memberships = membershipRepository.findActiveByPropertyId(propertyId);
        return memberships.stream()
                .filter(m -> m.getRole() == Role.PROPERTY_MANAGER)
                .map(Membership::getUserId)
                .distinct()
                .toList();
    }
}
