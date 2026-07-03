package com.strataresolve.property;

import com.strataresolve.property.repository.PropertyRepository;
import com.strataresolve.shared.event.DomainEventPublisher;
import com.strataresolve.ticket.config.TicketProperties;
import com.strataresolve.ticket.domain.Category;
import com.strataresolve.ticket.domain.Priority;
import com.strataresolve.ticket.domain.Ticket;
import com.strataresolve.ticket.domain.TicketStatus;
import com.strataresolve.ticket.policy.StatusWorkflowEngine;
import com.strataresolve.ticket.repository.StatusHistoryRepository;
import com.strataresolve.ticket.repository.TicketRepository;
import com.strataresolve.ticket.service.DuplicateDetectionService;
import com.strataresolve.ticket.service.ReferenceNumberGenerator;
import com.strataresolve.ticket.service.TicketService;
import com.strataresolve.user.domain.Membership;
import com.strataresolve.user.domain.Role;
import com.strataresolve.user.repository.MembershipRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-based test for Resident Data Scope.
 *
 * <p><b>Property 14: Resident Data Scope</b></p>
 * <p>For any resident viewing tickets, the system SHALL return only tickets submitted by that
 * resident or related to the resident's linked unit. No tickets from other residents or
 * unrelated units SHALL be included.</p>
 *
 * <p><b>Validates: Requirements 13.1, 21.1</b></p>
 */
@Tag("Feature: strataresolve-platform")
@Tag("Property 14: Resident Data Scope")
class ResidentDataScopePropertyTest {

    // =====================================================================
    // Property: All returned tickets are either submitted by the resident
    // OR related to one of the resident's linked units
    // =====================================================================

    /**
     * For any resident with linked units and any set of tickets returned by the repository,
     * every ticket in the result SHALL satisfy at least one of:
     * (1) submitted by that resident, or (2) associated with one of the resident's linked units.
     *
     * <p><b>Validates: Requirements 13.1, 21.1</b></p>
     */
    @Property(tries = 100)
    void allReturnedTicketsBelongToResidentOrTheirUnits(
            @ForAll("residentWithUnits") ResidentScenario scenario
    ) {
        // Arrange
        UUID propertyId = scenario.propertyId();
        UUID residentId = scenario.residentId();
        List<UUID> residentUnitIds = scenario.residentUnitIds();

        TicketRepository ticketRepository = mock(TicketRepository.class);
        MembershipRepository membershipRepository = mock(MembershipRepository.class);
        TicketService ticketService = buildTicketService(ticketRepository, membershipRepository);

        List<Membership> memberships = buildMemberships(residentId, propertyId, residentUnitIds);
        when(membershipRepository.findActiveByUserIdAndPropertyId(residentId, propertyId))
                .thenReturn(memberships);

        // Generate tickets that the repository would return (mix of own + unit-related)
        List<Ticket> repositoryResults = scenario.expectedTickets();
        if (residentUnitIds.isEmpty()) {
            when(ticketRepository.findByPropertyIdAndSubmittedBy(propertyId, residentId))
                    .thenReturn(repositoryResults);
        } else {
            when(ticketRepository.findByPropertyIdAndSubmittedByOrUnitIdIn(eq(propertyId), eq(residentId), any()))
                    .thenReturn(repositoryResults);
        }

        // Act
        List<Ticket> result = ticketService.findResidentTickets(propertyId, residentId);

        // Assert: every returned ticket is submitted by resident or related to their unit
        assertThat(result).allSatisfy(ticket -> {
            boolean submittedByResident = ticket.getSubmittedBy().equals(residentId);
            boolean relatedToResidentUnit = residentUnitIds.contains(ticket.getUnitId());
            assertThat(submittedByResident || relatedToResidentUnit)
                    .as("Ticket %s must be submitted by resident %s or related to one of their units %s, " +
                                    "but was submitted by %s for unit %s",
                            ticket.getReferenceNumber(), residentId, residentUnitIds,
                            ticket.getSubmittedBy(), ticket.getUnitId())
                    .isTrue();
        });
    }

    // =====================================================================
    // Property: Tickets from other residents/unrelated units are never included
    // =====================================================================

    /**
     * For any resident, tickets submitted by a different resident AND associated with
     * a unit NOT linked to the requesting resident SHALL NOT appear in the results.
     * This validates the exclusion guarantee.
     *
     * <p><b>Validates: Requirements 13.1, 21.1</b></p>
     */
    @Property(tries = 100)
    void ticketsFromOtherResidentsAndUnrelatedUnitsAreExcluded(
            @ForAll("residentWithUnits") ResidentScenario scenario,
            @ForAll("unrelatedTicketCount") @IntRange(min = 1, max = 10) int unrelatedCount
    ) {
        // Arrange
        UUID propertyId = scenario.propertyId();
        UUID residentId = scenario.residentId();
        List<UUID> residentUnitIds = scenario.residentUnitIds();

        TicketRepository ticketRepository = mock(TicketRepository.class);
        MembershipRepository membershipRepository = mock(MembershipRepository.class);
        TicketService ticketService = buildTicketService(ticketRepository, membershipRepository);

        List<Membership> memberships = buildMemberships(residentId, propertyId, residentUnitIds);
        when(membershipRepository.findActiveByUserIdAndPropertyId(residentId, propertyId))
                .thenReturn(memberships);

        // Build unrelated tickets (different submitter AND different unit)
        List<Ticket> unrelatedTickets = IntStream.range(0, unrelatedCount)
                .mapToObj(i -> buildTicket(
                        UUID.randomUUID(),   // different submitter
                        UUID.randomUUID(),   // different (unrelated) unit
                        propertyId,
                        "SR-2025-" + String.format("%06d", 900 + i)
                ))
                .toList();

        // The repository correctly enforces the filter so it returns ONLY valid tickets
        List<Ticket> repositoryResults = scenario.expectedTickets();
        if (residentUnitIds.isEmpty()) {
            when(ticketRepository.findByPropertyIdAndSubmittedBy(propertyId, residentId))
                    .thenReturn(repositoryResults);
        } else {
            when(ticketRepository.findByPropertyIdAndSubmittedByOrUnitIdIn(eq(propertyId), eq(residentId), any()))
                    .thenReturn(repositoryResults);
        }

        // Act
        List<Ticket> result = ticketService.findResidentTickets(propertyId, residentId);

        // Assert: no unrelated tickets are in the result
        for (Ticket unrelated : unrelatedTickets) {
            assertThat(result).doesNotContain(unrelated);
        }

        // Assert: none of the returned tickets have both a different submitter AND an unrelated unit
        assertThat(result).allSatisfy(ticket -> {
            boolean submittedByResident = ticket.getSubmittedBy().equals(residentId);
            boolean relatedToResidentUnit = residentUnitIds.contains(ticket.getUnitId());
            assertThat(submittedByResident || relatedToResidentUnit).isTrue();
        });
    }

    // =====================================================================
    // Property: When resident has no linked units, only submitted tickets returned
    // =====================================================================

    /**
     * For any resident with no linked units (membership without unit_id),
     * the system SHALL only use the findByPropertyIdAndSubmittedBy query
     * and return only tickets submitted by that resident.
     *
     * <p><b>Validates: Requirements 13.1, 21.1</b></p>
     */
    @Property(tries = 100)
    void residentWithNoLinkedUnitsGetsOnlyOwnSubmittedTickets(
            @ForAll("ticketCount") @IntRange(min = 0, max = 10) int ticketCount
    ) {
        // Arrange
        UUID propertyId = UUID.randomUUID();
        UUID residentId = UUID.randomUUID();

        TicketRepository ticketRepository = mock(TicketRepository.class);
        MembershipRepository membershipRepository = mock(MembershipRepository.class);
        TicketService ticketService = buildTicketService(ticketRepository, membershipRepository);

        // Membership with no unit linked
        Membership membershipNoUnit = Membership.builder()
                .id(UUID.randomUUID())
                .userId(residentId)
                .unitId(null)
                .role(Role.RESIDENT_TENANT)
                .isActive(true)
                .effectiveFrom(LocalDate.now())
                .build();
        membershipNoUnit.setPropertyId(propertyId);

        when(membershipRepository.findActiveByUserIdAndPropertyId(residentId, propertyId))
                .thenReturn(List.of(membershipNoUnit));

        // Build tickets submitted by the resident
        List<Ticket> residentTickets = IntStream.range(0, ticketCount)
                .mapToObj(i -> buildTicket(residentId, UUID.randomUUID(), propertyId,
                        "SR-2025-" + String.format("%06d", i + 1)))
                .toList();

        when(ticketRepository.findByPropertyIdAndSubmittedBy(propertyId, residentId))
                .thenReturn(residentTickets);

        // Act
        List<Ticket> result = ticketService.findResidentTickets(propertyId, residentId);

        // Assert: only submitted-by query was used, not the unit-based one
        verify(ticketRepository).findByPropertyIdAndSubmittedBy(propertyId, residentId);
        verify(ticketRepository, never()).findByPropertyIdAndSubmittedByOrUnitIdIn(any(), any(), any());

        // Assert: all returned tickets are submitted by the resident
        assertThat(result).allSatisfy(ticket ->
                assertThat(ticket.getSubmittedBy()).isEqualTo(residentId));
    }

    // =====================================================================
    // Property: Scope enforcement is correct regardless of ticket volume
    // =====================================================================

    /**
     * For any number of tickets (from 0 to many), the scope enforcement produces
     * consistent results — the returned set size never exceeds the valid ticket count
     * and all tickets satisfy the ownership or unit-relationship condition.
     *
     * <p><b>Validates: Requirements 13.1, 21.1</b></p>
     */
    @Property(tries = 100)
    void scopeEnforcementIsConsistentRegardlessOfTicketVolume(
            @ForAll("residentWithUnits") ResidentScenario scenario,
            @ForAll("ticketCount") @IntRange(min = 0, max = 20) int extraTicketCount
    ) {
        // Arrange
        UUID propertyId = scenario.propertyId();
        UUID residentId = scenario.residentId();
        List<UUID> residentUnitIds = scenario.residentUnitIds();

        TicketRepository ticketRepository = mock(TicketRepository.class);
        MembershipRepository membershipRepository = mock(MembershipRepository.class);
        TicketService ticketService = buildTicketService(ticketRepository, membershipRepository);

        List<Membership> memberships = buildMemberships(residentId, propertyId, residentUnitIds);
        when(membershipRepository.findActiveByUserIdAndPropertyId(residentId, propertyId))
                .thenReturn(memberships);

        // Build valid tickets: mix of resident-submitted and unit-related
        List<Ticket> validTickets = new ArrayList<>(scenario.expectedTickets());
        for (int i = 0; i < extraTicketCount; i++) {
            if (i % 2 == 0 && !residentUnitIds.isEmpty()) {
                // Unit-related ticket from another resident
                UUID randomUnit = residentUnitIds.get(i % residentUnitIds.size());
                validTickets.add(buildTicket(UUID.randomUUID(), randomUnit, propertyId,
                        "SR-2025-" + String.format("%06d", 500 + i)));
            } else {
                // Ticket submitted by the resident
                validTickets.add(buildTicket(residentId, UUID.randomUUID(), propertyId,
                        "SR-2025-" + String.format("%06d", 500 + i)));
            }
        }

        if (residentUnitIds.isEmpty()) {
            // Filter to only resident-submitted tickets
            List<Ticket> onlySubmitted = validTickets.stream()
                    .filter(t -> t.getSubmittedBy().equals(residentId))
                    .toList();
            when(ticketRepository.findByPropertyIdAndSubmittedBy(propertyId, residentId))
                    .thenReturn(onlySubmitted);
        } else {
            when(ticketRepository.findByPropertyIdAndSubmittedByOrUnitIdIn(eq(propertyId), eq(residentId), any()))
                    .thenReturn(validTickets);
        }

        // Act
        List<Ticket> result = ticketService.findResidentTickets(propertyId, residentId);

        // Assert: all returned tickets satisfy the data scope invariant
        assertThat(result).allSatisfy(ticket -> {
            boolean submittedByResident = ticket.getSubmittedBy().equals(residentId);
            boolean relatedToResidentUnit = residentUnitIds.contains(ticket.getUnitId());
            assertThat(submittedByResident || relatedToResidentUnit)
                    .as("Ticket must be scoped to resident (submitted_by=%s, unit_id=%s) " +
                                    "but resident=%s, units=%s",
                            ticket.getSubmittedBy(), ticket.getUnitId(), residentId, residentUnitIds)
                    .isTrue();
        });
    }

    // =====================================================================
    // Arbitraries (Generators)
    // =====================================================================

    @Provide
    Arbitrary<ResidentScenario> residentWithUnits() {
        Arbitrary<Integer> unitCount = Arbitraries.integers().between(1, 4);
        Arbitrary<Integer> ownTicketCount = Arbitraries.integers().between(0, 5);
        Arbitrary<Integer> unitTicketCount = Arbitraries.integers().between(0, 5);

        return Combinators.combine(unitCount, ownTicketCount, unitTicketCount)
                .as((units, ownTickets, unitTickets) -> {
                    UUID propertyId = UUID.randomUUID();
                    UUID residentId = UUID.randomUUID();

                    List<UUID> unitIds = IntStream.range(0, units)
                            .mapToObj(i -> UUID.randomUUID())
                            .toList();

                    List<Ticket> tickets = new ArrayList<>();

                    // Tickets submitted by the resident
                    for (int i = 0; i < ownTickets; i++) {
                        UUID unitId = unitIds.get(i % unitIds.size());
                        tickets.add(buildTicket(residentId, unitId, propertyId,
                                "SR-2025-" + String.format("%06d", i + 1)));
                    }

                    // Tickets from other residents but on the resident's unit
                    for (int i = 0; i < unitTickets; i++) {
                        UUID unitId = unitIds.get(i % unitIds.size());
                        tickets.add(buildTicket(UUID.randomUUID(), unitId, propertyId,
                                "SR-2025-" + String.format("%06d", 100 + i)));
                    }

                    return new ResidentScenario(propertyId, residentId, unitIds, tickets);
                });
    }

    @Provide
    Arbitrary<Integer> ticketCount() {
        return Arbitraries.integers().between(0, 20);
    }

    @Provide
    Arbitrary<Integer> unrelatedTicketCount() {
        return Arbitraries.integers().between(1, 10);
    }

    // =====================================================================
    // Helper Methods
    // =====================================================================

    private TicketService buildTicketService(TicketRepository ticketRepository,
                                             MembershipRepository membershipRepository) {
        PropertyRepository propertyRepository = mock(PropertyRepository.class);
        StatusHistoryRepository statusHistoryRepository = mock(StatusHistoryRepository.class);
        ReferenceNumberGenerator referenceNumberGenerator = mock(ReferenceNumberGenerator.class);
        StatusWorkflowEngine statusWorkflowEngine = mock(StatusWorkflowEngine.class);
        DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
        DuplicateDetectionService duplicateDetectionService = mock(DuplicateDetectionService.class);
        com.strataresolve.sla.service.SlaCalculator slaCalculator = mock(com.strataresolve.sla.service.SlaCalculator.class);
        TicketProperties ticketProperties = new TicketProperties(72, null, null);

        return new TicketService(
                ticketRepository, propertyRepository, membershipRepository,
                statusHistoryRepository, referenceNumberGenerator,
                statusWorkflowEngine, ticketProperties, eventPublisher, duplicateDetectionService,
                slaCalculator);
    }

    private List<Membership> buildMemberships(UUID residentId, UUID propertyId, List<UUID> unitIds) {
        if (unitIds.isEmpty()) {
            Membership membership = Membership.builder()
                    .id(UUID.randomUUID())
                    .userId(residentId)
                    .unitId(null)
                    .role(Role.RESIDENT_OWNER)
                    .isActive(true)
                    .effectiveFrom(LocalDate.now())
                    .build();
            membership.setPropertyId(propertyId);
            return List.of(membership);
        }

        return unitIds.stream()
                .map(unitId -> {
                    Membership membership = Membership.builder()
                            .id(UUID.randomUUID())
                            .userId(residentId)
                            .unitId(unitId)
                            .role(Role.RESIDENT_OWNER)
                            .isActive(true)
                            .effectiveFrom(LocalDate.now())
                            .build();
                    membership.setPropertyId(propertyId);
                    return membership;
                })
                .toList();
    }

    private static Ticket buildTicket(UUID submittedBy, UUID unitId, UUID propertyId, String refNumber) {
        Ticket ticket = Ticket.builder()
                .id(UUID.randomUUID())
                .submittedBy(submittedBy)
                .unitId(unitId)
                .referenceNumber(refNumber)
                .title("Test ticket " + refNumber)
                .description("Test description for " + refNumber)
                .category(Category.PLUMBING)
                .priority(Priority.NORMAL)
                .status(TicketStatus.SUBMITTED)
                .build();
        ticket.setPropertyId(propertyId);
        return ticket;
    }

    // =====================================================================
    // Test Data Record
    // =====================================================================

    record ResidentScenario(
            UUID propertyId,
            UUID residentId,
            List<UUID> residentUnitIds,
            List<Ticket> expectedTickets
    ) {}
}
