package com.strataresolve.notification.service;

import com.strataresolve.notification.domain.DeliveryStatus;
import com.strataresolve.notification.domain.Notification;
import com.strataresolve.notification.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class NotificationOutboxService {

    private final NotificationRepository notificationRepository;

    public NotificationOutboxService(
            NotificationRepository notificationRepository
    ) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public Notification createNotification(
            UUID propertyId,
            UUID recipientUserId,
            String recipientEmail,
            UUID ticketId,
            String eventType,
            String subject,
            String body
    ) {

        Notification notification = Notification.builder()
                .recipientUserId(recipientUserId)
                .recipientEmail(recipientEmail)
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