package com.strataresolve.ticket.service;

import com.strataresolve.shared.event.DomainEventPublisher;
import com.strataresolve.shared.event.StatusChangedEvent;
import com.strataresolve.shared.exception.BusinessRuleViolationException;
import com.strataresolve.shared.exception.ResourceNotFoundException;
import com.strataresolve.ticket.config.TicketProperties;
import com.strataresolve.ticket.domain.StatusHistory;
import com.strataresolve.ticket.domain.Ticket;
import com.strataresolve.ticket.domain.TicketStatus;
import com.strataresolve.ticket.dto.ReopenTicketRequest;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TicketService - Reopen Logic")
class TicketReopenServiceTest {

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
    private UUID userId;
    private UUID propertyId;

    @BeforeEach
    void setUp() {
        TicketProperties ticketProperties = new TicketProperties(72, null, null); // 72 hours window
        ticketService = new TicketService(
                ticketRepository, propertyRepository, membershipRepository,
                statusHistoryRepository, referenceNumberGenerator,
                statusWorkflowEngine, ticketProperties, eventPublisher, duplicateDetectionService,
                slaCalculator);

        ticketId = UUID.randomUUID();
        userId = UUID.randomUUID();
        propertyId = UUID.randomUUID();
    }

    private Ticket createTicketWithStatus(TicketStatus status) {
        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .submittedBy(userId)
                .unitId(UUID.randomUUID())
                .referenceNumber("SR-2025-000001")
                .title("Test ticket")
                .description("Test description")
                .status(status)
                .build();
        ticket.setPropertyId(propertyId);
        return ticket;
    }

    private StatusHistory createStatusHistoryEntry(TicketStatus previousStatus, TicketStatus newStatus, Instant changedAt) {
        return StatusHistory.builder()
                .id(UUID.randomUUID())
                .ticketId(ticketId)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .changedBy(userId)
                .reason("Status changed")
                .changedAt(changedAt)
                .build();
    }

    @Nested
    @DisplayName("Successful Reopen")
    class SuccessfulReopen {

        @Test
        @DisplayName("should reopen a CLOSED ticket within time window")
        void shouldReopenClosedTicketWithinTimeWindow() {
            Ticket ticket = createTicketWithStatus(TicketStatus.CLOSED);
            ReopenTicketRequest request = new ReopenTicketRequest("Issue not fully resolved");
            Instant closedAt = Instant.now().minus(24, ChronoUnit.HOURS); // closed 24 hours ago

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
            when(statusHistoryRepository.findByTicketIdOrderByChangedAtAsc(ticketId))
                    .thenReturn(List.of(
                            createStatusHistoryEntry(TicketStatus.RESOLVED, TicketStatus.CLOSED, closedAt)
                    ));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Ticket result = ticketService.reopenTicket(ticketId, request, userId);

            assertThat(result.getStatus()).isEqualTo(TicketStatus.REOPENED);
            verify(statusWorkflowEngine).validateTransition(TicketStatus.CLOSED, TicketStatus.REOPENED);
            verify(statusHistoryRepository).save(any(StatusHistory.class));
            verify(eventPublisher).publish(any(StatusChangedEvent.class));
        }

        @Test
        @DisplayName("should reopen a RESOLVED ticket within time window")
        void shouldReopenResolvedTicketWithinTimeWindow() {
            Ticket ticket = createTicketWithStatus(TicketStatus.RESOLVED);
            ReopenTicketRequest request = new ReopenTicketRequest("Work was incomplete");
            Instant resolvedAt = Instant.now().minus(48, ChronoUnit.HOURS); // resolved 48 hours ago

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
            when(statusHistoryRepository.findByTicketIdOrderByChangedAtAsc(ticketId))
                    .thenReturn(List.of(
                            createStatusHistoryEntry(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED, resolvedAt)
                    ));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Ticket result = ticketService.reopenTicket(ticketId, request, userId);

            assertThat(result.getStatus()).isEqualTo(TicketStatus.REOPENED);
            verify(statusWorkflowEngine).validateTransition(TicketStatus.RESOLVED, TicketStatus.REOPENED);
        }

        @Test
        @DisplayName("should publish StatusChangedEvent with correct details")
        void shouldPublishStatusChangedEventWithCorrectDetails() {
            Ticket ticket = createTicketWithStatus(TicketStatus.CLOSED);
            ReopenTicketRequest request = new ReopenTicketRequest("Problem recurred");
            Instant closedAt = Instant.now().minus(10, ChronoUnit.HOURS);

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
            when(statusHistoryRepository.findByTicketIdOrderByChangedAtAsc(ticketId))
                    .thenReturn(List.of(
                            createStatusHistoryEntry(TicketStatus.RESOLVED, TicketStatus.CLOSED, closedAt)
                    ));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ticketService.reopenTicket(ticketId, request, userId);

            ArgumentCaptor<StatusChangedEvent> eventCaptor = ArgumentCaptor.forClass(StatusChangedEvent.class);
            verify(eventPublisher).publish(eventCaptor.capture());

            StatusChangedEvent event = eventCaptor.getValue();
            assertThat(event.getActingUserId()).isEqualTo(userId);
            assertThat(event.getPropertyId()).isEqualTo(propertyId);
            assertThat(event.getTicketId()).isEqualTo(ticketId);
            assertThat(event.getPreviousStatus()).isEqualTo("CLOSED");
            assertThat(event.getNewStatus()).isEqualTo("REOPENED");
            assertThat(event.getReason()).isEqualTo("Problem recurred");
        }

        @Test
        @DisplayName("should record status history with reason on reopen")
        void shouldRecordStatusHistoryWithReasonOnReopen() {
            Ticket ticket = createTicketWithStatus(TicketStatus.CLOSED);
            ReopenTicketRequest request = new ReopenTicketRequest("The leak came back");
            Instant closedAt = Instant.now().minus(5, ChronoUnit.HOURS);

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
            when(statusHistoryRepository.findByTicketIdOrderByChangedAtAsc(ticketId))
                    .thenReturn(List.of(
                            createStatusHistoryEntry(TicketStatus.RESOLVED, TicketStatus.CLOSED, closedAt)
                    ));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ticketService.reopenTicket(ticketId, request, userId);

            ArgumentCaptor<StatusHistory> historyCaptor = ArgumentCaptor.forClass(StatusHistory.class);
            verify(statusHistoryRepository).save(historyCaptor.capture());

            StatusHistory history = historyCaptor.getValue();
            assertThat(history.getTicketId()).isEqualTo(ticketId);
            assertThat(history.getPreviousStatus()).isEqualTo(TicketStatus.CLOSED);
            assertThat(history.getNewStatus()).isEqualTo(TicketStatus.REOPENED);
            assertThat(history.getChangedBy()).isEqualTo(userId);
            assertThat(history.getReason()).isEqualTo("The leak came back");
        }
    }

    @Nested
    @DisplayName("Time Window Rejection")
    class TimeWindowRejection {

        @Test
        @DisplayName("should reject reopen when time window has expired")
        void shouldRejectReopenWhenTimeWindowExpired() {
            Ticket ticket = createTicketWithStatus(TicketStatus.CLOSED);
            ReopenTicketRequest request = new ReopenTicketRequest("Issue not resolved");
            Instant closedAt = Instant.now().minus(100, ChronoUnit.HOURS); // closed 100 hours ago (> 72h window)

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
            when(statusHistoryRepository.findByTicketIdOrderByChangedAtAsc(ticketId))
                    .thenReturn(List.of(
                            createStatusHistoryEntry(TicketStatus.RESOLVED, TicketStatus.CLOSED, closedAt)
                    ));

            assertThatThrownBy(() -> ticketService.reopenTicket(ticketId, request, userId))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("reopen window")
                    .hasMessageContaining("72 hours")
                    .hasMessageContaining("submit a new ticket");

            verify(ticketRepository, never()).save(any());
            verify(eventPublisher, never()).publish(any());
        }

        @Test
        @DisplayName("should reject reopen of RESOLVED ticket when time window has expired")
        void shouldRejectReopenOfResolvedTicketWhenTimeWindowExpired() {
            Ticket ticket = createTicketWithStatus(TicketStatus.RESOLVED);
            ReopenTicketRequest request = new ReopenTicketRequest("Still broken");
            Instant resolvedAt = Instant.now().minus(73, ChronoUnit.HOURS); // resolved 73 hours ago (> 72h window)

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
            when(statusHistoryRepository.findByTicketIdOrderByChangedAtAsc(ticketId))
                    .thenReturn(List.of(
                            createStatusHistoryEntry(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED, resolvedAt)
                    ));

            assertThatThrownBy(() -> ticketService.reopenTicket(ticketId, request, userId))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("submit a new ticket");

            verify(ticketRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Invalid Status Rejection")
    class InvalidStatusRejection {

        @Test
        @DisplayName("should reject reopen when ticket is in SUBMITTED status")
        void shouldRejectReopenWhenTicketIsSubmitted() {
            Ticket ticket = createTicketWithStatus(TicketStatus.SUBMITTED);
            ReopenTicketRequest request = new ReopenTicketRequest("Some reason");

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

            assertThatThrownBy(() -> ticketService.reopenTicket(ticketId, request, userId))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("CLOSED or RESOLVED")
                    .hasMessageContaining("SUBMITTED");

            verify(ticketRepository, never()).save(any());
            verify(eventPublisher, never()).publish(any());
        }

        @Test
        @DisplayName("should reject reopen when ticket is in IN_PROGRESS status")
        void shouldRejectReopenWhenTicketIsInProgress() {
            Ticket ticket = createTicketWithStatus(TicketStatus.IN_PROGRESS);
            ReopenTicketRequest request = new ReopenTicketRequest("Some reason");

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

            assertThatThrownBy(() -> ticketService.reopenTicket(ticketId, request, userId))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("CLOSED or RESOLVED");

            verify(ticketRepository, never()).save(any());
        }

        @Test
        @DisplayName("should reject reopen when ticket is in CANCELLED status")
        void shouldRejectReopenWhenTicketIsCancelled() {
            Ticket ticket = createTicketWithStatus(TicketStatus.CANCELLED);
            ReopenTicketRequest request = new ReopenTicketRequest("Some reason");

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

            assertThatThrownBy(() -> ticketService.reopenTicket(ticketId, request, userId))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("CLOSED or RESOLVED");

            verify(ticketRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Ticket Not Found")
    class TicketNotFound {

        @Test
        @DisplayName("should throw ResourceNotFoundException when ticket does not exist")
        void shouldThrowWhenTicketNotFound() {
            ReopenTicketRequest request = new ReopenTicketRequest("Some reason");

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ticketService.reopenTicket(ticketId, request, userId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(ticketRepository, never()).save(any());
            verify(eventPublisher, never()).publish(any());
        }
    }
}
