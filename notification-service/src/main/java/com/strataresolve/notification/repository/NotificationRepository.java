package com.strataresolve.notification.repository;

import com.strataresolve.notification.domain.DeliveryStatus;
import com.strataresolve.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for Notification entities.
 * Provides queries for outbox processing and notification retrieval.
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * Find notifications ready for delivery processing.
     * Returns pending notifications whose next attempt time has passed.
     */
    @Query("SELECT n FROM Notification n WHERE n.deliveryStatus = :status AND n.nextAttemptAt <= :now")
    List<Notification> findByDeliveryStatusAndNextAttemptAtBefore(
            @Param("status") DeliveryStatus status,
            @Param("now") Instant now);

    /**
     * Find all notifications for a specific recipient.
     */
    List<Notification> findByRecipientUserId(UUID recipientUserId);

    /**
     * Find all notifications for a specific ticket.
     */
    List<Notification> findByTicketId(UUID ticketId);
}
