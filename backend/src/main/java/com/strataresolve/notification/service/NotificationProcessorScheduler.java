package com.strataresolve.notification.service;

import com.strataresolve.notification.domain.DeliveryStatus;
import com.strataresolve.notification.domain.Notification;
import com.strataresolve.notification.repository.NotificationRepository;
import com.strataresolve.user.domain.User;
import com.strataresolve.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Scheduled processor that polls the notification outbox for pending notifications
 * and delivers them via email. Implements retry with exponential backoff.
 *
 * <p>Polling interval is configurable via {@code app.notification.poll-interval-ms}.
 * Max retry attempts are configurable via {@code app.notification.max-retry-attempts}.
 */
@Component
public class NotificationProcessorScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationProcessorScheduler.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final EmailSender emailSender;
    private final int maxRetryAttempts;

    public NotificationProcessorScheduler(NotificationRepository notificationRepository,
                                          UserRepository userRepository,
                                          EmailSender emailSender,
                                          @Value("${app.notification.max-retry-attempts:5}") int maxRetryAttempts) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.emailSender = emailSender;
        this.maxRetryAttempts = maxRetryAttempts;
    }

    /**
     * Polls the notification outbox for pending notifications whose next_attempt_at
     * has passed, and attempts to deliver each one via email.
     *
     * <p>On success, the notification is marked as SENT. On failure, the attempt count
     * is incremented with exponential backoff. If max attempts are reached, the
     * notification is marked as FAILED.
     */
    @Scheduled(fixedDelayString = "${app.notification.poll-interval-ms:30000}")
    @Transactional
    public void processOutbox() {
        List<Notification> pendingNotifications = notificationRepository
                .findByDeliveryStatusAndNextAttemptAtBefore(DeliveryStatus.PENDING, Instant.now());

        if (pendingNotifications.isEmpty()) {
            return;
        }

        log.info("Processing {} pending notification(s)", pendingNotifications.size());

        for (Notification notification : pendingNotifications) {
            processNotification(notification);
        }
    }

    /**
     * Processes a single notification: looks up the recipient's email and attempts delivery.
     */
    private void processNotification(Notification notification) {
        Optional<User> recipientOpt = userRepository.findById(notification.getRecipientUserId());

        if (recipientOpt.isEmpty()) {
            log.warn("Recipient user {} not found for notification {}. Marking as failed.",
                    notification.getRecipientUserId(), notification.getId());
            notification.setDeliveryStatus(DeliveryStatus.FAILED);
            notificationRepository.save(notification);
            return;
        }

        User recipient = recipientOpt.get();
        String recipientEmail = recipient.getEmail();

        try {
            emailSender.send(notification, recipientEmail);
            notification.markSent();
            log.debug("Notification {} delivered successfully to {}", notification.getId(), recipientEmail);
        } catch (Exception e) {
            log.warn("Failed to deliver notification {} to {} (attempt {}): {}",
                    notification.getId(), recipientEmail, notification.getAttemptCount() + 1, e.getMessage());
            notification.incrementAttemptWithBackoff(maxRetryAttempts);
        }

        notificationRepository.save(notification);
    }
}
