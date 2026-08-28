package com.strataresolve.notification.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Notification Entity")
class NotificationTest {

    @Test
    @DisplayName("markSent should set delivery status to SENT and record sent_at timestamp")
    void markSentShouldSetStatusAndTimestamp() {
        Notification notification = Notification.builder()
                .deliveryStatus(DeliveryStatus.PENDING)
                .attemptCount(1)
                .nextAttemptAt(Instant.now())
                .build();

        Instant before = Instant.now();
        notification.markSent();
        Instant after = Instant.now();

        assertThat(notification.getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(notification.getSentAt()).isBetween(before, after);
    }

    @Test
    @DisplayName("incrementAttemptWithBackoff should increment attempt count and schedule next attempt")
    void incrementAttemptShouldScheduleNextAttempt() {
        Notification notification = Notification.builder()
                .deliveryStatus(DeliveryStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(Instant.now())
                .build();

        Instant before = Instant.now();
        notification.incrementAttemptWithBackoff(5);

        assertThat(notification.getAttemptCount()).isEqualTo(1);
        assertThat(notification.getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
        // First backoff: 30 seconds
        assertThat(notification.getNextAttemptAt()).isAfter(before);
    }

    @Test
    @DisplayName("incrementAttemptWithBackoff should use exponential backoff")
    void incrementAttemptShouldUseExponentialBackoff() {
        Notification notification = Notification.builder()
                .deliveryStatus(DeliveryStatus.PENDING)
                .attemptCount(2)
                .nextAttemptAt(Instant.now())
                .build();

        Instant before = Instant.now();
        notification.incrementAttemptWithBackoff(5);

        assertThat(notification.getAttemptCount()).isEqualTo(3);
        assertThat(notification.getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
        // Third attempt backoff: 30 * 2^2 = 120 seconds
        assertThat(notification.getNextAttemptAt()).isAfter(before.plusSeconds(100));
    }

    @Test
    @DisplayName("incrementAttemptWithBackoff should mark FAILED when max attempts reached")
    void incrementAttemptShouldMarkFailedAtMaxAttempts() {
        Notification notification = Notification.builder()
                .deliveryStatus(DeliveryStatus.PENDING)
                .attemptCount(4)
                .nextAttemptAt(Instant.now())
                .build();

        notification.incrementAttemptWithBackoff(5);

        assertThat(notification.getAttemptCount()).isEqualTo(5);
        assertThat(notification.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
    }

    @Test
    @DisplayName("default builder values should set PENDING status and zero attempt count")
    void defaultBuilderValues() {
        Notification notification = Notification.builder()
                .nextAttemptAt(Instant.now())
                .build();

        assertThat(notification.getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(notification.getAttemptCount()).isEqualTo(0);
    }
}
