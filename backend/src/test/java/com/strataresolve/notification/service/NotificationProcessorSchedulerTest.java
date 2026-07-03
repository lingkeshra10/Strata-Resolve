package com.strataresolve.notification.service;

import com.strataresolve.notification.domain.DeliveryStatus;
import com.strataresolve.notification.domain.Notification;
import com.strataresolve.notification.repository.NotificationRepository;
import com.strataresolve.user.domain.User;
import com.strataresolve.user.repository.UserRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationProcessorScheduler")
class NotificationProcessorSchedulerTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailSender emailSender;

    private NotificationProcessorScheduler scheduler;

    private static final int MAX_RETRY_ATTEMPTS = 5;

    @BeforeEach
    void setUp() {
        scheduler = new NotificationProcessorScheduler(
                notificationRepository,
                userRepository,
                emailSender,
                MAX_RETRY_ATTEMPTS
        );
    }

    @Test
    @DisplayName("processOutbox should do nothing when no pending notifications exist")
    void processOutboxShouldDoNothingWhenEmpty() {
        when(notificationRepository.findByDeliveryStatusAndNextAttemptAtBefore(
                eq(DeliveryStatus.PENDING), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        scheduler.processOutbox();

        verify(emailSender, never()).send(any(), any());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("processOutbox should send email and mark notification as SENT on success")
    void processOutboxShouldMarkSentOnSuccess() {
        UUID userId = UUID.randomUUID();
        Notification notification = createPendingNotification(userId);
        User user = createUser(userId, "test@example.com");

        when(notificationRepository.findByDeliveryStatusAndNextAttemptAtBefore(
                eq(DeliveryStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(notification));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        scheduler.processOutbox();

        verify(emailSender).send(notification, "test@example.com");
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(saved.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("processOutbox should increment attempt and schedule retry on email failure")
    void processOutboxShouldRetryOnFailure() {
        UUID userId = UUID.randomUUID();
        Notification notification = createPendingNotification(userId);
        User user = createUser(userId, "fail@example.com");

        when(notificationRepository.findByDeliveryStatusAndNextAttemptAtBefore(
                eq(DeliveryStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(notification));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        doThrow(new EmailSendException("SMTP error"))
                .when(emailSender).send(notification, "fail@example.com");

        scheduler.processOutbox();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(saved.getAttemptCount()).isEqualTo(1);
        assertThat(saved.getNextAttemptAt()).isAfter(Instant.now().minusSeconds(1));
    }

    @Test
    @DisplayName("processOutbox should mark notification as FAILED when max attempts reached")
    void processOutboxShouldMarkFailedAtMaxAttempts() {
        UUID userId = UUID.randomUUID();
        Notification notification = createPendingNotification(userId);
        notification.setAttemptCount(MAX_RETRY_ATTEMPTS - 1); // One more attempt will reach max
        User user = createUser(userId, "fail@example.com");

        when(notificationRepository.findByDeliveryStatusAndNextAttemptAtBefore(
                eq(DeliveryStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(notification));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        doThrow(new EmailSendException("SMTP error"))
                .when(emailSender).send(notification, "fail@example.com");

        scheduler.processOutbox();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(saved.getAttemptCount()).isEqualTo(MAX_RETRY_ATTEMPTS);
    }

    @Test
    @DisplayName("processOutbox should mark notification as FAILED when recipient user not found")
    void processOutboxShouldFailWhenRecipientNotFound() {
        UUID userId = UUID.randomUUID();
        Notification notification = createPendingNotification(userId);

        when(notificationRepository.findByDeliveryStatusAndNextAttemptAtBefore(
                eq(DeliveryStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(notification));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        scheduler.processOutbox();

        verify(emailSender, never()).send(any(), any());
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
    }

    @Test
    @DisplayName("processOutbox should process multiple notifications independently")
    void processOutboxShouldProcessMultipleNotifications() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        Notification notification1 = createPendingNotification(userId1);
        Notification notification2 = createPendingNotification(userId2);
        User user1 = createUser(userId1, "user1@example.com");
        User user2 = createUser(userId2, "user2@example.com");

        when(notificationRepository.findByDeliveryStatusAndNextAttemptAtBefore(
                eq(DeliveryStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(notification1, notification2));
        when(userRepository.findById(userId1)).thenReturn(Optional.of(user1));
        when(userRepository.findById(userId2)).thenReturn(Optional.of(user2));

        // Use doAnswer to selectively throw for notification2 only
        org.mockito.Mockito.doAnswer(invocation -> {
            String email = invocation.getArgument(1, String.class);
            if ("user2@example.com".equals(email)) {
                throw new EmailSendException("SMTP error");
            }
            return null;
        }).when(emailSender).send(any(Notification.class), any(String.class));

        scheduler.processOutbox();

        verify(emailSender).send(eq(notification1), eq("user1@example.com"));
        verify(emailSender).send(eq(notification2), eq("user2@example.com"));
        verify(notificationRepository, times(2)).save(any(Notification.class));

        // Verify first notification is SENT
        assertThat(notification1.getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
        // Verify second notification is still PENDING with incremented attempts
        assertThat(notification2.getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(notification2.getAttemptCount()).isEqualTo(1);
    }

    private Notification createPendingNotification(UUID recipientUserId) {
        Notification notification = Notification.builder()
                .recipientUserId(recipientUserId)
                .ticketId(UUID.randomUUID())
                .eventType("TICKET_CREATED")
                .subject("Test Subject")
                .body("Test Body")
                .deliveryStatus(DeliveryStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(Instant.now().minusSeconds(10))
                .build();
        notification.setPropertyId(UUID.randomUUID());
        // Set id manually for test identification
        notification.setId(UUID.randomUUID());
        return notification;
    }

    private User createUser(UUID userId, String email) {
        return User.builder()
                .id(userId)
                .email(email)
                .firstName("Test")
                .lastName("User")
                .passwordHash("hashed")
                .build();
    }
}
