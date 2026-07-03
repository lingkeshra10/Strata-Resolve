package com.strataresolve.ticket.service;

import com.strataresolve.shared.event.DomainEventPublisher;
import com.strataresolve.shared.event.StatusChangedEvent;
import com.strataresolve.shared.exception.BusinessRuleViolationException;
import com.strataresolve.shared.exception.InvalidTransitionException;
import com.strataresolve.shared.exception.ResourceNotFoundException;
import com.strataresolve.ticket.config.TicketProperties;
import com.strataresolve.ticket.domain.StatusHistory;
import com.strataresolve.ticket.domain.Ticket;
import com.strataresolve.ticket.domain.TicketStatus;
import com.strataresolve.ticket.dto.TransitionTicketStatusRequest;
import com.strataresolve.ticket.policy.StatusWorkflowEngine;
import com.strataresolve.ticket.repository.StatusHistoryRepository;
import com.strataresolve.ticket.repository.TicketRepository;
import com.strataresolve.property.repository.PropertyRepository;
import com.strataresolve.user.repository.MembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TicketService - Status Transitions")
class TicketServiceTransitionTest {

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

    private UUID ticketId;
    private UUID actingUserId;
    private UUID propertyId;

    @BeforeEach
    void setUp() {
        TicketProperties ticketProperties = new TicketProperties(72, null, null);
        ticketService = new TicketService(
                ticketRepository, propertyRepository, membershipRepository,
                statusHistoryRepository, referenceNumberGenerator,
                statusWorkflowEngine, ticketProperties, eventPublisher, duplicateDetectionService,
                slaCalculator);

        ticketId = UUID.randomUUID();
        actingUserId = UUID.randomUUID();
        propertyId = UUID.randomUUID();
    }

    private Ticket buildTicket(TicketStatus status) {
        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .status(status)
                .submittedBy(UUID.randomUUID())
                .unitId(UUID.randomUUID())
                .referenceNumber("SR-2025-000001")
                .title("Test Ticket")
                .description("Test description")
                .build();
        ticket.setPropertyId(propertyId);
        return ticket;
    }

    @Nested
    @DisplayName("Acknowledge Transition")
    class AcknowledgeTransition {

        @Test
        @DisplayName("should transition to ACKNOWLEDGED and record acknowledged_at timestamp")
        void shouldTransitionToAcknowledgedAndRecordTimestamp() {
            Ticket ticket = buildTicket(TicketStatus.SUBMITTED);
            TransitionTicketStatusRequest request = new TransitionTicketStatusRequest(
                    TicketStatus.ACKNOWLEDGED, "Ticket acknowledged by manager");

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

            Ticket result = ticketService.transitionStatus(ticketId, request, actingUserId);

            assertThat(result.getStatus()).isEqualTo(TicketStatus.ACKNOWLEDGED);
            assertThat(result.getAcknowledgedAt()).isNotNull();
            verify(statusWorkflowEngine).validateTransition(TicketStatus.SUBMITTED, TicketStatus.ACKNOWLEDGED);
        }
    }

    @Nested
    @DisplayName("Resolve Transition")
    class ResolveTransition {

        @Test
        @DisplayName("should transition to RESOLVED and record resolved_at timestamp")
        void shouldTransitionToResolvedAndRecordTimestamp() {
            Ticket ticket = buildTicket(TicketStatus.READY_FOR_VERIFICATION);
            TransitionTicketStatusRequest request = new TransitionTicketStatusRequest(
                    TicketStatus.RESOLVED, "Work verified and approved");

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

            Ticket result = ticketService.transitionStatus(ticketId, request, actingUserId);

            assertThat(result.getStatus()).isEqualTo(TicketStatus.RESOLVED);
            assertThat(result.getResolvedAt()).isNotNull();
            verify(statusWorkflowEngine).validateTransition(TicketStatus.READY_FOR_VERIFICATION, TicketStatus.RESOLVED);
        }
    }

    @Nested
    @DisplayName("StatusHistory Recording")
    class StatusHistoryRecording {

        @Test
        @DisplayName("should record StatusHistory entry with previous status, new status, acting user, and reason")
        void shouldRecordStatusHistoryEntry() {
            Ticket ticket = buildTicket(TicketStatus.SUBMITTED);
            TransitionTicketStatusRequest request = new TransitionTicketStatusRequest(
                    TicketStatus.ACKNOWLEDGED, "Reviewed the issue");

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

            ticketService.transitionStatus(ticketId, request, actingUserId);

            ArgumentCaptor<StatusHistory> historyCaptor = ArgumentCaptor.forClass(StatusHistory.class);
            verify(statusHistoryRepository).save(historyCaptor.capture());

            StatusHistory history = historyCaptor.getValue();
            assertThat(history.getTicketId()).isEqualTo(ticketId);
            assertThat(history.getPreviousStatus()).isEqualTo(TicketStatus.SUBMITTED);
            assertThat(history.getNewStatus()).isEqualTo(TicketStatus.ACKNOWLEDGED);
            assertThat(history.getChangedBy()).isEqualTo(actingUserId);
            assertThat(history.getReason()).isEqualTo("Reviewed the issue");
            assertThat(history.getChangedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("StatusChangedEvent Publishing")
    class EventPublishing {

        @Test
        @DisplayName("should publish StatusChangedEvent after successful transition")
        void shouldPublishStatusChangedEvent() {
            Ticket ticket = buildTicket(TicketStatus.ACKNOWLEDGED);
            TransitionTicketStatusRequest request = new TransitionTicketStatusRequest(
                    TicketStatus.ASSIGNED, "Assigned to maintenance team");

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

            ticketService.transitionStatus(ticketId, request, actingUserId);

            ArgumentCaptor<StatusChangedEvent> eventCaptor = ArgumentCaptor.forClass(StatusChangedEvent.class);
            verify(eventPublisher).publish(eventCaptor.capture());

            StatusChangedEvent event = eventCaptor.getValue();
            assertThat(event.getActingUserId()).isEqualTo(actingUserId);
            assertThat(event.getPropertyId()).isEqualTo(propertyId);
            assertThat(event.getTicketId()).isEqualTo(ticketId);
            assertThat(event.getPreviousStatus()).isEqualTo("ACKNOWLEDGED");
            assertThat(event.getNewStatus()).isEqualTo("ASSIGNED");
            assertThat(event.getReason()).isEqualTo("Assigned to maintenance team");
        }
    }

    @Nested
    @DisplayName("Invalid Transitions")
    class InvalidTransitions {

        @Test
        @DisplayName("should reject transition when ticket is not found")
        void shouldRejectWhenTicketNotFound() {
            TransitionTicketStatusRequest request = new TransitionTicketStatusRequest(
                    TicketStatus.ACKNOWLEDGED, null);

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ticketService.transitionStatus(ticketId, request, actingUserId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(ticketRepository, never()).save(any());
            verify(eventPublisher, never()).publish(any());
        }

        @Test
        @DisplayName("should reject transition when workflow engine denies it")
        void shouldRejectWhenWorkflowEngineDenies() {
            Ticket ticket = buildTicket(TicketStatus.SUBMITTED);
            TransitionTicketStatusRequest request = new TransitionTicketStatusRequest(
                    TicketStatus.CLOSED, null);

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
            doThrow(new InvalidTransitionException("SUBMITTED", "CLOSED"))
                    .when(statusWorkflowEngine).validateTransition(TicketStatus.SUBMITTED, TicketStatus.CLOSED);

            assertThatThrownBy(() -> ticketService.transitionStatus(ticketId, request, actingUserId))
                    .isInstanceOf(InvalidTransitionException.class);

            verify(ticketRepository, never()).save(any());
            verify(eventPublisher, never()).publish(any());
        }
    }

    @Nested
    @DisplayName("Reason Validation")
    class ReasonValidation {

        @Test
        @DisplayName("should require reason for REJECTED transition")
        void shouldRequireReasonForRejected() {
            Ticket ticket = buildTicket(TicketStatus.SUBMITTED);
            TransitionTicketStatusRequest request = new TransitionTicketStatusRequest(
                    TicketStatus.REJECTED, null);

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

            assertThatThrownBy(() -> ticketService.transitionStatus(ticketId, request, actingUserId))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("reason is required");

            verify(ticketRepository, never()).save(any());
        }

        @Test
        @DisplayName("should require reason for CANCELLED transition")
        void shouldRequireReasonForCancelled() {
            Ticket ticket = buildTicket(TicketStatus.SUBMITTED);
            TransitionTicketStatusRequest request = new TransitionTicketStatusRequest(
                    TicketStatus.CANCELLED, "   ");

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

            assertThatThrownBy(() -> ticketService.transitionStatus(ticketId, request, actingUserId))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("reason is required");
        }

        @Test
        @DisplayName("should require reason for REOPENED transition")
        void shouldRequireReasonForReopened() {
            Ticket ticket = buildTicket(TicketStatus.RESOLVED);
            TransitionTicketStatusRequest request = new TransitionTicketStatusRequest(
                    TicketStatus.REOPENED, "");

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

            assertThatThrownBy(() -> ticketService.transitionStatus(ticketId, request, actingUserId))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("reason is required");
        }

        @Test
        @DisplayName("should allow transition without reason when not required")
        void shouldAllowTransitionWithoutReasonWhenNotRequired() {
            Ticket ticket = buildTicket(TicketStatus.SUBMITTED);
            TransitionTicketStatusRequest request = new TransitionTicketStatusRequest(
                    TicketStatus.ACKNOWLEDGED, null);

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

            Ticket result = ticketService.transitionStatus(ticketId, request, actingUserId);

            assertThat(result.getStatus()).isEqualTo(TicketStatus.ACKNOWLEDGED);
        }
    }

    @Nested
    @DisplayName("Non-timestamp transitions")
    class NonTimestampTransitions {

        @Test
        @DisplayName("should not set acknowledgedAt when transitioning to other statuses")
        void shouldNotSetAcknowledgedAtForOtherTransitions() {
            Ticket ticket = buildTicket(TicketStatus.ACKNOWLEDGED);
            TransitionTicketStatusRequest request = new TransitionTicketStatusRequest(
                    TicketStatus.ASSIGNED, "Assigned to tech");

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

            Ticket result = ticketService.transitionStatus(ticketId, request, actingUserId);

            assertThat(result.getAcknowledgedAt()).isNull();
            assertThat(result.getResolvedAt()).isNull();
        }

        @Test
        @DisplayName("should transition to CLOSED without setting timestamps")
        void shouldTransitionToClosedWithoutTimestamps() {
            Ticket ticket = buildTicket(TicketStatus.RESOLVED);
            TransitionTicketStatusRequest request = new TransitionTicketStatusRequest(
                    TicketStatus.CLOSED, null);

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

            Ticket result = ticketService.transitionStatus(ticketId, request, actingUserId);

            assertThat(result.getStatus()).isEqualTo(TicketStatus.CLOSED);
        }
    }
}
