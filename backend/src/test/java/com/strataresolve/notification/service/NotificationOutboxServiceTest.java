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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationOutboxService")
class NotificationOutboxServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationOutboxService outboxService;

    @BeforeEach
    void setUp() {
        outboxService = new NotificationOutboxService(notificationRepository);
    }

    @Test
    @DisplayName("should create notification with correct fields")
    void shouldCreateNotificationWithCorrectFields() {
        UUID propertyId = UUID.randomUUID();
        UUID recipientUserId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        String eventType = "TICKET_CREATED";
        String subject = "New ticket submitted";
        String body = "A new ticket has been submitted.";

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> {
                    Notification n = invocation.getArgument(0);
                    n.setId(UUID.randomUUID());
                    return n;
                });

        Notification result = outboxService.createNotification(
                propertyId, recipientUserId, ticketId, eventType, subject, body);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getPropertyId()).isEqualTo(propertyId);
        assertThat(saved.getRecipientUserId()).isEqualTo(recipientUserId);
        assertThat(saved.getTicketId()).isEqualTo(ticketId);
        assertThat(saved.getEventType()).isEqualTo(eventType);
        assertThat(saved.getSubject()).isEqualTo(subject);
        assertThat(saved.getBody()).isEqualTo(body);
        assertThat(saved.getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(saved.getAttemptCount()).isEqualTo(0);
        assertThat(saved.getNextAttemptAt()).isNotNull();
    }

    @Test
    @DisplayName("should create notification with null ticket ID")
    void shouldCreateNotificationWithNullTicketId() {
        UUID propertyId = UUID.randomUUID();
        UUID recipientUserId = UUID.randomUUID();
        String eventType = "SYSTEM_NOTIFICATION";
        String subject = "System update";
        String body = "System maintenance scheduled.";

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> {
                    Notification n = invocation.getArgument(0);
                    n.setId(UUID.randomUUID());
                    return n;
                });

        Notification result = outboxService.createNotification(
                propertyId, recipientUserId, null, eventType, subject, body);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getTicketId()).isNull();
        assertThat(saved.getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
    }
}
