package com.strataresolve.notification.service;

import com.strataresolve.common.event.AssignmentCreatedEvent;
import com.strataresolve.common.event.StatusChangedEvent;
import com.strataresolve.common.event.TicketCreatedEvent;
import com.strataresolve.notification.client.IdentityServiceClient;
import com.strataresolve.notification.model.NotificationRecipient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationOutboxService outboxService;
    private final IdentityServiceClient identityClient;

    public NotificationEventListener(NotificationOutboxService outboxService, IdentityServiceClient identityClient) {
        this.outboxService = outboxService;
        this.identityClient = identityClient;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.ticket-created:ticket-created}",
            groupId = "${spring.kafka.consumer.group-id:notification-service}")
    public void onTicketCreated(TicketCreatedEvent event) {

        log.debug("Handling TicketCreatedEvent: ticket={}, property={}",
                event.getTicketId(), event.getPropertyId());

        List<NotificationRecipient> managers = identityClient.findPropertyManagers(event.getPropertyId());

        String subject = String.format("New ticket submitted: %s", event.getReferenceNumber());

        String body = String.format("""
                A new maintenance ticket %s has been submitted.
                
                Category: %s
                Priority: %s
                """, event.getReferenceNumber(), event.getCategory(), event.getPriority());

        for (NotificationRecipient manager : managers) {

            if (manager.userId().equals(event.getActingUserId())) {
                continue;
            }

            outboxService.createNotification(
                    event.getPropertyId(),
                    manager.userId(),
                    manager.email(),
                    event.getTicketId(),
                    "TICKET_CREATED",
                    subject,
                    body);
        }
    }

    /**
     * Handles status change events.
     * Notifies the ticket submitter about status changes (the acting user is excluded).
     */
    @KafkaListener(
            topics = "${app.kafka.topics.status-changed:status-changed}",
            groupId = "${spring.kafka.consumer.group-id:notification-service}")
    public void onStatusChanged(StatusChangedEvent event) {

        log.debug("Handling StatusChangedEvent: ticket={}, {} -> {}",
                event.getTicketId(), event.getPreviousStatus(), event.getNewStatus());

        List<NotificationRecipient> managers = identityClient.findPropertyManagers(event.getPropertyId());

        String subject = String.format("Ticket status changed to %s", event.getNewStatus());

        String body = String.format("""
                Ticket status has been updated.
                
                Previous status: %s
                New status: %s%s
                """, event.getPreviousStatus(),
                event.getNewStatus(),
                event.getReason() != null ? "\nReason: " + event.getReason() : "");

        int notificationCount = 0;

        for (NotificationRecipient manager : managers) {
            if (manager.userId().equals(event.getActingUserId())) {
                continue;
            }
            outboxService.createNotification(
                    event.getPropertyId(),
                    manager.userId(),
                    manager.email(),
                    event.getTicketId(),
                    "STATUS_CHANGED",
                    subject,
                    body);

            notificationCount++;
        }

        log.info("Created {} notification(s) for StatusChangedEvent: ticket={}, newStatus={}",
                notificationCount, event.getTicketId(), event.getNewStatus());
    }

    /**
     * Handles assignment creation events.
     * Notifies the assignee about the new assignment.
     */
    @KafkaListener(
            topics = "${app.kafka.topics.assignment-created:assignment-created}",
            groupId = "${spring.kafka.consumer.group-id:notification-service}")
    public void onAssignmentCreated(AssignmentCreatedEvent event) {
        log.debug("Handling AssignmentCreatedEvent: ticket={}, assignee={}",
                event.getTicketId(),
                event.getAssigneeId());

        String subject = "You have been assigned a new ticket";

        String body = String.format("""
                You have been assigned a new maintenance ticket.
                
                Assignment type: %s
                """, event.getAssignmentType());

        identityClient.findUser(event.getAssigneeId()).ifPresentOrElse(
                recipient -> {
                    outboxService.createNotification(
                            event.getPropertyId(),
                            recipient.userId(),
                            recipient.email(),
                            event.getTicketId(),
                            "ASSIGNMENT_CREATED",
                            subject,
                            body);

                    log.info("Created notification for AssignmentCreatedEvent: ticket={}, assignee={}",
                            event.getTicketId(), recipient.userId());
                },
                () -> log.warn("Unable to create assignment notification. User {} was not found. ticket={}",
                        event.getAssigneeId(), event.getTicketId()));
    }
}