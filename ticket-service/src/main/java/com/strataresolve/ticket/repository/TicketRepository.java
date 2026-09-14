package com.strataresolve.ticket.repository;

import com.strataresolve.ticket.domain.Ticket;
import com.strataresolve.ticket.domain.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Ticket} entities.
 */
@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    Optional<Ticket> findByReferenceNumber(String referenceNumber);

    List<Ticket> findByPropertyIdAndStatus(UUID propertyId, TicketStatus status);

    List<Ticket> findBySubmittedBy(UUID userId);

    List<Ticket> findByPropertyId(UUID propertyId);

    List<Ticket> findByUnitId(UUID unitId);

    /**
     * Finds tickets within a property that were either submitted by the given resident
     * or related to any of the resident's linked units.
     * Used to enforce resident data scope (Requirement 21.1).
     */
    @Query("SELECT t FROM Ticket t WHERE t.propertyId = :propertyId AND (t.submittedBy = :residentId OR t.unitId IN :unitIds)")
    List<Ticket> findByPropertyIdAndSubmittedByOrUnitIdIn(
            @Param("propertyId") UUID propertyId,
            @Param("residentId") UUID residentId,
            @Param("unitIds") Collection<UUID> unitIds);

    /**
     * Finds tickets within a property that were submitted by the given resident.
     * Fallback when the resident has no linked units.
     */
    @Query("SELECT t FROM Ticket t WHERE t.propertyId = :propertyId AND t.submittedBy = :residentId")
    List<Ticket> findByPropertyIdAndSubmittedBy(
            @Param("propertyId") UUID propertyId,
            @Param("residentId") UUID residentId);

    /**
     * Counts the number of tickets submitted by a specific user since the given timestamp.
     * Used for enforcing submission rate limiting (Requirements 16.4, 16.5).
     *
     * @param submittedBy the user ID of the submitter
     * @param since       the start of the rate-limiting time window
     * @return the number of tickets submitted within the window
     */
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.submittedBy = :submittedBy AND t.createdAt >= :since")
    long countBySubmittedByAndCreatedAtAfter(@Param("submittedBy") UUID submittedBy, @Param("since") Instant since);

    /**
     * Finds tickets in the same property with the same category, created within the given time window.
     * Used for duplicate detection — returns candidates that share category and were recently created.
     * Excludes tickets in terminal statuses (CLOSED, CANCELLED, REJECTED).
     */
    @Query("SELECT t FROM Ticket t WHERE t.propertyId = :propertyId " +
           "AND t.category = :category " +
           "AND t.createdAt >= :since " +
           "AND t.status NOT IN (com.strataresolve.ticket.domain.TicketStatus.CLOSED, " +
           "com.strataresolve.ticket.domain.TicketStatus.CANCELLED, " +
           "com.strataresolve.ticket.domain.TicketStatus.REJECTED)")
    List<Ticket> findPotentialDuplicateCandidates(
            @Param("propertyId") UUID propertyId,
            @Param("category") com.strataresolve.ticket.domain.Category category,
            @Param("since") Instant since);

    /**
     * Finds all tickets flagged as potential duplicates for a property.
     */
    List<Ticket> findByPropertyIdAndDuplicateFlagTrue(UUID propertyId);

    /**
     * Finds tickets that may have breached their SLA targets.
     * Selects tickets where:
     * - Status is NOT in a terminal state (CLOSED, CANCELLED, REJECTED, RESOLVED)
     * - AND at least one of: acknowledgement_due_at < now (and not yet acknowledged),
     *   or resolution_due_at < now (and not yet resolved)
     *
     * Used by SlaMonitorScheduler for periodic breach detection (Requirement 14.3).
     */
    @Query("SELECT t FROM Ticket t WHERE t.status NOT IN (" +
           "com.strataresolve.ticket.domain.TicketStatus.CLOSED, " +
           "com.strataresolve.ticket.domain.TicketStatus.CANCELLED, " +
           "com.strataresolve.ticket.domain.TicketStatus.REJECTED, " +
           "com.strataresolve.ticket.domain.TicketStatus.RESOLVED) " +
           "AND (" +
           "(t.acknowledgementDueAt IS NOT NULL AND t.acknowledgementDueAt < :now AND t.acknowledgedAt IS NULL) " +
           "OR " +
           "(t.resolutionDueAt IS NOT NULL AND t.resolutionDueAt < :now AND t.resolvedAt IS NULL)" +
           ")")
    List<Ticket> findTicketsWithBreachedSla(@Param("now") Instant now);
}
