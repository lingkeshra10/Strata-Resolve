package com.strataresolve.property;

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
import com.strataresolve.ticket.service.DuplicateDetectionService;
import com.strataresolve.ticket.service.ReferenceNumberGenerator;
import com.strataresolve.ticket.service.TicketService;
import com.strataresolve.user.domain.Membership;
import com.strataresolve.user.domain.Role;
import com.strataresolve.user.repository.MembershipRepository;
import net.jqwik.api.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based test for Submission Rate Limiting.
 *
 * <p><b>Property 15: Submission Rate Limiting</b></p>
 * <p>For any resident who has reached the configured maximum submissions per time period,
 * subsequent ticket submissions within that period SHALL be rejected with an informative
 * error message indicating the waiting period.</p>
 *
 * <p><b>Validates: Requirements 16.4, 16.5</b></p>
 */
@Tag("Feature: strataresolve-platform")
@Tag("Property 15: Submission Rate Limiting")
class SubmissionRateLimitingPropertyTest {

    // =====================================================================
    // Property: Submissions at or above the rate limit SHALL be rejected
    // =====================================================================

    /**
     * For any resident who has reached or exceeded the configured maximum submissions
     * per time period, a subsequent ticket submission SHALL be rejected with a
     * RateLimitExceededException.
     *
     * <p><b>Validates: Requirements 16.4, 16.5</b></p>
     */
    @Property(tries = 100)
    void submissionsShouldBeRejectedWhenAtOrAboveRateLimit(
            @ForAll("rateLimitConfigurations") RateLimitConfig config,
            @ForAll("submissionCountsAtOrAboveLimit") long submissionCountOffset
    ) {
        // Arrange
        UUID propertyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();

        long recentSubmissions = config.maxSubmissions() + submissionCountOffset;

        TicketService ticketService = buildTicketService(config, propertyId, userId, unitId, recentSubmissions);

        CreateTicketRequest request = new CreateTicketRequest(
                "Test ticket title",
                "Test ticket description",
                Category.PLUMBING,
                "Unit 1A, Kitchen",
                Priority.NORMAL
        );

        // Act & Assert: submission should be rejected
        assertThatThrownBy(() -> ticketService.submitTicket(request, propertyId, userId))
                .isInstanceOf(RateLimitExceededException.class);
    }

    // =====================================================================
    // Property: Submissions below the rate limit SHALL be allowed
    // =====================================================================

    /**
     * For any resident who has NOT reached the configured maximum submissions
     * per time period, ticket submission SHALL succeed.
     *
     * <p><b>Validates: Requirements 16.4, 16.5</b></p>
     */
    @Property(tries = 100)
    void submissionsShouldBeAllowedWhenBelowRateLimit(
            @ForAll("rateLimitConfigurations") RateLimitConfig config,
            @ForAll("submissionCountsBelowLimit") long submissionCountBelowLimit
    ) {
        // Arrange
        UUID propertyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();

        // Ensure count is below the max for this configuration
        long recentSubmissions = Math.min(submissionCountBelowLimit, config.maxSubmissions() - 1);

        TicketRepository ticketRepository = mock(TicketRepository.class);
        PropertyRepository propertyRepository = mock(PropertyRepository.class);
        MembershipRepository membershipRepository = mock(MembershipRepository.class);
        StatusHistoryRepository statusHistoryRepository = mock(StatusHistoryRepository.class);
        ReferenceNumberGenerator referenceNumberGenerator = mock(ReferenceNumberGenerator.class);
        StatusWorkflowEngine statusWorkflowEngine = mock(StatusWorkflowEngine.class);
        DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
        DuplicateDetectionService duplicateDetectionService = mock(DuplicateDetectionService.class);
        com.strataresolve.sla.service.SlaCalculator slaCalculator = mock(com.strataresolve.sla.service.SlaCalculator.class);

        TicketProperties ticketProperties = new TicketProperties(
                72, null,
                new TicketProperties.RateLimitProperties(config.maxSubmissions(), config.periodMinutes())
        );

        TicketService ticketService = new TicketService(
                ticketRepository, propertyRepository, membershipRepository,
                statusHistoryRepository, referenceNumberGenerator,
                statusWorkflowEngine, ticketProperties, eventPublisher, duplicateDetectionService,
                slaCalculator
        );

        com.strataresolve.property.domain.Property property = com.strataresolve.property.domain.Property.builder()
                .id(propertyId)
                .name("Test Property")
                .code("TST")
                .address("123 Test St")
                .timezone("Asia/Kuala_Lumpur")
                .status(PropertyStatus.ACTIVE)
                .build();

        Membership membership = Membership.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .unitId(unitId)
                .role(Role.RESIDENT_OWNER)
                .isActive(true)
                .effectiveFrom(LocalDate.now())
                .build();
        membership.setPropertyId(propertyId);

        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
        when(ticketRepository.countBySubmittedByAndCreatedAtAfter(eq(userId), any(Instant.class)))
                .thenReturn(recentSubmissions);
        when(membershipRepository.findActiveByUserIdAndPropertyId(userId, propertyId))
                .thenReturn(List.of(membership));
        when(referenceNumberGenerator.generateReferenceNumber()).thenReturn("SR-2025-000001");
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket t = invocation.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
        when(duplicateDetectionService.checkForDuplicates(any(), any(), any(), any()))
                .thenReturn(DuplicateDetectionResult.noDuplicates());
        when(slaCalculator.calculateTargets(any(), any(), any(), any()))
                .thenReturn(new com.strataresolve.sla.service.SlaCalculator.SlaTargets(
                        Instant.now().plusSeconds(24 * 3600), Instant.now().plusSeconds(72 * 3600)));

        CreateTicketRequest request = new CreateTicketRequest(
                "Test ticket title",
                "Test ticket description",
                Category.PLUMBING,
                "Unit 1A, Kitchen",
                Priority.NORMAL
        );

        // Act
        Ticket result = ticketService.submitTicket(request, propertyId, userId);

        // Assert: submission should succeed
        assertThat(result).isNotNull();
        verify(ticketRepository).save(any(Ticket.class));
    }

    // =====================================================================
    // Property: Error message SHALL include waiting period information
    // =====================================================================

    /**
     * For any resident who exceeds the rate limit, the error message SHALL contain
     * information about the maximum submissions, the time period, and the waiting period.
     *
     * <p><b>Validates: Requirements 16.4, 16.5</b></p>
     */
    @Property(tries = 100)
    void errorMessageShouldIncludeWaitingPeriodInformation(
            @ForAll("rateLimitConfigurations") RateLimitConfig config
    ) {
        // Arrange
        UUID propertyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();

        // At exact limit
        long recentSubmissions = config.maxSubmissions();

        TicketService ticketService = buildTicketService(config, propertyId, userId, unitId, recentSubmissions);

        CreateTicketRequest request = new CreateTicketRequest(
                "Test ticket title",
                "Test ticket description",
                Category.ELECTRICAL,
                "Unit 2B, Living Room",
                Priority.HIGH
        );

        // Act & Assert: error message should contain informative details
        assertThatThrownBy(() -> ticketService.submitTicket(request, propertyId, userId))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(throwable -> {
                    String message = throwable.getMessage();
                    // Must mention the max submissions limit
                    assertThat(message).contains(String.valueOf(config.maxSubmissions()));
                    // Must mention the time period
                    assertThat(message).contains(String.valueOf(config.periodMinutes()));
                    // Must mention waiting period (minutes before submitting again)
                    assertThat(message).containsIgnoringCase("wait");
                    assertThat(message).containsIgnoringCase("minute");
                });
    }

    // =====================================================================
    // Helper methods
    // =====================================================================

    /**
     * Builds a TicketService configured for rate limit testing with the given submission count.
     */
    private TicketService buildTicketService(RateLimitConfig config, UUID propertyId, UUID userId,
                                             UUID unitId, long recentSubmissions) {
        TicketRepository ticketRepository = mock(TicketRepository.class);
        PropertyRepository propertyRepository = mock(PropertyRepository.class);
        MembershipRepository membershipRepository = mock(MembershipRepository.class);
        StatusHistoryRepository statusHistoryRepository = mock(StatusHistoryRepository.class);
        ReferenceNumberGenerator referenceNumberGenerator = mock(ReferenceNumberGenerator.class);
        StatusWorkflowEngine statusWorkflowEngine = mock(StatusWorkflowEngine.class);
        DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
        DuplicateDetectionService duplicateDetectionService = mock(DuplicateDetectionService.class);
        com.strataresolve.sla.service.SlaCalculator slaCalculator = mock(com.strataresolve.sla.service.SlaCalculator.class);

        TicketProperties ticketProperties = new TicketProperties(
                72, null,
                new TicketProperties.RateLimitProperties(config.maxSubmissions(), config.periodMinutes())
        );

        TicketService ticketService = new TicketService(
                ticketRepository, propertyRepository, membershipRepository,
                statusHistoryRepository, referenceNumberGenerator,
                statusWorkflowEngine, ticketProperties, eventPublisher, duplicateDetectionService,
                slaCalculator
        );

        com.strataresolve.property.domain.Property property = com.strataresolve.property.domain.Property.builder()
                .id(propertyId)
                .name("Test Property")
                .code("TST")
                .address("123 Test St")
                .timezone("Asia/Kuala_Lumpur")
                .status(PropertyStatus.ACTIVE)
                .build();

        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
        when(ticketRepository.countBySubmittedByAndCreatedAtAfter(eq(userId), any(Instant.class)))
                .thenReturn(recentSubmissions);

        return ticketService;
    }

    // =====================================================================
    // Arbitraries (Generators)
    // =====================================================================

    /**
     * Generates random rate limit configurations with reasonable bounds.
     * maxSubmissions: 1 to 50, periodMinutes: 1 to 1440 (24 hours).
     */
    @Provide
    Arbitrary<RateLimitConfig> rateLimitConfigurations() {
        return Combinators.combine(
                Arbitraries.integers().between(1, 50),
                Arbitraries.integers().between(1, 1440)
        ).as(RateLimitConfig::new);
    }

    /**
     * Generates submission count offsets at or above zero (used to create counts at or above limit).
     * Range: 0 to 100 (added to the max to get the actual count).
     */
    @Provide
    Arbitrary<Long> submissionCountsAtOrAboveLimit() {
        return Arbitraries.longs().between(0L, 100L);
    }

    /**
     * Generates submission counts below the limit.
     * Range: 0 to a reasonable value (will be clamped to below the actual limit).
     */
    @Provide
    Arbitrary<Long> submissionCountsBelowLimit() {
        return Arbitraries.longs().between(0L, 49L);
    }

    // =====================================================================
    // Supporting record
    // =====================================================================

    /**
     * Represents a rate limit configuration for property-based testing.
     */
    record RateLimitConfig(int maxSubmissions, int periodMinutes) {}
}
