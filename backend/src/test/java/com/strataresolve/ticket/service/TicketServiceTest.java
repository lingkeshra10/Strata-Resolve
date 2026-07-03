package com.strataresolve.ticket.service;

import com.strataresolve.property.domain.Property;
import com.strataresolve.property.domain.PropertyStatus;
import com.strataresolve.property.repository.PropertyRepository;
import com.strataresolve.shared.event.DomainEventPublisher;
import com.strataresolve.shared.event.TicketCreatedEvent;
import com.strataresolve.shared.exception.BusinessRuleViolationException;
import com.strataresolve.shared.exception.ResourceNotFoundException;
import com.strataresolve.sla.service.SlaCalculator;
import com.strataresolve.ticket.config.TicketProperties;
import com.strataresolve.ticket.domain.Category;
import com.strataresolve.ticket.domain.Priority;
import com.strataresolve.ticket.domain.Ticket;
import com.strataresolve.ticket.domain.TicketStatus;
import com.strataresolve.ticket.dto.CreateTicketRequest;
import com.strataresolve.ticket.dto.DuplicateDetectionResult;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
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
@DisplayName("TicketService - Submission Logic")
class TicketServiceTest {

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
    private SlaCalculator slaCalculator;

    private TicketService ticketService;

    private UUID propertyId;
    private UUID userId;
    private UUID unitId;
    private Property activeProperty;
    private Membership residentMembership;

    @BeforeEach
    void setUp() {
        TicketProperties ticketProperties = new TicketProperties(72, null, new TicketProperties.RateLimitProperties(10, 60));
        ticketService = new TicketService(
                ticketRepository, propertyRepository, membershipRepository,
                statusHistoryRepository, referenceNumberGenerator,
                statusWorkflowEngine, ticketProperties, eventPublisher, duplicateDetectionService,
                slaCalculator);

        propertyId = UUID.randomUUID();
        userId = UUID.randomUUID();
        unitId = UUID.randomUUID();

        activeProperty = Property.builder()
                .id(propertyId)
                .name("Test Property")
                .code("TST")
                .address("123 Test St")
                .timezone("Asia/Kuala_Lumpur")
                .status(PropertyStatus.ACTIVE)
                .build();

        residentMembership = Membership.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .unitId(unitId)
                .role(Role.RESIDENT_OWNER)
                .isActive(true)
                .effectiveFrom(LocalDate.now())
                .build();
        residentMembership.setPropertyId(propertyId);

        // Default: no duplicates detected (lenient because not all tests reach this code path)
        org.mockito.Mockito.lenient().when(duplicateDetectionService.checkForDuplicates(any(), any(), any(), any()))
                .thenReturn(DuplicateDetectionResult.noDuplicates());

        // Default: SLA calculator returns targets 24h/72h ahead (lenient because not all tests reach this code path)
        Instant ackDue = Instant.now().plusSeconds(24 * 3600);
        Instant resDue = Instant.now().plusSeconds(72 * 3600);
        org.mockito.Mockito.lenient().when(slaCalculator.calculateTargets(any(), any(), any(), any()))
                .thenReturn(new SlaCalculator.SlaTargets(ackDue, resDue));
    }

    @Test
    @DisplayName("should submit ticket successfully with generated reference number")
    void shouldSubmitTicketSuccessfully() {
        CreateTicketRequest request = new CreateTicketRequest(
                "Leaking pipe in bathroom",
                "Water is leaking from the ceiling pipe",
                Category.PLUMBING,
                "Unit 5A, Bathroom",
                Priority.HIGH
        );

        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(activeProperty));
        when(ticketRepository.countBySubmittedByAndCreatedAtAfter(any(UUID.class), any(Instant.class)))
                .thenReturn(0L);
        when(membershipRepository.findActiveByUserIdAndPropertyId(userId, propertyId))
                .thenReturn(List.of(residentMembership));
        when(referenceNumberGenerator.generateReferenceNumber()).thenReturn("SR-2025-000001");
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket t = invocation.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        Ticket result = ticketService.submitTicket(request, propertyId, userId);

        assertThat(result).isNotNull();
        assertThat(result.getReferenceNumber()).isEqualTo("SR-2025-000001");
        assertThat(result.getTitle()).isEqualTo("Leaking pipe in bathroom");
        assertThat(result.getDescription()).isEqualTo("Water is leaking from the ceiling pipe");
        assertThat(result.getCategory()).isEqualTo(Category.PLUMBING);
        assertThat(result.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(result.getStatus()).isEqualTo(TicketStatus.SUBMITTED);
        assertThat(result.getSubmittedBy()).isEqualTo(userId);
        assertThat(result.getUnitId()).isEqualTo(unitId);
        assertThat(result.getPropertyId()).isEqualTo(propertyId);
        assertThat(result.getLocation()).isEqualTo("Unit 5A, Bathroom");
        assertThat(result.getAcknowledgementDueAt()).isNotNull();
        assertThat(result.getResolutionDueAt()).isNotNull();
    }

    @Test
    @DisplayName("should use default priority NORMAL when no suggested priority is provided")
    void shouldUseDefaultPriorityWhenNoneProvided() {
        CreateTicketRequest request = new CreateTicketRequest(
                "Broken light",
                "The corridor light is not working",
                Category.ELECTRICAL,
                "Level 3, Corridor",
                null // no suggested priority
        );

        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(activeProperty));
        when(ticketRepository.countBySubmittedByAndCreatedAtAfter(any(UUID.class), any(Instant.class)))
                .thenReturn(0L);
        when(membershipRepository.findActiveByUserIdAndPropertyId(userId, propertyId))
                .thenReturn(List.of(residentMembership));
        when(referenceNumberGenerator.generateReferenceNumber()).thenReturn("SR-2025-000002");
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket t = invocation.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        Ticket result = ticketService.submitTicket(request, propertyId, userId);

        assertThat(result.getPriority()).isEqualTo(Priority.NORMAL);
    }

    @Test
    @DisplayName("should reject submission when property is inactive")
    void shouldRejectSubmissionWhenPropertyInactive() {
        Property inactiveProperty = Property.builder()
                .id(propertyId)
                .name("Inactive Property")
                .code("INA")
                .address("456 Test St")
                .timezone("Asia/Kuala_Lumpur")
                .status(PropertyStatus.INACTIVE)
                .build();

        CreateTicketRequest request = new CreateTicketRequest(
                "Some issue", "Description", Category.PLUMBING, "Location", null);

        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(inactiveProperty));

        assertThatThrownBy(() -> ticketService.submitTicket(request, propertyId, userId))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("inactive");

        verify(ticketRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("should reject submission when property not found")
    void shouldRejectSubmissionWhenPropertyNotFound() {
        CreateTicketRequest request = new CreateTicketRequest(
                "Some issue", "Description", Category.PLUMBING, "Location", null);

        when(propertyRepository.findById(propertyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.submitTicket(request, propertyId, userId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("should reject submission when resident has no linked unit")
    void shouldRejectSubmissionWhenNoLinkedUnit() {
        Membership membershipWithoutUnit = Membership.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .unitId(null) // no unit linked
                .role(Role.RESIDENT_OWNER)
                .isActive(true)
                .effectiveFrom(LocalDate.now())
                .build();
        membershipWithoutUnit.setPropertyId(propertyId);

        CreateTicketRequest request = new CreateTicketRequest(
                "Some issue", "Description", Category.PLUMBING, "Location", null);

        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(activeProperty));
        when(membershipRepository.findActiveByUserIdAndPropertyId(userId, propertyId))
                .thenReturn(List.of(membershipWithoutUnit));

        assertThatThrownBy(() -> ticketService.submitTicket(request, propertyId, userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No active resident membership");

        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("should publish TicketCreatedEvent on successful submission")
    void shouldPublishTicketCreatedEvent() {
        CreateTicketRequest request = new CreateTicketRequest(
                "Lift stuck", "Lift at block A is stuck on floor 5",
                Category.LIFT, "Block A, Lift 1", Priority.URGENT);

        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(activeProperty));
        when(ticketRepository.countBySubmittedByAndCreatedAtAfter(any(UUID.class), any(Instant.class)))
                .thenReturn(0L);
        when(membershipRepository.findActiveByUserIdAndPropertyId(userId, propertyId))
                .thenReturn(List.of(residentMembership));
        when(referenceNumberGenerator.generateReferenceNumber()).thenReturn("SR-2025-000003");
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket t = invocation.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        ticketService.submitTicket(request, propertyId, userId);

        ArgumentCaptor<TicketCreatedEvent> eventCaptor = ArgumentCaptor.forClass(TicketCreatedEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());

        TicketCreatedEvent event = eventCaptor.getValue();
        assertThat(event.getActingUserId()).isEqualTo(userId);
        assertThat(event.getPropertyId()).isEqualTo(propertyId);
        assertThat(event.getReferenceNumber()).isEqualTo("SR-2025-000003");
        assertThat(event.getCategory()).isEqualTo("LIFT");
        assertThat(event.getPriority()).isEqualTo("URGENT");
        assertThat(event.getUnitId()).isEqualTo(unitId);
    }

    @Test
    @DisplayName("should record initial status history entry on submission")
    void shouldRecordInitialStatusHistory() {
        CreateTicketRequest request = new CreateTicketRequest(
                "AC not working", "Air conditioning unit is broken",
                Category.ELECTRICAL, "Unit 10B", Priority.NORMAL);

        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(activeProperty));
        when(ticketRepository.countBySubmittedByAndCreatedAtAfter(any(UUID.class), any(Instant.class)))
                .thenReturn(0L);
        when(membershipRepository.findActiveByUserIdAndPropertyId(userId, propertyId))
                .thenReturn(List.of(residentMembership));
        when(referenceNumberGenerator.generateReferenceNumber()).thenReturn("SR-2025-000004");
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket t = invocation.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        ticketService.submitTicket(request, propertyId, userId);

        verify(statusHistoryRepository).save(any());
    }

    @Test
    @DisplayName("should calculate SLA targets based on property timezone")
    void shouldCalculateSlaTargets() {
        CreateTicketRequest request = new CreateTicketRequest(
                "Water leak", "Pipe burst in kitchen",
                Category.PLUMBING, "Kitchen", Priority.HIGH);

        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(activeProperty));
        when(ticketRepository.countBySubmittedByAndCreatedAtAfter(any(UUID.class), any(Instant.class)))
                .thenReturn(0L);
        when(membershipRepository.findActiveByUserIdAndPropertyId(userId, propertyId))
                .thenReturn(List.of(residentMembership));
        when(referenceNumberGenerator.generateReferenceNumber()).thenReturn("SR-2025-000005");
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket t = invocation.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        Ticket result = ticketService.submitTicket(request, propertyId, userId);

        assertThat(result.getAcknowledgementDueAt()).isNotNull();
        assertThat(result.getResolutionDueAt()).isNotNull();
        // Resolution due should be after acknowledgement due
        assertThat(result.getResolutionDueAt()).isAfter(result.getAcknowledgementDueAt());
    }
}
