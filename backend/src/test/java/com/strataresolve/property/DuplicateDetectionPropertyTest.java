package com.strataresolve.property;

import com.strataresolve.ticket.config.TicketProperties;
import com.strataresolve.ticket.domain.Category;
import com.strataresolve.ticket.domain.Priority;
import com.strataresolve.ticket.domain.SlaStatus;
import com.strataresolve.ticket.domain.Ticket;
import com.strataresolve.ticket.domain.TicketStatus;
import com.strataresolve.ticket.dto.DuplicateDetectionResult;
import com.strataresolve.ticket.repository.TicketDuplicateLinkRepository;
import com.strataresolve.ticket.repository.TicketRepository;
import com.strataresolve.ticket.service.DuplicateDetectionService;
import net.jqwik.api.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-based test for Duplicate Detection.
 *
 * <p><b>Property 16: Duplicate Detection</b></p>
 * <p>For any two tickets submitted within a configurable recent time window for the same property
 * with similar title, category, and location, the system SHALL flag the later submission as a
 * potential duplicate for manager review without blocking its creation.</p>
 *
 * <p><b>Validates: Requirements 16.1, 16.2</b></p>
 */
@Tag("Feature: strataresolve-platform")
@Tag("Property 16: Duplicate Detection")
class DuplicateDetectionPropertyTest {

    private static final double SIMILARITY_THRESHOLD = 0.6;
    private static final int TIME_WINDOW_HOURS = 48;

    // =====================================================================
    // Property: Tickets with similar title, same category, and matching
    // location within the time window SHALL be detected as duplicates
    // =====================================================================

    /**
     * For any two tickets with identical titles, the same category, and matching location
     * within the same property and time window, the system SHALL flag the later submission
     * as a potential duplicate.
     *
     * <p><b>Validates: Requirements 16.1, 16.2</b></p>
     */
    @Property(tries = 100)
    void identicalTicketsShouldBeDetectedAsDuplicates(
            @ForAll("categories") Category category,
            @ForAll("ticketTitles") String title,
            @ForAll("locations") String location
    ) {
        // Arrange
        UUID propertyId = UUID.randomUUID();

        TicketRepository ticketRepository = mock(TicketRepository.class);
        TicketDuplicateLinkRepository duplicateLinkRepository = mock(TicketDuplicateLinkRepository.class);
        TicketProperties properties = createProperties(TIME_WINDOW_HOURS, SIMILARITY_THRESHOLD);

        DuplicateDetectionService service = new DuplicateDetectionService(
                ticketRepository, duplicateLinkRepository, properties);

        // Existing ticket with same title, category, and location (within time window)
        Ticket existingTicket = createTicket(title, category, location, propertyId);

        when(ticketRepository.findPotentialDuplicateCandidates(eq(propertyId), eq(category), any()))
                .thenReturn(List.of(existingTicket));

        // Act - submit a new ticket with the same title, category, and location
        DuplicateDetectionResult result = service.checkForDuplicates(
                propertyId, title, category, location);

        // Assert - identical tickets should always be flagged as duplicates
        assertThat(result.flaggedAsDuplicate()).isTrue();
        assertThat(result.potentialDuplicates()).isNotEmpty();
        assertThat(result.potentialDuplicates().get(0).similarityScore()).isEqualTo(1.0);
    }

    /**
     * For any ticket with a title that has been slightly modified (high similarity above threshold),
     * same category, and matching location, the system SHALL detect it as a potential duplicate.
     *
     * <p><b>Validates: Requirements 16.1, 16.2</b></p>
     */
    @Property(tries = 100)
    void similarTitlesShouldBeDetectedAsDuplicates(
            @ForAll("categories") Category category,
            @ForAll("baseTitles") String baseTitle,
            @ForAll("smallSuffixes") String suffix,
            @ForAll("locations") String location
    ) {
        // Arrange
        UUID propertyId = UUID.randomUUID();

        TicketRepository ticketRepository = mock(TicketRepository.class);
        TicketDuplicateLinkRepository duplicateLinkRepository = mock(TicketDuplicateLinkRepository.class);
        TicketProperties properties = createProperties(TIME_WINDOW_HOURS, SIMILARITY_THRESHOLD);

        DuplicateDetectionService service = new DuplicateDetectionService(
                ticketRepository, duplicateLinkRepository, properties);

        // Existing ticket with the base title
        Ticket existingTicket = createTicket(baseTitle, category, location, propertyId);

        when(ticketRepository.findPotentialDuplicateCandidates(eq(propertyId), eq(category), any()))
                .thenReturn(List.of(existingTicket));

        // Construct a slightly modified title by appending a small suffix
        // Since base titles are long (30+ chars) and suffixes are short (3-5 chars),
        // the normalized Levenshtein similarity will remain well above the 0.6 threshold
        String modifiedTitle = baseTitle + " " + suffix;

        // Act
        DuplicateDetectionResult result = service.checkForDuplicates(
                propertyId, modifiedTitle, category, location);

        // Assert - similar titles (long base + short suffix) should be detected
        assertThat(result.flaggedAsDuplicate()).isTrue();
        assertThat(result.potentialDuplicates()).isNotEmpty();
        assertThat(result.potentialDuplicates().get(0).similarityScore())
                .isGreaterThanOrEqualTo(SIMILARITY_THRESHOLD);
    }

    // =====================================================================
    // Property: Duplicate detection SHALL NOT block ticket creation
    // =====================================================================

    /**
     * For any duplicate detection result (whether duplicates are found or not),
     * the checkForDuplicates method SHALL return a result object without throwing
     * an exception, meaning submission is never blocked by duplicate detection.
     * The flagAsDuplicate method simply marks the ticket — it does not prevent persistence.
     *
     * <p><b>Validates: Requirements 16.2</b></p>
     */
    @Property(tries = 100)
    void duplicateDetectionShallNotBlockTicketCreation(
            @ForAll("categories") Category category,
            @ForAll("ticketTitles") String title,
            @ForAll("locations") String location
    ) {
        // Arrange
        UUID propertyId = UUID.randomUUID();

        TicketRepository ticketRepository = mock(TicketRepository.class);
        TicketDuplicateLinkRepository duplicateLinkRepository = mock(TicketDuplicateLinkRepository.class);
        TicketProperties properties = createProperties(TIME_WINDOW_HOURS, SIMILARITY_THRESHOLD);

        DuplicateDetectionService service = new DuplicateDetectionService(
                ticketRepository, duplicateLinkRepository, properties);

        // Existing ticket with identical title (strongest possible duplicate)
        Ticket existingTicket = createTicket(title, category, location, propertyId);

        when(ticketRepository.findPotentialDuplicateCandidates(eq(propertyId), eq(category), any()))
                .thenReturn(List.of(existingTicket));

        // Act - duplicate detection should NEVER throw an exception
        DuplicateDetectionResult result = service.checkForDuplicates(
                propertyId, title, category, location);

        // Assert - result is returned (not blocked), and the ticket can still be persisted
        assertThat(result).isNotNull();
        // Even when flagged, the result is informational only — no exception thrown
        // The ticket would be saved, then flagged — both operations succeed

        // Verify: flagging a ticket simply sets the flag and saves, no blocking
        Ticket newTicket = createTicket(title, category, location, propertyId);
        when(ticketRepository.save(any(Ticket.class))).thenReturn(newTicket);

        service.flagAsDuplicate(newTicket);

        // The ticket was saved successfully (not rejected)
        verify(ticketRepository).save(newTicket);
        assertThat(newTicket.isDuplicateFlag()).isTrue();
    }

    // =====================================================================
    // Property: Flagged tickets SHALL be marked for Property Manager review
    // =====================================================================

    /**
     * For any ticket that is detected as a potential duplicate, the system SHALL
     * set the duplicateFlag to true, making it visible to Property Managers
     * for review via the getFlaggedDuplicates query.
     *
     * <p><b>Validates: Requirements 16.2</b></p>
     */
    @Property(tries = 100)
    void detectedDuplicatesShallBeFlaggedForManagerReview(
            @ForAll("categories") Category category,
            @ForAll("ticketTitles") String title,
            @ForAll("locations") String location
    ) {
        // Arrange
        UUID propertyId = UUID.randomUUID();

        TicketRepository ticketRepository = mock(TicketRepository.class);
        TicketDuplicateLinkRepository duplicateLinkRepository = mock(TicketDuplicateLinkRepository.class);
        TicketProperties properties = createProperties(TIME_WINDOW_HOURS, SIMILARITY_THRESHOLD);

        DuplicateDetectionService service = new DuplicateDetectionService(
                ticketRepository, duplicateLinkRepository, properties);

        // Existing ticket (same title = guaranteed duplicate)
        Ticket existingTicket = createTicket(title, category, location, propertyId);

        when(ticketRepository.findPotentialDuplicateCandidates(eq(propertyId), eq(category), any()))
                .thenReturn(List.of(existingTicket));

        // Act - Check for duplicates
        DuplicateDetectionResult result = service.checkForDuplicates(
                propertyId, title, category, location);

        // When duplicates are detected, flagAsDuplicate should be called on the new ticket
        assertThat(result.flaggedAsDuplicate()).isTrue();

        // Simulate the submission flow: flag the new ticket
        Ticket newTicket = createTicket(title, category, location, propertyId);
        when(ticketRepository.save(any(Ticket.class))).thenReturn(newTicket);

        service.flagAsDuplicate(newTicket);

        // Assert - the ticket is flagged for PM review
        assertThat(newTicket.isDuplicateFlag()).isTrue();
        verify(ticketRepository).save(newTicket);
    }

    /**
     * For any tickets that are NOT similar (completely different titles), the system
     * SHALL NOT flag them as duplicates.
     *
     * <p><b>Validates: Requirements 16.1</b></p>
     */
    @Property(tries = 100)
    void dissimilarTicketsShouldNotBeFlaggedAsDuplicates(
            @ForAll("categories") Category category,
            @ForAll("dissimilarTitlePairs") List<String> titlePair,
            @ForAll("locations") String location
    ) {
        // Arrange
        UUID propertyId = UUID.randomUUID();
        String existingTitle = titlePair.get(0);
        String newTitle = titlePair.get(1);

        TicketRepository ticketRepository = mock(TicketRepository.class);
        TicketDuplicateLinkRepository duplicateLinkRepository = mock(TicketDuplicateLinkRepository.class);
        TicketProperties properties = createProperties(TIME_WINDOW_HOURS, SIMILARITY_THRESHOLD);

        DuplicateDetectionService service = new DuplicateDetectionService(
                ticketRepository, duplicateLinkRepository, properties);

        Ticket existingTicket = createTicket(existingTitle, category, location, propertyId);

        when(ticketRepository.findPotentialDuplicateCandidates(eq(propertyId), eq(category), any()))
                .thenReturn(List.of(existingTicket));

        // Act
        DuplicateDetectionResult result = service.checkForDuplicates(
                propertyId, newTitle, category, location);

        // Assert - should NOT be flagged since titles are very different
        assertThat(result.flaggedAsDuplicate()).isFalse();
        assertThat(result.potentialDuplicates()).isEmpty();
    }

    // =====================================================================
    // Arbitraries (Generators)
    // =====================================================================

    @Provide
    Arbitrary<Category> categories() {
        return Arbitraries.of(Category.values());
    }

    @Provide
    Arbitrary<String> ticketTitles() {
        // Generate realistic ticket titles with at least 10 characters
        return Arbitraries.of(
                "Leaking pipe in kitchen",
                "Broken window in living room",
                "Electrical fault in basement",
                "Lift not working on floor 5",
                "Water damage on ceiling",
                "Air conditioning unit failure",
                "Parking barrier is jammed",
                "Security camera not recording",
                "Drainage blocked in ground floor",
                "Common area light flickering",
                "Fire alarm beeping constantly",
                "Door lock mechanism broken",
                "Roof leak during heavy rain",
                "Gas smell near utility room",
                "Pest control needed urgently"
        );
    }

    @Provide
    Arbitrary<String> baseTitles() {
        // Longer base titles where appending a short suffix still keeps similarity above threshold
        return Arbitraries.of(
                "Leaking pipe in the kitchen sink area",
                "Broken window in the main living room",
                "Electrical fault in the underground basement",
                "Lift not working properly on floor five",
                "Water damage spreading on the ceiling",
                "Air conditioning unit complete failure",
                "Parking barrier mechanism is jammed again",
                "Security camera stopped recording footage",
                "Drainage system blocked in ground floor corridor"
        );
    }

    @Provide
    Arbitrary<String> smallSuffixes() {
        // Very short suffixes that won't drop similarity below threshold on long titles
        return Arbitraries.of("again", "now", "too", "also", "here");
    }

    @Provide
    Arbitrary<String> locations() {
        return Arbitraries.of(
                "Floor 1 Lobby",
                "Floor 3 Corridor",
                "Block A Unit 5",
                "Basement Parking",
                "Rooftop Terrace",
                "Ground Floor Reception",
                "Floor 7 Common Area",
                "Block B Stairwell",
                null
        );
    }

    @Provide
    Arbitrary<List<String>> dissimilarTitlePairs() {
        // Pairs of titles that should be very different from each other
        return Arbitraries.of(
                List.of("Leaking pipe in kitchen", "Security camera not recording"),
                List.of("Broken window in living room", "Parking barrier is jammed"),
                List.of("Electrical fault in basement", "Roof leak during rain"),
                List.of("Lift not working", "Pest control needed urgently"),
                List.of("Water damage on ceiling", "Door lock mechanism broken"),
                List.of("Air conditioning failure", "Drainage blocked in lobby"),
                List.of("Fire alarm beeping", "Gas smell near utility room"),
                List.of("Common area light flickering", "Broken elevator button"),
                List.of("Intercom system malfunction", "Plumbing overflow in bathroom"),
                List.of("Playground equipment damaged", "Network router not responding")
        );
    }

    // =====================================================================
    // Helper Methods
    // =====================================================================

    private TicketProperties createProperties(int timeWindowHours, double similarityThreshold) {
        TicketProperties.DuplicateDetection duplicateConfig =
                new TicketProperties.DuplicateDetection(timeWindowHours, similarityThreshold);
        return new TicketProperties(72, duplicateConfig, null);
    }

    private Ticket createTicket(String title, Category category, String location, UUID propertyId) {
        Ticket ticket = Ticket.builder()
                .title(title)
                .category(category)
                .location(location)
                .description("Test description")
                .priority(Priority.NORMAL)
                .status(TicketStatus.SUBMITTED)
                .slaStatus(SlaStatus.ON_TRACK)
                .submittedBy(UUID.randomUUID())
                .unitId(UUID.randomUUID())
                .referenceNumber("SR-2025-" + String.format("%06d", (int) (Math.random() * 999999)))
                .createdAt(Instant.now())
                .build();
        ticket.setPropertyId(propertyId);
        ticket.setId(UUID.randomUUID());
        return ticket;
    }
}
