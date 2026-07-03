package com.strataresolve.ticket.service;

import com.strataresolve.property.domain.Property;
import com.strataresolve.property.domain.PropertyStatus;
import com.strataresolve.property.repository.PropertyRepository;
import com.strataresolve.shared.event.DomainEventPublisher;
import com.strataresolve.shared.event.PriorityChangedEvent;
import com.strataresolve.shared.exception.BusinessRuleViolationException;
import com.strataresolve.shared.exception.ResourceNotFoundException;
import com.strataresolve.ticket.config.TicketProperties;
import com.strataresolve.ticket.domain.Category;
import com.strataresolve.ticket.domain.Priority;
import com.strataresolve.ticket.domain.SlaStatus;
import com.strataresolve.ticket.domain.StatusHistory;
import com.strataresolve.ticket.domain.Ticket;
import com.strataresolve.ticket.domain.TicketStatus;
import com.strataresolve.ticket.policy.StatusWorkflowEngine;
import com.strataresolve.ticket.repository.StatusHistoryRepository;
import com.strataresolve.ticket.repository.TicketRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TicketService - Category and Priority Management")
class TicketCategoryPriorityTest {

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
    private UUID ticketId;
    private UUID actingUserId;
    private Property property;
    private Ticket existingTicket;

    @BeforeEach
    void setUp() {
        TicketProperties ticketProperties = new TicketProperties(72, null, null);
        ticketService = new TicketService(
                ticketRepository, propertyRepository, membershipRepository,
                statusHistoryRepository, referenceNumberGenerator,
                statusWorkflowEngine, ticketProperties, eventPublisher, duplicateDetectionService,
                slaCalculator);

        propertyId = UUID.randomUUID();
        ticketId = UUID.randomUUID();
        actingUserId = UUID.randomUUID();

        property = Property.builder()
                .id(propertyId)
                .name("Test Property")
                .code("TST")
                .address("123 Test St")
                .timezone("Asia/Kuala_Lumpur")
                .status(PropertyStatus.ACTIVE)
                .build();

        existingTicket = Ticket.builder()
                .id(ticketId)
                .submittedBy(UUID.randomUUID())
                .unitId(UUID.randomUUID())
                .referenceNumber("SR-2025-000001")
                .title("Test Ticket")
                .description("Test Description")
                .category(Category.PLUMBING)
                .priority(Priority.NORMAL)
                .status(TicketStatus.SUBMITTED)
                .location("Unit 1A")
                .acknowledgementDueAt(Instant.now().plusSeconds(86400))
                .resolutionDueAt(Instant.now().plusSeconds(259200))
                .slaStatus(SlaStatus.ON_TRACK)
                .build();
        existingTicket.setPropertyId(propertyId);

        // Default: SLA calculator returns targets (lenient because not all tests call SLA methods)
        org.mockito.Mockito.lenient().when(slaCalculator.calculateTargets(any(), any(), any(), any()))
                .thenReturn(new com.strataresolve.sla.service.SlaCalculator.SlaTargets(
                        Instant.now().plusSeconds(24 * 3600), Instant.now().plusSeconds(72 * 3600)));
    }

    @Nested
    @DisplayName("changeCategory")
    class ChangeCategoryTests {

        @Test
        @DisplayName("should change category and recalculate SLA targets")
        void shouldChangeCategoryAndRecalculateSla() {
            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(existingTicket));
            when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Ticket result = ticketService.changeCategory(ticketId, Category.ELECTRICAL, actingUserId);

            assertThat(result.getCategory()).isEqualTo(Category.ELECTRICAL);
            assertThat(result.getAcknowledgementDueAt()).isNotNull();
            assertThat(result.getResolutionDueAt()).isNotNull();
            // Resolution due should be after acknowledgement due
            assertThat(result.getResolutionDueAt()).isAfter(result.getAcknowledgementDueAt());
        }

        @Test
        @DisplayName("should record category change in status history")
        void shouldRecordCategoryChangeInHistory() {
            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(existingTicket));
            when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ticketService.changeCategory(ticketId, Category.ELECTRICAL, actingUserId);

            ArgumentCaptor<StatusHistory> historyCaptor = ArgumentCaptor.forClass(StatusHistory.class);
            verify(statusHistoryRepository).save(historyCaptor.capture());

            StatusHistory history = historyCaptor.getValue();
            assertThat(history.getTicketId()).isEqualTo(ticketId);
            assertThat(history.getPreviousStatus()).isEqualTo(TicketStatus.SUBMITTED);
            assertThat(history.getNewStatus()).isEqualTo(TicketStatus.SUBMITTED);
            assertThat(history.getChangedBy()).isEqualTo(actingUserId);
            assertThat(history.getReason()).isEqualTo("Category changed from PLUMBING to ELECTRICAL");
        }

        @Test
        @DisplayName("should reject when new category is the same as current")
        void shouldRejectWhenSameCategory() {
            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(existingTicket));

            assertThatThrownBy(() -> ticketService.changeCategory(ticketId, Category.PLUMBING, actingUserId))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("already PLUMBING");

            verify(ticketRepository, never()).save(any());
            verify(statusHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when ticket not found")
        void shouldThrowWhenTicketNotFound() {
            when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ticketService.changeCategory(ticketId, Category.ELECTRICAL, actingUserId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should not publish PriorityChangedEvent on category change")
        void shouldNotPublishPriorityEventOnCategoryChange() {
            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(existingTicket));
            when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ticketService.changeCategory(ticketId, Category.LIFT, actingUserId);

            verify(eventPublisher, never()).publish(any());
        }
    }

    @Nested
    @DisplayName("changePriority")
    class ChangePriorityTests {

        @Test
        @DisplayName("should change priority and recalculate SLA targets")
        void shouldChangePriorityAndRecalculateSla() {
            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(existingTicket));
            when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Ticket result = ticketService.changePriority(ticketId, Priority.URGENT, actingUserId);

            assertThat(result.getPriority()).isEqualTo(Priority.URGENT);
            assertThat(result.getAcknowledgementDueAt()).isNotNull();
            assertThat(result.getResolutionDueAt()).isNotNull();
            assertThat(result.getResolutionDueAt()).isAfter(result.getAcknowledgementDueAt());
        }

        @Test
        @DisplayName("should record priority change in status history with previous and new values")
        void shouldRecordPriorityChangeInHistory() {
            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(existingTicket));
            when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ticketService.changePriority(ticketId, Priority.HIGH, actingUserId);

            ArgumentCaptor<StatusHistory> historyCaptor = ArgumentCaptor.forClass(StatusHistory.class);
            verify(statusHistoryRepository).save(historyCaptor.capture());

            StatusHistory history = historyCaptor.getValue();
            assertThat(history.getTicketId()).isEqualTo(ticketId);
            assertThat(history.getPreviousStatus()).isEqualTo(TicketStatus.SUBMITTED);
            assertThat(history.getNewStatus()).isEqualTo(TicketStatus.SUBMITTED);
            assertThat(history.getChangedBy()).isEqualTo(actingUserId);
            assertThat(history.getReason()).isEqualTo("Priority changed from NORMAL to HIGH");
        }

        @Test
        @DisplayName("should publish PriorityChangedEvent on priority change")
        void shouldPublishPriorityChangedEvent() {
            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(existingTicket));
            when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ticketService.changePriority(ticketId, Priority.EMERGENCY, actingUserId);

            ArgumentCaptor<PriorityChangedEvent> eventCaptor = ArgumentCaptor.forClass(PriorityChangedEvent.class);
            verify(eventPublisher).publish(eventCaptor.capture());

            PriorityChangedEvent event = eventCaptor.getValue();
            assertThat(event.getActingUserId()).isEqualTo(actingUserId);
            assertThat(event.getPropertyId()).isEqualTo(propertyId);
            assertThat(event.getTicketId()).isEqualTo(ticketId);
            assertThat(event.getPreviousPriority()).isEqualTo("NORMAL");
            assertThat(event.getNewPriority()).isEqualTo("EMERGENCY");
        }

        @Test
        @DisplayName("should reject when new priority is the same as current")
        void shouldRejectWhenSamePriority() {
            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(existingTicket));

            assertThatThrownBy(() -> ticketService.changePriority(ticketId, Priority.NORMAL, actingUserId))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("already NORMAL");

            verify(ticketRepository, never()).save(any());
            verify(statusHistoryRepository, never()).save(any());
            verify(eventPublisher, never()).publish(any());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when ticket not found")
        void shouldThrowWhenTicketNotFound() {
            when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ticketService.changePriority(ticketId, Priority.HIGH, actingUserId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should change from EMERGENCY to LOW priority")
        void shouldChangeFromEmergencyToLow() {
            existingTicket.setPriority(Priority.EMERGENCY);

            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(existingTicket));
            when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Ticket result = ticketService.changePriority(ticketId, Priority.LOW, actingUserId);

            assertThat(result.getPriority()).isEqualTo(Priority.LOW);

            ArgumentCaptor<PriorityChangedEvent> eventCaptor = ArgumentCaptor.forClass(PriorityChangedEvent.class);
            verify(eventPublisher).publish(eventCaptor.capture());

            PriorityChangedEvent event = eventCaptor.getValue();
            assertThat(event.getPreviousPriority()).isEqualTo("EMERGENCY");
            assertThat(event.getNewPriority()).isEqualTo("LOW");
        }
    }
}
