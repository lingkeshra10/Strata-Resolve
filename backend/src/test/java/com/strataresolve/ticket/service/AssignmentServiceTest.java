package com.strataresolve.ticket.service;

import com.strataresolve.shared.event.AssignmentCreatedEvent;
import com.strataresolve.shared.event.DomainEventPublisher;
import com.strataresolve.shared.exception.BusinessRuleViolationException;
import com.strataresolve.shared.exception.ResourceNotFoundException;
import com.strataresolve.ticket.domain.Assignment;
import com.strataresolve.ticket.domain.AssignmentType;
import com.strataresolve.ticket.domain.Ticket;
import com.strataresolve.ticket.domain.TicketStatus;
import com.strataresolve.ticket.dto.CreateAssignmentRequest;
import com.strataresolve.ticket.dto.TransitionTicketStatusRequest;
import com.strataresolve.ticket.repository.AssignmentRepository;
import com.strataresolve.ticket.repository.TicketRepository;
import com.strataresolve.user.domain.Membership;
import com.strataresolve.user.domain.Role;
import com.strataresolve.user.repository.MembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AssignmentService - Assignment Logic")
class AssignmentServiceTest {

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private TicketService ticketService;

    @Mock
    private DomainEventPublisher eventPublisher;

    private AssignmentService assignmentService;

    private UUID propertyId;
    private UUID ticketId;
    private UUID technicianUserId;
    private UUID vendorUserId;
    private UUID actingUserId;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        assignmentService = new AssignmentService(
                assignmentRepository, ticketRepository, membershipRepository,
                ticketService, eventPublisher);

        propertyId = UUID.randomUUID();
        ticketId = UUID.randomUUID();
        technicianUserId = UUID.randomUUID();
        vendorUserId = UUID.randomUUID();
        actingUserId = UUID.randomUUID();

        ticket = Ticket.builder()
                .id(ticketId)
                .submittedBy(UUID.randomUUID())
                .unitId(UUID.randomUUID())
                .referenceNumber("SR-2025-000001")
                .title("Test Ticket")
                .description("Test description")
                .status(TicketStatus.ACKNOWLEDGED)
                .build();
        ticket.setPropertyId(propertyId);
    }

    @Test
    @DisplayName("should successfully assign ticket to technician with active TECHNICIAN membership")
    void shouldAssignToTechnicianSuccessfully() {
        CreateAssignmentRequest request = new CreateAssignmentRequest(
                ticketId, technicianUserId, AssignmentType.TECHNICIAN);

        Membership technicianMembership = Membership.builder()
                .id(UUID.randomUUID())
                .userId(technicianUserId)
                .role(Role.TECHNICIAN)
                .isActive(true)
                .effectiveFrom(LocalDate.now())
                .build();
        technicianMembership.setPropertyId(propertyId);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(membershipRepository.findActiveByUserIdAndPropertyId(technicianUserId, propertyId))
                .thenReturn(List.of(technicianMembership));
        when(ticketService.transitionStatus(eq(ticketId), any(TransitionTicketStatusRequest.class), eq(actingUserId)))
                .thenReturn(ticket);
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> {
            Assignment a = invocation.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        Assignment result = assignmentService.createAssignment(request, actingUserId);

        assertThat(result).isNotNull();
        assertThat(result.getTicketId()).isEqualTo(ticketId);
        assertThat(result.getAssignedTo()).isEqualTo(technicianUserId);
        assertThat(result.getType()).isEqualTo(AssignmentType.TECHNICIAN);

        // Verify status transition to ASSIGNED was called
        ArgumentCaptor<TransitionTicketStatusRequest> transitionCaptor =
                ArgumentCaptor.forClass(TransitionTicketStatusRequest.class);
        verify(ticketService).transitionStatus(eq(ticketId), transitionCaptor.capture(), eq(actingUserId));
        assertThat(transitionCaptor.getValue().targetStatus()).isEqualTo(TicketStatus.ASSIGNED);
    }

    @Test
    @DisplayName("should successfully assign ticket to vendor user with active VENDOR_ADMIN membership")
    void shouldAssignToVendorSuccessfully() {
        CreateAssignmentRequest request = new CreateAssignmentRequest(
                ticketId, vendorUserId, AssignmentType.VENDOR);

        Membership vendorMembership = Membership.builder()
                .id(UUID.randomUUID())
                .userId(vendorUserId)
                .role(Role.VENDOR_ADMIN)
                .isActive(true)
                .effectiveFrom(LocalDate.now())
                .build();
        vendorMembership.setPropertyId(propertyId);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(membershipRepository.findActiveByUserIdAndPropertyId(vendorUserId, propertyId))
                .thenReturn(List.of(vendorMembership));
        when(ticketService.transitionStatus(eq(ticketId), any(TransitionTicketStatusRequest.class), eq(actingUserId)))
                .thenReturn(ticket);
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> {
            Assignment a = invocation.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        Assignment result = assignmentService.createAssignment(request, actingUserId);

        assertThat(result).isNotNull();
        assertThat(result.getTicketId()).isEqualTo(ticketId);
        assertThat(result.getAssignedTo()).isEqualTo(vendorUserId);
        assertThat(result.getType()).isEqualTo(AssignmentType.VENDOR);
    }

    @Test
    @DisplayName("should successfully assign ticket to vendor user with VENDOR_TECHNICIAN membership")
    void shouldAssignToVendorTechnicianSuccessfully() {
        CreateAssignmentRequest request = new CreateAssignmentRequest(
                ticketId, vendorUserId, AssignmentType.VENDOR);

        Membership vendorTechMembership = Membership.builder()
                .id(UUID.randomUUID())
                .userId(vendorUserId)
                .role(Role.VENDOR_TECHNICIAN)
                .isActive(true)
                .effectiveFrom(LocalDate.now())
                .build();
        vendorTechMembership.setPropertyId(propertyId);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(membershipRepository.findActiveByUserIdAndPropertyId(vendorUserId, propertyId))
                .thenReturn(List.of(vendorTechMembership));
        when(ticketService.transitionStatus(eq(ticketId), any(TransitionTicketStatusRequest.class), eq(actingUserId)))
                .thenReturn(ticket);
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> {
            Assignment a = invocation.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        Assignment result = assignmentService.createAssignment(request, actingUserId);

        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(AssignmentType.VENDOR);
    }

    @Test
    @DisplayName("should reject assignment when assignee has no active membership for the property")
    void shouldRejectWhenNoActiveMembership() {
        CreateAssignmentRequest request = new CreateAssignmentRequest(
                ticketId, technicianUserId, AssignmentType.TECHNICIAN);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(membershipRepository.findActiveByUserIdAndPropertyId(technicianUserId, propertyId))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> assignmentService.createAssignment(request, actingUserId))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("does not have an active membership");

        verify(assignmentRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("should reject TECHNICIAN assignment when assignee has VENDOR role")
    void shouldRejectTechnicianAssignmentWithVendorRole() {
        CreateAssignmentRequest request = new CreateAssignmentRequest(
                ticketId, vendorUserId, AssignmentType.TECHNICIAN);

        Membership vendorMembership = Membership.builder()
                .id(UUID.randomUUID())
                .userId(vendorUserId)
                .role(Role.VENDOR_ADMIN)
                .isActive(true)
                .effectiveFrom(LocalDate.now())
                .build();
        vendorMembership.setPropertyId(propertyId);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(membershipRepository.findActiveByUserIdAndPropertyId(vendorUserId, propertyId))
                .thenReturn(List.of(vendorMembership));

        assertThatThrownBy(() -> assignmentService.createAssignment(request, actingUserId))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("does not have the required role")
                .hasMessageContaining("TECHNICIAN");

        verify(assignmentRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("should reject VENDOR assignment when assignee has TECHNICIAN role")
    void shouldRejectVendorAssignmentWithTechnicianRole() {
        CreateAssignmentRequest request = new CreateAssignmentRequest(
                ticketId, technicianUserId, AssignmentType.VENDOR);

        Membership techMembership = Membership.builder()
                .id(UUID.randomUUID())
                .userId(technicianUserId)
                .role(Role.TECHNICIAN)
                .isActive(true)
                .effectiveFrom(LocalDate.now())
                .build();
        techMembership.setPropertyId(propertyId);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(membershipRepository.findActiveByUserIdAndPropertyId(technicianUserId, propertyId))
                .thenReturn(List.of(techMembership));

        assertThatThrownBy(() -> assignmentService.createAssignment(request, actingUserId))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("does not have the required role")
                .hasMessageContaining("VENDOR_ADMIN or VENDOR_TECHNICIAN");

        verify(assignmentRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("should reject assignment when ticket does not exist")
    void shouldRejectWhenTicketNotFound() {
        CreateAssignmentRequest request = new CreateAssignmentRequest(
                ticketId, technicianUserId, AssignmentType.TECHNICIAN);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assignmentService.createAssignment(request, actingUserId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(assignmentRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("should publish AssignmentCreatedEvent on successful assignment")
    void shouldPublishEventOnSuccess() {
        CreateAssignmentRequest request = new CreateAssignmentRequest(
                ticketId, technicianUserId, AssignmentType.TECHNICIAN);

        Membership technicianMembership = Membership.builder()
                .id(UUID.randomUUID())
                .userId(technicianUserId)
                .role(Role.TECHNICIAN)
                .isActive(true)
                .effectiveFrom(LocalDate.now())
                .build();
        technicianMembership.setPropertyId(propertyId);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(membershipRepository.findActiveByUserIdAndPropertyId(technicianUserId, propertyId))
                .thenReturn(List.of(technicianMembership));
        when(ticketService.transitionStatus(eq(ticketId), any(TransitionTicketStatusRequest.class), eq(actingUserId)))
                .thenReturn(ticket);
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> {
            Assignment a = invocation.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        assignmentService.createAssignment(request, actingUserId);

        ArgumentCaptor<AssignmentCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(AssignmentCreatedEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());

        AssignmentCreatedEvent event = eventCaptor.getValue();
        assertThat(event.getActingUserId()).isEqualTo(actingUserId);
        assertThat(event.getPropertyId()).isEqualTo(propertyId);
        assertThat(event.getTicketId()).isEqualTo(ticketId);
        assertThat(event.getAssigneeId()).isEqualTo(technicianUserId);
        assertThat(event.getAssignmentType()).isEqualTo("TECHNICIAN");
    }

    @Test
    @DisplayName("should reject TECHNICIAN assignment when assignee only has RESIDENT role")
    void shouldRejectTechnicianAssignmentWithResidentRole() {
        CreateAssignmentRequest request = new CreateAssignmentRequest(
                ticketId, technicianUserId, AssignmentType.TECHNICIAN);

        Membership residentMembership = Membership.builder()
                .id(UUID.randomUUID())
                .userId(technicianUserId)
                .role(Role.RESIDENT_OWNER)
                .isActive(true)
                .effectiveFrom(LocalDate.now())
                .build();
        residentMembership.setPropertyId(propertyId);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(membershipRepository.findActiveByUserIdAndPropertyId(technicianUserId, propertyId))
                .thenReturn(List.of(residentMembership));

        assertThatThrownBy(() -> assignmentService.createAssignment(request, actingUserId))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("does not have the required role");

        verify(assignmentRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }
}
