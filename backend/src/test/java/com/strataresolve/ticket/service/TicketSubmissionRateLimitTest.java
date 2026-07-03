package com.strataresolve.ticket.service;

import com.strataresolve.property.domain.Property;
import com.strataresolve.property.domain.PropertyStatus;
import com.strataresolve.property.repository.PropertyRepository;
import com.strataresolve.shared.event.DomainEventPublisher;
import com.strataresolve.shared.exception.RateLimitExceededException;
import com.strataresolve.ticket.config.TicketProperties;
import com.strataresolve.ticket.domain.Category;
import com.strataresolve.ticket.domain.Priority;
import com.strataresolve.ticket.domain.Ticket;
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

/**
 * Unit tests for ticket submission rate limiting.
 * Validates Requirements 16.4 and 16.5.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TicketService - Submission Rate Limiting")
class TicketSubmissionRateLimitTest {

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
    private UUID userId;
    private UUID unitId;
    private Property activeProperty;
    private Membership residentMembership;
    private CreateTicketRequest validRequest;

    @BeforeEach
    void setUp() {
        // Configure rate limit: max 5 submissions per 30 minutes for easier testing
        TicketProperties ticketProperties = new TicketProperties(
                72,
                null,
                new TicketProperties.RateLimitProperties(5, 30)
        );
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

        validRequest = new CreateTicketRequest(
                "Leaking pipe",
                "Water is leaking from the ceiling pipe",
                Category.PLUMBING,
                "Unit 5A, Bathroom",
                Priority.HIGH
        );

        // Default: SLA calculator returns targets (lenient because not all tests call submitTicket successfully)
        org.mockito.Mockito.lenient().when(slaCalculator.calculateTargets(any(), any(), any(), any()))
                .thenReturn(new com.strataresolve.sla.service.SlaCalculator.SlaTargets(
                        java.time.Instant.now().plusSeconds(24 * 3600),
                        java.time.Instant.now().plusSeconds(72 * 3600)));
    }

    @Test
    @DisplayName("should allow submission when resident has not reached the rate limit")
    void shouldAllowSubmissionWhenUnderRateLimit() {
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(activeProperty));
        when(ticketRepository.countBySubmittedByAndCreatedAtAfter(any(UUID.class), any(Instant.class)))
                .thenReturn(4L); // 4 submissions, limit is 5
        when(membershipRepository.findActiveByUserIdAndPropertyId(userId, propertyId))
                .thenReturn(List.of(residentMembership));
        when(referenceNumberGenerator.generateReferenceNumber()).thenReturn("SR-2025-000001");
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket t = invocation.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
        when(duplicateDetectionService.checkForDuplicates(any(), any(), any(), any()))
                .thenReturn(DuplicateDetectionResult.noDuplicates());

        Ticket result = ticketService.submitTicket(validRequest, propertyId, userId);

        assertThat(result).isNotNull();
        assertThat(result.getReferenceNumber()).isEqualTo("SR-2025-000001");
        verify(ticketRepository).save(any(Ticket.class));
    }

    @Test
    @DisplayName("should reject submission when resident has reached the rate limit")
    void shouldRejectSubmissionWhenRateLimitReached() {
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(activeProperty));
        when(ticketRepository.countBySubmittedByAndCreatedAtAfter(any(UUID.class), any(Instant.class)))
                .thenReturn(5L); // exactly at the limit

        assertThatThrownBy(() -> ticketService.submitTicket(validRequest, propertyId, userId))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("exceeded the maximum of 5 ticket submissions per 30 minutes");

        // Verify no ticket was saved
        verify(ticketRepository, never()).save(any(Ticket.class));
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("should reject submission when resident has exceeded the rate limit")
    void shouldRejectSubmissionWhenRateLimitExceeded() {
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(activeProperty));
        when(ticketRepository.countBySubmittedByAndCreatedAtAfter(any(UUID.class), any(Instant.class)))
                .thenReturn(10L); // well above the limit

        assertThatThrownBy(() -> ticketService.submitTicket(validRequest, propertyId, userId))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("exceeded the maximum of 5 ticket submissions");

        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    @DisplayName("should include waiting period information in error message")
    void shouldIncludeWaitingPeriodInErrorMessage() {
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(activeProperty));
        when(ticketRepository.countBySubmittedByAndCreatedAtAfter(any(UUID.class), any(Instant.class)))
                .thenReturn(5L);

        assertThatThrownBy(() -> ticketService.submitTicket(validRequest, propertyId, userId))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Please wait approximately")
                .hasMessageContaining("minute(s) before submitting again");
    }

    @Test
    @DisplayName("should allow submission when count is zero")
    void shouldAllowSubmissionWhenNoRecentSubmissions() {
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
        when(duplicateDetectionService.checkForDuplicates(any(), any(), any(), any()))
                .thenReturn(DuplicateDetectionResult.noDuplicates());

        Ticket result = ticketService.submitTicket(validRequest, propertyId, userId);

        assertThat(result).isNotNull();
        verify(ticketRepository).save(any(Ticket.class));
    }

    @Test
    @DisplayName("should check rate limit with correct time window")
    void shouldCheckRateLimitWithCorrectTimeWindow() {
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
        when(duplicateDetectionService.checkForDuplicates(any(), any(), any(), any()))
                .thenReturn(DuplicateDetectionResult.noDuplicates());

        ticketService.submitTicket(validRequest, propertyId, userId);

        // Verify the count query was called with the correct user ID
        verify(ticketRepository).countBySubmittedByAndCreatedAtAfter(
                org.mockito.ArgumentMatchers.eq(userId),
                any(Instant.class)
        );
    }

    @Test
    @DisplayName("should use default rate limit configuration when none specified")
    void shouldUseDefaultRateLimitConfiguration() {
        // Use default configuration (null rate limit should default to 10 per 60 minutes)
        TicketProperties defaultProperties = new TicketProperties(72, null, null);
        TicketService serviceWithDefaults = new TicketService(
                ticketRepository, propertyRepository, membershipRepository,
                statusHistoryRepository, referenceNumberGenerator,
                statusWorkflowEngine, defaultProperties, eventPublisher, duplicateDetectionService,
                slaCalculator);

        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(activeProperty));
        when(ticketRepository.countBySubmittedByAndCreatedAtAfter(any(UUID.class), any(Instant.class)))
                .thenReturn(10L); // at the default limit of 10

        assertThatThrownBy(() -> serviceWithDefaults.submitTicket(validRequest, propertyId, userId))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("maximum of 10 ticket submissions per 60 minutes");
    }

    @Test
    @DisplayName("rate limit should be per-resident, not global")
    void rateLimitShouldBePerResident() {
        UUID anotherUserId = UUID.randomUUID();
        UUID anotherUnitId = UUID.randomUUID();

        Membership anotherMembership = Membership.builder()
                .id(UUID.randomUUID())
                .userId(anotherUserId)
                .unitId(anotherUnitId)
                .role(Role.RESIDENT_OWNER)
                .isActive(true)
                .effectiveFrom(LocalDate.now())
                .build();
        anotherMembership.setPropertyId(propertyId);

        // First user is at the limit
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(activeProperty));
        when(ticketRepository.countBySubmittedByAndCreatedAtAfter(
                org.mockito.ArgumentMatchers.eq(userId), any(Instant.class)))
                .thenReturn(5L);

        // First user should be rejected
        assertThatThrownBy(() -> ticketService.submitTicket(validRequest, propertyId, userId))
                .isInstanceOf(RateLimitExceededException.class);

        // Second user has no submissions
        when(ticketRepository.countBySubmittedByAndCreatedAtAfter(
                org.mockito.ArgumentMatchers.eq(anotherUserId), any(Instant.class)))
                .thenReturn(0L);
        when(membershipRepository.findActiveByUserIdAndPropertyId(anotherUserId, propertyId))
                .thenReturn(List.of(anotherMembership));
        when(referenceNumberGenerator.generateReferenceNumber()).thenReturn("SR-2025-000004");
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket t = invocation.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
        when(duplicateDetectionService.checkForDuplicates(any(), any(), any(), any()))
                .thenReturn(DuplicateDetectionResult.noDuplicates());

        // Second user should be allowed
        Ticket result = ticketService.submitTicket(validRequest, propertyId, anotherUserId);
        assertThat(result).isNotNull();
    }
}
