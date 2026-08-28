package com.strataresolve.notification.service;

import com.strataresolve.notification.domain.DeliveryStatus;
import com.strataresolve.notification.domain.Notification;
import com.strataresolve.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationProcessorScheduler")
class NotificationProcessorSchedulerTest {

    private static final int MAX_RETRY_ATTEMPTS = 5;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private EmailSender emailSender;

    private NotificationProcessorScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new NotificationProcessorScheduler(
                notificationRepository,
                emailSender,
                MAX_RETRY_ATTEMPTS
        );
    }

    @Test
    @DisplayName("processOutbox should do nothing when no pending notifications exist")
    void processOutboxShouldDoNothingWhenEmpty() {

        when(notificationRepository
                .findByDeliveryStatusAndNextAttemptAtBefore(
                        eq(DeliveryStatus.PENDING),
                        any(Instant.class)
                ))
                .thenReturn(Collections.emptyList());

        scheduler.processOutbox();

        verifyNoInteractions(emailSender);

        verify(notificationRepository, never())
                .save(any(Notification.class));
    }

    @Test
    @DisplayName("processOutbox should send email and mark notification as SENT on success")
    void processOutboxShouldMarkSentOnSuccess() {

        Notification notification =
                createPendingNotification(
                        UUID.randomUUID(),
                        "test@example.com"
                );

        when(notificationRepository
                .findByDeliveryStatusAndNextAttemptAtBefore(
                        eq(DeliveryStatus.PENDING),
                        any(Instant.class)
                ))
                .thenReturn(List.of(notification));

        scheduler.processOutbox();

        verify(emailSender)
                .send(
                        notification,
                        "test@example.com"
                );

        ArgumentCaptor<Notification> captor =
                ArgumentCaptor.forClass(Notification.class);

        verify(notificationRepository)
                .save(captor.capture());

        Notification saved = captor.getValue();

        assertThat(saved.getDeliveryStatus())
                .isEqualTo(DeliveryStatus.SENT);

        assertThat(saved.getSentAt())
                .isNotNull();

        assertThat(saved.getAttemptCount())
                .isZero();
    }

    @Test
    @DisplayName("processOutbox should use recipient email stored in notification")
    void processOutboxShouldUseStoredRecipientEmail() {

        Notification notification =
                createPendingNotification(
                        UUID.randomUUID(),
                        "stored-email@example.com"
                );

        when(notificationRepository
                .findByDeliveryStatusAndNextAttemptAtBefore(
                        eq(DeliveryStatus.PENDING),
                        any(Instant.class)
                ))
                .thenReturn(List.of(notification));

        scheduler.processOutbox();

        verify(emailSender)
                .send(
                        notification,
                        "stored-email@example.com"
                );
    }

    @Test
    @DisplayName("processOutbox should increment attempt and schedule retry on email failure")
    void processOutboxShouldRetryOnFailure() {

        Notification notification =
                createPendingNotification(
                        UUID.randomUUID(),
                        "fail@example.com"
                );

        Instant previousNextAttemptAt =
                notification.getNextAttemptAt();

        when(notificationRepository
                .findByDeliveryStatusAndNextAttemptAtBefore(
                        eq(DeliveryStatus.PENDING),
                        any(Instant.class)
                ))
                .thenReturn(List.of(notification));

        doThrow(new EmailSendException("SMTP error"))
                .when(emailSender)
                .send(
                        notification,
                        "fail@example.com"
                );

        scheduler.processOutbox();

        ArgumentCaptor<Notification> captor =
                ArgumentCaptor.forClass(Notification.class);

        verify(notificationRepository)
                .save(captor.capture());

        Notification saved = captor.getValue();

        assertThat(saved.getDeliveryStatus())
                .isEqualTo(DeliveryStatus.PENDING);

        assertThat(saved.getAttemptCount())
                .isEqualTo(1);

        assertThat(saved.getNextAttemptAt())
                .isAfter(previousNextAttemptAt);
    }

    @Test
    @DisplayName("processOutbox should mark notification as FAILED when max attempts reached")
    void processOutboxShouldMarkFailedAtMaxAttempts() {

        Notification notification =
                createPendingNotification(
                        UUID.randomUUID(),
                        "fail@example.com"
                );

        notification.setAttemptCount(
                MAX_RETRY_ATTEMPTS - 1
        );

        when(notificationRepository
                .findByDeliveryStatusAndNextAttemptAtBefore(
                        eq(DeliveryStatus.PENDING),
                        any(Instant.class)
                ))
                .thenReturn(List.of(notification));

        doThrow(new EmailSendException("SMTP error"))
                .when(emailSender)
                .send(
                        notification,
                        "fail@example.com"
                );

        scheduler.processOutbox();

        ArgumentCaptor<Notification> captor =
                ArgumentCaptor.forClass(Notification.class);

        verify(notificationRepository)
                .save(captor.capture());

        Notification saved = captor.getValue();

        assertThat(saved.getDeliveryStatus())
                .isEqualTo(DeliveryStatus.FAILED);

        assertThat(saved.getAttemptCount())
                .isEqualTo(MAX_RETRY_ATTEMPTS);
    }

    @Test
    @DisplayName("processOutbox should process multiple notifications independently")
    void processOutboxShouldProcessMultipleNotifications() {

        Notification notification1 =
                createPendingNotification(
                        UUID.randomUUID(),
                        "user1@example.com"
                );

        Notification notification2 =
                createPendingNotification(
                        UUID.randomUUID(),
                        "user2@example.com"
                );

        when(notificationRepository
                .findByDeliveryStatusAndNextAttemptAtBefore(
                        eq(DeliveryStatus.PENDING),
                        any(Instant.class)
                ))
                .thenReturn(
                        List.of(
                                notification1,
                                notification2
                        )
                );

        doAnswer(invocation -> {

            String email =
                    invocation.getArgument(
                            1,
                            String.class
                    );

            if ("user2@example.com".equals(email)) {
                throw new EmailSendException(
                        "SMTP error"
                );
            }

            return null;

        }).when(emailSender)
                .send(
                        any(Notification.class),
                        any(String.class)
                );

        scheduler.processOutbox();

        verify(emailSender)
                .send(
                        notification1,
                        "user1@example.com"
                );

        verify(emailSender)
                .send(
                        notification2,
                        "user2@example.com"
                );

        verify(notificationRepository, times(2))
                .save(any(Notification.class));

        assertThat(notification1.getDeliveryStatus())
                .isEqualTo(DeliveryStatus.SENT);

        assertThat(notification1.getSentAt())
                .isNotNull();

        assertThat(notification2.getDeliveryStatus())
                .isEqualTo(DeliveryStatus.PENDING);

        assertThat(notification2.getAttemptCount())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("processOutbox should process every pending notification returned by repository")
    void processOutboxShouldProcessEveryPendingNotification() {

        Notification notification1 =
                createPendingNotification(
                        UUID.randomUUID(),
                        "first@example.com"
                );

        Notification notification2 =
                createPendingNotification(
                        UUID.randomUUID(),
                        "second@example.com"
                );

        Notification notification3 =
                createPendingNotification(
                        UUID.randomUUID(),
                        "third@example.com"
                );

        when(notificationRepository
                .findByDeliveryStatusAndNextAttemptAtBefore(
                        eq(DeliveryStatus.PENDING),
                        any(Instant.class)
                ))
                .thenReturn(
                        List.of(
                                notification1,
                                notification2,
                                notification3
                        )
                );

        scheduler.processOutbox();

        verify(emailSender, times(3))
                .send(
                        any(Notification.class),
                        any(String.class)
                );

        verify(notificationRepository, times(3))
                .save(any(Notification.class));
    }

    private Notification createPendingNotification(
            UUID recipientUserId,
            String recipientEmail
    ) {

        Notification notification =
                Notification.builder()
                        .recipientUserId(recipientUserId)
                        .recipientEmail(recipientEmail)
                        .ticketId(UUID.randomUUID())
                        .eventType("TICKET_CREATED")
                        .subject("Test Subject")
                        .body("Test Body")
                        .deliveryStatus(DeliveryStatus.PENDING)
                        .attemptCount(0)
                        .nextAttemptAt(
                                Instant.now()
                                        .minusSeconds(10)
                        )
                        .build();

        notification.setPropertyId(
                UUID.randomUUID()
        );

        notification.setId(
                UUID.randomUUID()
        );

        return notification;
    }
}