package com.strataresolve.property;

import com.strataresolve.sla.service.SlaMonitorScheduler;
import com.strataresolve.ticket.domain.*;
import com.strataresolve.ticket.repository.TicketRepository;
import net.jqwik.api.*;
import net.jqwik.api.Combinators;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Property-based test for SLA Breach Detection.
 *
 * <p><b>Property 6: SLA Breach Detection</b></p>
 * <p>For any ticket where the current time exceeds acknowledgement_due_at (and not yet acknowledged)
 * or exceeds resolution_due_at (and not yet resolved), the sla_status SHALL reflect the appropriate
 * breach state.</p>
 *
 * <p><b>Validates: Requirements 14.3</b></p>
 */
@Tag("Feature: strataresolve-platform")
@Tag("Property 6: SLA Breach Detection")
class SlaBreachDetectionPropertyTest {

    private final SlaMonitorScheduler scheduler = new SlaMonitorScheduler(mock(TicketRepository.class));

    // =====================================================================
    // Property: ACK_BREACHED when only acknowledgement deadline exceeded
    // =====================================================================

    /**
     * For any ticket where acknowledgement_due_at < now AND acknowledged_at IS NULL
     * AND resolution_due_at >= now (or resolved_at is set), the determined status
     * SHALL be ACK_BREACHED.
     *
     * <p><b>Validates: Requirements 14.3</b></p>
     */
    @Property(tries = 100)
    void ackBreachedWhenOnlyAcknowledgementDeadlineExceeded(
            @ForAll("ticketWithOnlyAckBreached") TicketScenario scenario
    ) {
        // Act
        SlaStatus result = scheduler.determineSlaStatus(scenario.ticket(), scenario.now());

        // Assert
        assertThat(result)
                .as("Should be ACK_BREACHED when ack deadline exceeded and not acknowledged, " +
                        "but resolution deadline not yet exceeded")
                .isEqualTo(SlaStatus.ACK_BREACHED);
    }

    // =====================================================================
    // Property: RESOLUTION_BREACHED when only resolution deadline exceeded
    // =====================================================================

    /**
     * For any ticket where resolution_due_at < now AND resolved_at IS NULL
     * AND (acknowledgement_due_at >= now OR acknowledged_at IS NOT NULL), the determined status
     * SHALL be RESOLUTION_BREACHED.
     *
     * <p><b>Validates: Requirements 14.3</b></p>
     */
    @Property(tries = 100)
    void resolutionBreachedWhenOnlyResolutionDeadlineExceeded(
            @ForAll("ticketWithOnlyResolutionBreached") TicketScenario scenario
    ) {
        // Act
        SlaStatus result = scheduler.determineSlaStatus(scenario.ticket(), scenario.now());

        // Assert
        assertThat(result)
                .as("Should be RESOLUTION_BREACHED when resolution deadline exceeded and not resolved, " +
                        "but ack is either not breached or already acknowledged")
                .isEqualTo(SlaStatus.RESOLUTION_BREACHED);
    }

    // =====================================================================
    // Property: BOTH_BREACHED when both deadlines exceeded
    // =====================================================================

    /**
     * For any ticket where acknowledgement_due_at < now AND acknowledged_at IS NULL
     * AND resolution_due_at < now AND resolved_at IS NULL, the determined status
     * SHALL be BOTH_BREACHED.
     *
     * <p><b>Validates: Requirements 14.3</b></p>
     */
    @Property(tries = 100)
    void bothBreachedWhenBothDeadlinesExceeded(
            @ForAll("ticketWithBothBreached") TicketScenario scenario
    ) {
        // Act
        SlaStatus result = scheduler.determineSlaStatus(scenario.ticket(), scenario.now());

        // Assert
        assertThat(result)
                .as("Should be BOTH_BREACHED when both ack and resolution deadlines exceeded " +
                        "and neither acknowledged nor resolved")
                .isEqualTo(SlaStatus.BOTH_BREACHED);
    }

    // =====================================================================
    // Property: ON_TRACK when neither deadline exceeded
    // =====================================================================

    /**
     * For any ticket where acknowledgement_due_at >= now AND resolution_due_at >= now,
     * the determined status SHALL be ON_TRACK.
     *
     * <p><b>Validates: Requirements 14.3</b></p>
     */
    @Property(tries = 100)
    void onTrackWhenNoDeadlinesExceeded(
            @ForAll("ticketOnTrack") TicketScenario scenario
    ) {
        // Act
        SlaStatus result = scheduler.determineSlaStatus(scenario.ticket(), scenario.now());

        // Assert
        assertThat(result)
                .as("Should be ON_TRACK when neither deadline has been exceeded")
                .isEqualTo(SlaStatus.ON_TRACK);
    }

    // =====================================================================
    // Property: ON_TRACK when acknowledged before ack deadline
    // =====================================================================

    /**
     * For any ticket where acknowledgement_due_at < now BUT acknowledged_at IS NOT NULL
     * AND resolution_due_at >= now, the determined status SHALL be ON_TRACK
     * (acknowledging clears the ack breach condition).
     *
     * <p><b>Validates: Requirements 14.3</b></p>
     */
    @Property(tries = 100)
    void onTrackWhenAcknowledgedEvenIfAckDeadlinePassed(
            @ForAll("ticketAcknowledgedNoResolutionBreach") TicketScenario scenario
    ) {
        // Act
        SlaStatus result = scheduler.determineSlaStatus(scenario.ticket(), scenario.now());

        // Assert
        assertThat(result)
                .as("Should be ON_TRACK when ticket is acknowledged (even if ack deadline passed) " +
                        "and resolution deadline not yet exceeded")
                .isEqualTo(SlaStatus.ON_TRACK);
    }

    // =====================================================================
    // Property: ON_TRACK when resolved before resolution deadline
    // =====================================================================

    /**
     * For any ticket where resolution_due_at < now BUT resolved_at IS NOT NULL
     * AND (acknowledgement_due_at >= now OR acknowledged_at IS NOT NULL),
     * the determined status SHALL be ON_TRACK (resolving clears the resolution breach).
     *
     * <p><b>Validates: Requirements 14.3</b></p>
     */
    @Property(tries = 100)
    void onTrackWhenResolvedEvenIfResolutionDeadlinePassed(
            @ForAll("ticketResolvedNoAckBreach") TicketScenario scenario
    ) {
        // Act
        SlaStatus result = scheduler.determineSlaStatus(scenario.ticket(), scenario.now());

        // Assert
        assertThat(result)
                .as("Should be ON_TRACK when ticket is resolved (even if resolution deadline passed) " +
                        "and ack is not breached")
                .isEqualTo(SlaStatus.ON_TRACK);
    }

    // =====================================================================
    // Arbitraries (Generators)
    // =====================================================================

    /**
     * Generates a scenario where only the acknowledgement deadline is breached:
     * - acknowledgement_due_at < now
     * - acknowledged_at IS NULL
     * - resolution_due_at >= now (not breached)
     */
    @Provide
    Arbitrary<TicketScenario> ticketWithOnlyAckBreached() {
        return Combinators.combine(
                baseInstantArbitrary(),
                positiveDurationArbitrary(),
                positiveDurationArbitrary()
        ).as((baseTime, ackOverdue, resRemaining) -> {
            Instant now = baseTime;
            Instant ackDueAt = now.minus(ackOverdue);    // in the past (breached)
            Instant resDueAt = now.plus(resRemaining);   // in the future (not breached)

            Ticket ticket = buildTicket(ackDueAt, resDueAt, null, null);
            return new TicketScenario(ticket, now);
        });
    }

    /**
     * Generates a scenario where only the resolution deadline is breached:
     * - resolution_due_at < now
     * - resolved_at IS NULL
     * - acknowledged_at IS NOT NULL (so ack breach is cleared)
     */
    @Provide
    Arbitrary<TicketScenario> ticketWithOnlyResolutionBreached() {
        return Combinators.combine(
                baseInstantArbitrary(),
                positiveDurationArbitrary(),
                positiveDurationArbitrary()
        ).as((baseTime, resOverdue, ackBeforeNow) -> {
            Instant now = baseTime;
            Instant resDueAt = now.minus(resOverdue);          // in the past (breached)
            Instant ackDueAt = now.minus(ackBeforeNow);        // ack deadline also in past
            Instant acknowledgedAt = ackDueAt.minus(Duration.ofHours(1)); // acknowledged before deadline

            Ticket ticket = buildTicket(ackDueAt, resDueAt, acknowledgedAt, null);
            return new TicketScenario(ticket, now);
        });
    }

    /**
     * Generates a scenario where both deadlines are breached:
     * - acknowledgement_due_at < now AND acknowledged_at IS NULL
     * - resolution_due_at < now AND resolved_at IS NULL
     */
    @Provide
    Arbitrary<TicketScenario> ticketWithBothBreached() {
        return Combinators.combine(
                baseInstantArbitrary(),
                positiveDurationArbitrary(),
                positiveDurationArbitrary()
        ).as((baseTime, ackOverdue, resOverdue) -> {
            Instant now = baseTime;
            Instant ackDueAt = now.minus(ackOverdue);   // in the past (breached)
            Instant resDueAt = now.minus(resOverdue);   // in the past (breached)

            Ticket ticket = buildTicket(ackDueAt, resDueAt, null, null);
            return new TicketScenario(ticket, now);
        });
    }

    /**
     * Generates a scenario where neither deadline is exceeded:
     * - acknowledgement_due_at >= now
     * - resolution_due_at >= now
     */
    @Provide
    Arbitrary<TicketScenario> ticketOnTrack() {
        return Combinators.combine(
                baseInstantArbitrary(),
                positiveDurationArbitrary(),
                positiveDurationArbitrary()
        ).as((baseTime, ackRemaining, resRemaining) -> {
            Instant now = baseTime;
            Instant ackDueAt = now.plus(ackRemaining);   // in the future
            Instant resDueAt = now.plus(resRemaining);   // in the future

            Ticket ticket = buildTicket(ackDueAt, resDueAt, null, null);
            return new TicketScenario(ticket, now);
        });
    }

    /**
     * Generates a scenario where ack deadline passed but ticket was acknowledged,
     * and resolution deadline is not yet breached:
     * - acknowledgement_due_at < now
     * - acknowledged_at IS NOT NULL
     * - resolution_due_at >= now
     */
    @Provide
    Arbitrary<TicketScenario> ticketAcknowledgedNoResolutionBreach() {
        return Combinators.combine(
                baseInstantArbitrary(),
                positiveDurationArbitrary(),
                positiveDurationArbitrary()
        ).as((baseTime, ackOverdue, resRemaining) -> {
            Instant now = baseTime;
            Instant ackDueAt = now.minus(ackOverdue);           // ack deadline in past
            Instant resDueAt = now.plus(resRemaining);          // resolution in future
            Instant acknowledgedAt = ackDueAt.minus(Duration.ofMinutes(30)); // acknowledged before deadline

            Ticket ticket = buildTicket(ackDueAt, resDueAt, acknowledgedAt, null);
            return new TicketScenario(ticket, now);
        });
    }

    /**
     * Generates a scenario where resolution deadline passed but ticket was resolved,
     * and ack is not breached (acknowledged):
     * - resolution_due_at < now
     * - resolved_at IS NOT NULL
     * - acknowledged_at IS NOT NULL (so ack is not breached either)
     */
    @Provide
    Arbitrary<TicketScenario> ticketResolvedNoAckBreach() {
        return Combinators.combine(
                baseInstantArbitrary(),
                positiveDurationArbitrary(),
                positiveDurationArbitrary()
        ).as((baseTime, resOverdue, ackOverdue) -> {
            Instant now = baseTime;
            Instant resDueAt = now.minus(resOverdue);           // resolution deadline in past
            Instant ackDueAt = now.minus(ackOverdue);           // ack deadline in past
            Instant acknowledgedAt = ackDueAt.minus(Duration.ofHours(1)); // acknowledged
            Instant resolvedAt = resDueAt.minus(Duration.ofHours(1));     // resolved before deadline

            Ticket ticket = buildTicket(ackDueAt, resDueAt, acknowledgedAt, resolvedAt);
            return new TicketScenario(ticket, now);
        });
    }

    /**
     * Generates base instants spanning a wide range (2020-2030).
     */
    private Arbitrary<Instant> baseInstantArbitrary() {
        long minEpoch = Instant.parse("2020-01-01T00:00:00Z").getEpochSecond();
        long maxEpoch = Instant.parse("2030-12-31T23:59:59Z").getEpochSecond();
        return Arbitraries.longs()
                .between(minEpoch, maxEpoch)
                .map(Instant::ofEpochSecond);
    }

    /**
     * Generates positive durations between 1 minute and 30 days (representing overdue/remaining time).
     */
    private Arbitrary<Duration> positiveDurationArbitrary() {
        return Arbitraries.longs()
                .between(60, 30L * 24 * 3600) // 1 minute to 30 days in seconds
                .map(Duration::ofSeconds);
    }

    // =====================================================================
    // Helper Methods
    // =====================================================================

    private Ticket buildTicket(Instant ackDueAt, Instant resDueAt,
                               Instant acknowledgedAt, Instant resolvedAt) {
        return Ticket.builder()
                .id(UUID.randomUUID())
                .submittedBy(UUID.randomUUID())
                .unitId(UUID.randomUUID())
                .referenceNumber("SR-2025-000001")
                .title("Test ticket")
                .description("Test description")
                .category(Category.PLUMBING)
                .priority(Priority.NORMAL)
                .status(TicketStatus.SUBMITTED)
                .acknowledgementDueAt(ackDueAt)
                .resolutionDueAt(resDueAt)
                .acknowledgedAt(acknowledgedAt)
                .resolvedAt(resolvedAt)
                .slaStatus(SlaStatus.ON_TRACK)
                .build();
    }

    // =====================================================================
    // Test Data Record
    // =====================================================================

    record TicketScenario(Ticket ticket, Instant now) {}
}
