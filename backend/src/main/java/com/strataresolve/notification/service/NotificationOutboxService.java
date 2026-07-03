package com.strataresolve.notification.service;

import com.strataresolve.notification.domain.DeliveryStatus;
import com.strataresolve.notification.domain.Notification;
import com.strataresolve.notification.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Service for creating notification records within business transactions (outbox pattern).
 * Notifications are persisted in the same transaction as the triggering event,
 * ensuring reliable delivery even if the application crashes after the event.
 */
@Service
public class NotificationOutboxService {

    private final NotificationRepository notificationRepository;

    public NotificationOutboxService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * Creates a notification record in the outbox within the current transaction.
     *
     * @param propertyId      the property context
     * @param recipientUserId the user to notify
     * @param ticketId        the related ticket (nullable)
     * @param eventType       the type of event that triggered this notification
     * @param subject         the notification subject
     * @param body            the notification body
     * @return the persisted notification
     */
    public Notification createNotification(UUID propertyId, UUID recipientUserId, UUID ticketId,
                                           String eventType, String subject, String body) {
        Notification notification = Notification.builder()
                .recipientUserId(recipientUserId)
                .ticketId(ticketId)
                .eventType(eventType)
                .subject(subject)
                .body(body)
                .deliveryStatus(DeliveryStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(Instant.now())
                .build();
        notification.setPropertyId(propertyId);

        return notificationRepository.save(notification);
    }
}
