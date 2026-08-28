package com.strataresolve.notification.domain;

import com.strataresolve.common.tenant.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a notification record in the outbox.
 * Notifications are created within business transactions and processed
 * asynchronously by a scheduled poller for delivery reliability.
 */
@Entity
@Table(name = "notification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Column(name = "ticket_id")
    private UUID ticketId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, length = 500)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    @Builder.Default
    private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        if (this.nextAttemptAt == null) {
            this.nextAttemptAt = Instant.now();
        }
    }

    /**
     * Marks this notification as successfully sent.
     */
    public void markSent() {
        this.deliveryStatus = DeliveryStatus.SENT;
        this.sentAt = Instant.now();
    }

    /**
     * Increments the attempt count and schedules the next attempt with exponential backoff.
     * If max attempts are reached, marks the notification as failed.
     *
     * @param maxAttempts the maximum number of delivery attempts allowed
     */
    public void incrementAttemptWithBackoff(int maxAttempts) {
        this.attemptCount++;
        if (this.attemptCount >= maxAttempts) {
            this.deliveryStatus = DeliveryStatus.FAILED;
        } else {
            // Exponential backoff: 30s, 60s, 120s, 240s, ...
            long backoffSeconds = 30L * (1L << (this.attemptCount - 1));
            this.nextAttemptAt = Instant.now().plusSeconds(backoffSeconds);
        }
    }
}
