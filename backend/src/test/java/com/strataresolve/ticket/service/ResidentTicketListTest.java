package com.strataresolve.ticket.service;

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
import com.strataresolve.user.domain.Membership;
import com.strataresolve.user.domain.Role;
import com.strataresolve.user.repository.MembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for resident ticket list data scope enforcement.
 * Validates Requirement 21.1: Residents see only tickets they submitted or related to their linked unit.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TicketService - Resident Ticket List Data Scope")
class ResidentTicketListTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private StatusHistoryRepository statusHistoryRepository;

    @Mock
    private ReferenceNumberGenerator referenceNumberGenerator;

    @Mock
    private StatusWorkflowEngine statusWorkflowEngine;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Mock
    private DuplicateDetectionService duplicateDetectionService;

    @Mock
    private com.strataresolve.sla.service.SlaCalculator slaCalculator;

    private TicketService ticketService;

    private UUID propertyId;
    private UUID residentId;
    private UUID unitId;
    private UUID otherUnitId;

    @BeforeEach
    void setUp() {
        TicketProperties ticketProperties = new TicketProperties(72, null, null);
        ticketService = new TicketService(
                ticketRepository, propertyRepository, membershipRepository,
                statusHistoryRepository, referenceNumberGenerator,
                statusWorkflowEngine, ticketProperties, eventPublisher, duplicateDetectionService,
                slaCalculator);

        propertyId = UUID.randomUUID();
        residentId = UUID.randomUUID();
        unitId = UUID.randomUUID();
        otherUnitId = UUID.randomUUID();
    }

    @Test
    @DisplayName("should return only tickets submitted by resident or related to resident's unit")
    void shouldReturnOnlyResidentTicketsOrUnitTickets() {
        Membership membership = Membership.builder()
                .id(UUID.randomUUID())
                .userId(residentId)
                .unitId(unitId)
                .role(Role.RESIDENT_OWNER)
                .isActive(true)
                .effectiveFrom(LocalDate.now())
                .build();
        membership.setPropertyId(propertyId);

        Ticket residentOwnTicket = buildTicket(residentId, unitId, "SR-2025-000001");
        Ticket unitRelatedTicket = buildTicket(UUID.randomUUID(), unitId, "SR-2025-000002");

        when(membershipRepository.findActiveByUserIdAndPropertyId(residentId, propertyId))
                .thenReturn(List.of(membership));
        when(ticketRepository.findByPropertyIdAndSubmittedByOrUnitIdIn(propertyId, residentId, List.of(unitId)))
                .thenReturn(List.of(residentOwnTicket, unitRelatedTicket));

        List<Ticket> result = ticketService.findResidentTickets(propertyId, residentId);

        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyInAnyOrder(residentOwnTicket, unitRelatedTicket);
        verify(ticketRepository).findByPropertyIdAndSubmittedByOrUnitIdIn(propertyId, residentId, List.of(unitId));
    }

    @Test
    @DisplayName("should exclude tickets from other residents and unrelated units")
    void shouldExcludeOtherResidentsTickets() {
        Membership membership = Membership.builder()
                .id(UUID.randomUUID())
                .userId(residentId)
                .unitId(unitId)
                .role(Role.RESIDENT_OWNER)
                .isActive(true)
                .effectiveFrom(LocalDate.now())
                .build();
        membership.setPropertyId(propertyId);

        // Only return the resident's own ticket (repository enforces the filter)
        Ticket residentOwnTicket = buildTicket(residentId, unitId, "SR-2025-000001");

        when(membershipRepository.findActiveByUserIdAndPropertyId(residentId, propertyId))
                .thenReturn(List.of(membership));
        when(ticketRepository.findByPropertyIdAndSubmittedByOrUnitIdIn(propertyId, residentId, List.of(unitId)))
                .thenReturn(List.of(residentOwnTicket));

        List<Ticket> result = ticketService.findResidentTickets(propertyId, residentId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSubmittedBy()).isEqualTo(residentId);
    }

    @Test
    @DisplayName("should return only submitted tickets when resident has no linked unit")
    void shouldReturnOnlySubmittedTicketsWhenNoUnitLinked() {
        // Membership without unit linkage
        Membership membershipNoUnit = Membership.builder()
                .id(UUID.randomUUID())
                .userId(residentId)
                .unitId(null)
                .role(Role.RESIDENT_TENANT)
                .isActive(true)
                .effectiveFrom(LocalDate.now())
                .build();
        membershipNoUnit.setPropertyId(propertyId);

        Ticket residentTicket = buildTicket(residentId, otherUnitId, "SR-2025-000001");

        when(membershipRepository.findActiveByUserIdAndPropertyId(residentId, propertyId))
                .thenReturn(List.of(membershipNoUnit));
        when(ticketRepository.findByPropertyIdAndSubmittedBy(propertyId, residentId))
                .thenReturn(List.of(residentTicket));

        List<Ticket> result = ticketService.findResidentTickets(propertyId, residentId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSubmittedBy()).isEqualTo(residentId);
        verify(ticketRepository).findByPropertyIdAndSubmittedBy(propertyId, residentId);
    }

    @Test
    @DisplayName("should return empty list when resident has no tickets and no unit tickets")
    void shouldReturnEmptyListWhenNoTickets() {
        Membership membership = Membership.builder()
                .id(UUID.randomUUID())
                .userId(residentId)
                .unitId(unitId)
                .role(Role.RESIDENT_OWNER)
                .isActive(true)
                .effectiveFrom(LocalDate.now())
                .build();
        membership.setPropertyId(propertyId);

        when(membershipRepository.findActiveByUserIdAndPropertyId(residentId, propertyId))
                .thenReturn(List.of(membership));
        when(ticketRepository.findByPropertyIdAndSubmittedByOrUnitIdIn(propertyId, residentId, List.of(unitId)))
                .thenReturn(Collections.emptyList());

        List<Ticket> result = ticketService.findResidentTickets(propertyId, residentId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should handle resident with multiple units across multiple memberships")
    void shouldHandleMultipleUnits() {
        UUID secondUnitId = UUID.randomUUID();

        Membership membership1 = Membership.builder()
                .id(UUID.randomUUID())
                .userId(residentId)
                .unitId(unitId)
                .role(Role.RESIDENT_OWNER)
                .isActive(true)
                .effectiveFrom(LocalDate.now())
                .build();
        membership1.setPropertyId(propertyId);

        Membership membership2 = Membership.builder()
                .id(UUID.randomUUID())
                .userId(residentId)
                .unitId(secondUnitId)
                .role(Role.RESIDENT_TENANT)
                .isActive(true)
                .effectiveFrom(LocalDate.now())
                .build();
        membership2.setPropertyId(propertyId);

        Ticket ticket1 = buildTicket(residentId, unitId, "SR-2025-000001");
        Ticket ticket2 = buildTicket(UUID.randomUUID(), secondUnitId, "SR-2025-000002");

        when(membershipRepository.findActiveByUserIdAndPropertyId(residentId, propertyId))
                .thenReturn(List.of(membership1, membership2));
        when(ticketRepository.findByPropertyIdAndSubmittedByOrUnitIdIn(
                propertyId, residentId, List.of(unitId, secondUnitId)))
                .thenReturn(List.of(ticket1, ticket2));

        List<Ticket> result = ticketService.findResidentTickets(propertyId, residentId);

        assertThat(result).hasSize(2);
        verify(ticketRepository).findByPropertyIdAndSubmittedByOrUnitIdIn(
                propertyId, residentId, List.of(unitId, secondUnitId));
    }

    @Test
    @DisplayName("should deduplicate unit IDs when resident has duplicate unit memberships")
    void shouldDeduplicateUnitIds() {
        // Two memberships pointing to the same unit (e.g. owner and tenant)
        Membership membership1 = Membership.builder()
                .id(UUID.randomUUID())
                .userId(residentId)
                .unitId(unitId)
                .role(Role.RESIDENT_OWNER)
                .isActive(true)
                .effectiveFrom(LocalDate.now())
                .build();
        membership1.setPropertyId(propertyId);

        Membership membership2 = Membership.builder()
                .id(UUID.randomUUID())
                .userId(residentId)
                .unitId(unitId) // Same unit
                .role(Role.RESIDENT_TENANT)
                .isActive(true)
                .effectiveFrom(LocalDate.now())
                .build();
        membership2.setPropertyId(propertyId);

        when(membershipRepository.findActiveByUserIdAndPropertyId(residentId, propertyId))
                .thenReturn(List.of(membership1, membership2));
        when(ticketRepository.findByPropertyIdAndSubmittedByOrUnitIdIn(propertyId, residentId, List.of(unitId)))
                .thenReturn(Collections.emptyList());

        ticketService.findResidentTickets(propertyId, residentId);

        // Should only pass one unit ID (deduplicated)
        verify(ticketRepository).findByPropertyIdAndSubmittedByOrUnitIdIn(
                propertyId, residentId, List.of(unitId));
    }

    private Ticket buildTicket(UUID submittedBy, UUID unitId, String refNumber) {
        Ticket ticket = Ticket.builder()
                .id(UUID.randomUUID())
                .submittedBy(submittedBy)
                .unitId(unitId)
                .referenceNumber(refNumber)
                .title("Test ticket " + refNumber)
                .description("Test description")
                .category(Category.PLUMBING)
                .priority(Priority.NORMAL)
                .status(TicketStatus.SUBMITTED)
                .build();
        ticket.setPropertyId(propertyId);
        return ticket;
    }
}
