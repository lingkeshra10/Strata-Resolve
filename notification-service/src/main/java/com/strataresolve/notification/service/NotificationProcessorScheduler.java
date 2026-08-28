package com.strataresolve.notification.service;

import com.strataresolve.notification.domain.DeliveryStatus;
import com.strataresolve.notification.domain.Notification;
import com.strataresolve.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class NotificationProcessorScheduler {

    private static final Logger log = LoggerFactory.getLogger(
                    NotificationProcessorScheduler.class
            );

    private final NotificationRepository notificationRepository;
    private final EmailSender emailSender;
    private final int maxRetryAttempts;

    public NotificationProcessorScheduler(
            NotificationRepository notificationRepository,
            EmailSender emailSender,
            @Value("${app.notification.max-retry-attempts:5}")
            int maxRetryAttempts
    ) {
        this.notificationRepository = notificationRepository;
        this.emailSender = emailSender;
        this.maxRetryAttempts = maxRetryAttempts;
    }

    @Scheduled(
            fixedDelayString =
                    "${app.notification.poll-interval-ms:30000}"
    )
    @Transactional
    public void processOutbox() {
        List<Notification> pendingNotifications = notificationRepository
                .findByDeliveryStatusAndNextAttemptAtBefore(
                        DeliveryStatus.PENDING,
                        Instant.now()
                );

        if (pendingNotifications.isEmpty()) {
            return;
        }

        log.info("Processing {} pending notification(s)", pendingNotifications.size());

        for (Notification notification : pendingNotifications) {
            processNotification(notification);
        }
    }

    private void processNotification(Notification notification) {
        String recipientEmail = notification.getRecipientEmail();

        try {
            emailSender.send(notification, recipientEmail);
            notification.markSent();
            log.debug(
                    "Notification {} delivered successfully to {}",
                    notification.getId(),
                    recipientEmail
            );
        } catch (Exception ex) {
            log.warn(
                    "Failed to deliver notification {} to {} " +
                            "(attempt {}): {}",
                    notification.getId(),
                    recipientEmail,
                    notification.getAttemptCount() + 1,
                    ex.getMessage()
            );

            notification.incrementAttemptWithBackoff(
                    maxRetryAttempts
            );
        }
        notificationRepository.save(notification);
    }
}