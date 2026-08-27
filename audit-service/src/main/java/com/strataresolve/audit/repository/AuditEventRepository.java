package com.strataresolve.audit.repository;

import com.strataresolve.audit.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link AuditEvent} entities.
 *
 * <p>This repository intentionally provides only read and create operations.
 * No delete or update methods are exposed, enforcing the append-only nature
 * of audit records at the repository level.
 *
 * <p>Note: While Spring Data JPA's base interface includes delete methods,
 * the service layer ensures these are never called. The append-only constraint
 * is enforced through the {@code AuditService} which only exposes creation.
 */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    /**
     * Finds audit events for a specific property, ordered by creation time descending.
     */
    List<AuditEvent> findByPropertyIdOrderByCreatedAtDesc(UUID propertyId);

    /**
     * Finds audit events for a property within a date range.
     */
    @Query("SELECT ae FROM AuditEvent ae WHERE ae.propertyId = :propertyId " +
            "AND ae.createdAt >= :from AND ae.createdAt <= :to " +
            "ORDER BY ae.createdAt DESC")
    List<AuditEvent> findByPropertyIdAndDateRange(
            @Param("propertyId") UUID propertyId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Finds audit events by event type within a property.
     */
    List<AuditEvent> findByPropertyIdAndEventTypeOrderByCreatedAtDesc(
            UUID propertyId, String eventType);

    /**
     * Finds audit events by acting user within a property.
     */
    List<AuditEvent> findByPropertyIdAndActingUserIdOrderByCreatedAtDesc(
            UUID propertyId, UUID actingUserId);

    /**
     * Finds audit events by target entity within a property.
     */
    List<AuditEvent> findByPropertyIdAndTargetEntityTypeAndTargetEntityIdOrderByCreatedAtDesc(
            UUID propertyId, String targetEntityType, UUID targetEntityId);
}
