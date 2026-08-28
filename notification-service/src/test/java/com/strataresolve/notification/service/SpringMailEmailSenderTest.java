package com.strataresolve.notification.service;

import com.strataresolve.notification.domain.DeliveryStatus;
import com.strataresolve.notification.domain.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SpringMailEmailSender")
class SpringMailEmailSenderTest {

    @Mock
    private JavaMailSender javaMailSender;

    private SpringMailEmailSender emailSender;

    private static final String FROM_ADDRESS = "noreply@strataresolve.com";

    @BeforeEach
    void setUp() {
        emailSender = new SpringMailEmailSender(javaMailSender, FROM_ADDRESS);
    }

    @Test
    @DisplayName("send should construct and send SimpleMailMessage with correct fields")
    void sendShouldConstructCorrectMessage() {
        Notification notification = createNotification("Important Subject", "Email body content");

        emailSender.send(notification, "recipient@example.com");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();
        assertThat(message.getFrom()).isEqualTo(FROM_ADDRESS);
        assertThat(message.getTo()).containsExactly("recipient@example.com");
        assertThat(message.getSubject()).isEqualTo("Important Subject");
        assertThat(message.getText()).isEqualTo("Email body content");
    }

    @Test
    @DisplayName("send should throw EmailSendException when JavaMailSender fails")
    void sendShouldThrowEmailSendExceptionOnFailure() {
        Notification notification = createNotification("Subject", "Body");
        doThrow(new MailSendException("SMTP connection refused"))
                .when(javaMailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> emailSender.send(notification, "recipient@example.com"))
                .isInstanceOf(EmailSendException.class)
                .hasMessageContaining("Failed to send email to recipient@example.com")
                .hasCauseInstanceOf(MailSendException.class);
    }

    @Test
    @DisplayName("send should use the configured from address")
    void sendShouldUseConfiguredFromAddress() {
        SpringMailEmailSender customSender = new SpringMailEmailSender(javaMailSender, "custom@domain.com");
        Notification notification = createNotification("Subject", "Body");

        customSender.send(notification, "recipient@example.com");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(captor.capture());

        assertThat(captor.getValue().getFrom()).isEqualTo("custom@domain.com");
    }

    private Notification createNotification(String subject, String body) {
        Notification notification = Notification.builder()
                .recipientUserId(UUID.randomUUID())
                .ticketId(UUID.randomUUID())
                .eventType("TICKET_CREATED")
                .subject(subject)
                .body(body)
                .deliveryStatus(DeliveryStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(Instant.now())
                .build();
        notification.setId(UUID.randomUUID());
        notification.setPropertyId(UUID.randomUUID());
        return notification;
    }
}
