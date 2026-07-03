package com.strataresolve.ticket.service;

import com.strataresolve.shared.exception.BusinessRuleViolationException;
import com.strataresolve.shared.exception.ResourceNotFoundException;
import com.strataresolve.ticket.config.TicketProperties;
import com.strataresolve.ticket.domain.Category;
import com.strataresolve.ticket.domain.Priority;
import com.strataresolve.ticket.domain.SlaStatus;
import com.strataresolve.ticket.domain.Ticket;
import com.strataresolve.ticket.domain.TicketDuplicateLink;
import com.strataresolve.ticket.domain.TicketStatus;
import com.strataresolve.ticket.dto.DuplicateDetectionResult;
import com.strataresolve.ticket.dto.DuplicateLinkResponse;
import com.strataresolve.ticket.repository.TicketDuplicateLinkRepository;
import com.strataresolve.ticket.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
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
@DisplayName("DuplicateDetectionService")
class DuplicateDetectionServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketDuplicateLinkRepository duplicateLinkRepository;

    private DuplicateDetectionService service;

    private static final UUID PROPERTY_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TicketProperties.DuplicateDetection duplicateConfig =
                new TicketProperties.DuplicateDetection(48, 0.6);
        TicketProperties properties = new TicketProperties(72, duplicateConfig, null);
        service = new DuplicateDetectionService(ticketRepository, duplicateLinkRepository, properties);
    }

    @Nested
    @DisplayName("checkForDuplicates")
    class CheckForDuplicates {

        @Test
        @DisplayName("returns no duplicates when no candidates found")
        void noCandidatesFound() {
            when(ticketRepository.findPotentialDuplicateCandidates(eq(PROPERTY_ID), eq(Category.PLUMBING), any()))
                    .thenReturn(List.of());

            DuplicateDetectionResult result = service.checkForDuplicates(
                    PROPERTY_ID, "Leaking pipe in kitchen", Category.PLUMBING, "Unit 5A Kitchen");

            assertThat(result.flaggedAsDuplicate()).isFalse();
            assertThat(result.potentialDuplicates()).isEmpty();
        }

        @Test
        @DisplayName("detects duplicate when title is very similar")
        void detectsDuplicateWithSimilarTitle() {
            Ticket existingTicket = createTicket("Leaking pipe in kitchen", Category.PLUMBING, "Unit 5A Kitchen");

            when(ticketRepository.findPotentialDuplicateCandidates(eq(PROPERTY_ID), eq(Category.PLUMBING), any()))
                    .thenReturn(List.of(existingTicket));

            DuplicateDetectionResult result = service.checkForDuplicates(
                    PROPERTY_ID, "Leaking pipe in the kitchen", Category.PLUMBING, "Unit 5A Kitchen");

            assertThat(result.flaggedAsDuplicate()).isTrue();
            assertThat(result.potentialDuplicates()).hasSize(1);
            assertThat(result.potentialDuplicates().get(0).ticketId()).isEqualTo(existingTicket.getId());
        }

        @Test
        @DisplayName("detects duplicate with identical title")
        void detectsDuplicateWithIdenticalTitle() {
            Ticket existingTicket = createTicket("Water leak in bathroom", Category.PLUMBING, "Floor 3");

            when(ticketRepository.findPotentialDuplicateCandidates(eq(PROPERTY_ID), eq(Category.PLUMBING), any()))
                    .thenReturn(List.of(existingTicket));

            DuplicateDetectionResult result = service.checkForDuplicates(
                    PROPERTY_ID, "Water leak in bathroom", Category.PLUMBING, "Floor 3");

            assertThat(result.flaggedAsDuplicate()).isTrue();
            assertThat(result.potentialDuplicates()).hasSize(1);
            assertThat(result.potentialDuplicates().get(0).similarityScore()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("does not flag when title is sufficiently different")
        void doesNotFlagDifferentTitle() {
            Ticket existingTicket = createTicket("Broken window in living room", Category.PLUMBING, "Unit 5A");

            when(ticketRepository.findPotentialDuplicateCandidates(eq(PROPERTY_ID), eq(Category.PLUMBING), any()))
                    .thenReturn(List.of(existingTicket));

            DuplicateDetectionResult result = service.checkForDuplicates(
                    PROPERTY_ID, "Leaking pipe in kitchen", Category.PLUMBING, "Unit 5A");

            assertThat(result.flaggedAsDuplicate()).isFalse();
            assertThat(result.potentialDuplicates()).isEmpty();
        }

        @Test
        @DisplayName("detects duplicate when location is null on both")
        void detectsDuplicateWithNullLocation() {
            Ticket existingTicket = createTicket("Lift not working", Category.LIFT, null);

            when(ticketRepository.findPotentialDuplicateCandidates(eq(PROPERTY_ID), eq(Category.LIFT), any()))
                    .thenReturn(List.of(existingTicket));

            DuplicateDetectionResult result = service.checkForDuplicates(
                    PROPERTY_ID, "Lift is not working", Category.LIFT, null);

            assertThat(result.flaggedAsDuplicate()).isTrue();
            assertThat(result.potentialDuplicates()).hasSize(1);
        }

        @Test
        @DisplayName("detects multiple potential duplicates")
        void detectsMultipleDuplicates() {
            Ticket ticket1 = createTicket("Leaking pipe in kitchen", Category.PLUMBING, "Block A");
            Ticket ticket2 = createTicket("Leaking pipe in the kitchen area", Category.PLUMBING, "Block A");

            when(ticketRepository.findPotentialDuplicateCandidates(eq(PROPERTY_ID), eq(Category.PLUMBING), any()))
                    .thenReturn(List.of(ticket1, ticket2));

            DuplicateDetectionResult result = service.checkForDuplicates(
                    PROPERTY_ID, "Leaking pipe in kitchen", Category.PLUMBING, "Block A");

            assertThat(result.flaggedAsDuplicate()).isTrue();
            assertThat(result.potentialDuplicates()).hasSizeGreaterThanOrEqualTo(1);
        }
    }

    @Nested
    @DisplayName("calculateTitleSimilarity")
    class CalculateTitleSimilarity {

        @Test
        @DisplayName("returns 1.0 for identical strings")
        void identicalStrings() {
            assertThat(service.calculateTitleSimilarity("hello", "hello")).isEqualTo(1.0);
        }

        @Test
        @DisplayName("returns 1.0 for case-insensitive identical strings")
        void caseInsensitiveMatch() {
            assertThat(service.calculateTitleSimilarity("Hello World", "hello world")).isEqualTo(1.0);
        }

        @Test
        @DisplayName("returns 0.0 for null inputs")
        void nullInputs() {
            assertThat(service.calculateTitleSimilarity(null, "hello")).isEqualTo(0.0);
            assertThat(service.calculateTitleSimilarity("hello", null)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("returns 0.0 for empty inputs")
        void emptyInputs() {
            assertThat(service.calculateTitleSimilarity("", "hello")).isEqualTo(0.0);
            assertThat(service.calculateTitleSimilarity("hello", "")).isEqualTo(0.0);
        }

        @Test
        @DisplayName("returns high similarity for minor differences")
        void minorDifferences() {
            double similarity = service.calculateTitleSimilarity(
                    "Leaking pipe in kitchen", "Leaking pipe in the kitchen");
            assertThat(similarity).isGreaterThan(0.8);
        }

        @Test
        @DisplayName("returns low similarity for very different strings")
        void veryDifferentStrings() {
            double similarity = service.calculateTitleSimilarity(
                    "Broken window", "Electrical fault in basement");
            assertThat(similarity).isLessThan(0.4);
        }
    }

    @Nested
    @DisplayName("isLocationMatch")
    class IsLocationMatch {

        @Test
        @DisplayName("returns true when both locations are null")
        void bothNull() {
            assertThat(service.isLocationMatch(null, null)).isTrue();
        }

        @Test
        @DisplayName("returns true when one location is null")
        void oneNull() {
            assertThat(service.isLocationMatch("Floor 3", null)).isTrue();
            assertThat(service.isLocationMatch(null, "Floor 3")).isTrue();
        }

        @Test
        @DisplayName("returns true when locations are identical")
        void identical() {
            assertThat(service.isLocationMatch("Floor 3", "Floor 3")).isTrue();
        }

        @Test
        @DisplayName("returns true when one contains the other")
        void containment() {
            assertThat(service.isLocationMatch("Floor 3 Corridor", "Floor 3")).isTrue();
            assertThat(service.isLocationMatch("Floor 3", "Floor 3 Corridor")).isTrue();
        }

        @Test
        @DisplayName("returns false when locations are very different")
        void veryDifferent() {
            assertThat(service.isLocationMatch("Floor 1 Lobby", "Floor 10 Rooftop")).isFalse();
        }
    }

    @Nested
    @DisplayName("linkDuplicate")
    class LinkDuplicate {

        @Test
        @DisplayName("successfully links a duplicate ticket to a primary ticket")
        void successfulLink() {
            UUID primaryId = UUID.randomUUID();
            UUID duplicateId = UUID.randomUUID();
            Ticket primaryTicket = createTicketWithId(primaryId, "Original issue", PROPERTY_ID);
            Ticket duplicateTicket = createTicketWithId(duplicateId, "Same issue", PROPERTY_ID);

            when(ticketRepository.findById(primaryId)).thenReturn(Optional.of(primaryTicket));
            when(ticketRepository.findById(duplicateId)).thenReturn(Optional.of(duplicateTicket));
            when(duplicateLinkRepository.existsByPrimaryTicketIdAndDuplicateTicketId(primaryId, duplicateId))
                    .thenReturn(false);
            when(duplicateLinkRepository.save(any(TicketDuplicateLink.class)))
                    .thenAnswer(inv -> {
                        TicketDuplicateLink link = inv.getArgument(0);
                        link.setId(UUID.randomUUID());
                        link.setLinkedAt(Instant.now());
                        return link;
                    });
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

            DuplicateLinkResponse result = service.linkDuplicate(primaryId, duplicateId, USER_ID);

            assertThat(result.primaryTicketId()).isEqualTo(primaryId);
            assertThat(result.duplicateTicketId()).isEqualTo(duplicateId);
            assertThat(result.linkedBy()).isEqualTo(USER_ID);

            // Verify duplicate ticket was updated
            ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
            verify(ticketRepository).save(ticketCaptor.capture());
            Ticket savedDuplicate = ticketCaptor.getValue();
            assertThat(savedDuplicate.getLinkedToTicketId()).isEqualTo(primaryId);
            assertThat(savedDuplicate.isDuplicateFlag()).isTrue();
        }

        @Test
        @DisplayName("rejects self-linking")
        void rejectsSelfLink() {
            UUID ticketId = UUID.randomUUID();

            assertThatThrownBy(() -> service.linkDuplicate(ticketId, ticketId, USER_ID))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("cannot be linked as a duplicate of itself");
        }

        @Test
        @DisplayName("rejects when primary ticket not found")
        void rejectsMissingPrimary() {
            UUID primaryId = UUID.randomUUID();
            UUID duplicateId = UUID.randomUUID();

            when(ticketRepository.findById(primaryId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.linkDuplicate(primaryId, duplicateId, USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("rejects when tickets belong to different properties")
        void rejectsCrossPropertyLink() {
            UUID primaryId = UUID.randomUUID();
            UUID duplicateId = UUID.randomUUID();
            UUID otherPropertyId = UUID.randomUUID();

            Ticket primaryTicket = createTicketWithId(primaryId, "Issue A", PROPERTY_ID);
            Ticket duplicateTicket = createTicketWithId(duplicateId, "Issue B", otherPropertyId);

            when(ticketRepository.findById(primaryId)).thenReturn(Optional.of(primaryTicket));
            when(ticketRepository.findById(duplicateId)).thenReturn(Optional.of(duplicateTicket));

            assertThatThrownBy(() -> service.linkDuplicate(primaryId, duplicateId, USER_ID))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("different properties");
        }

        @Test
        @DisplayName("rejects duplicate link that already exists")
        void rejectsExistingLink() {
            UUID primaryId = UUID.randomUUID();
            UUID duplicateId = UUID.randomUUID();

            Ticket primaryTicket = createTicketWithId(primaryId, "Issue A", PROPERTY_ID);
            Ticket duplicateTicket = createTicketWithId(duplicateId, "Issue B", PROPERTY_ID);

            when(ticketRepository.findById(primaryId)).thenReturn(Optional.of(primaryTicket));
            when(ticketRepository.findById(duplicateId)).thenReturn(Optional.of(duplicateTicket));
            when(duplicateLinkRepository.existsByPrimaryTicketIdAndDuplicateTicketId(primaryId, duplicateId))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.linkDuplicate(primaryId, duplicateId, USER_ID))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("already exists");
        }
    }

    @Nested
    @DisplayName("levenshteinDistance")
    class LevenshteinDistanceTest {

        @Test
        @DisplayName("distance is 0 for identical strings")
        void identicalStrings() {
            assertThat(DuplicateDetectionService.levenshteinDistance("hello", "hello")).isEqualTo(0);
        }

        @Test
        @DisplayName("distance equals length for empty vs non-empty")
        void emptyVsNonEmpty() {
            assertThat(DuplicateDetectionService.levenshteinDistance("", "hello")).isEqualTo(5);
            assertThat(DuplicateDetectionService.levenshteinDistance("hello", "")).isEqualTo(5);
        }

        @Test
        @DisplayName("distance is 1 for single character difference")
        void singleCharDifference() {
            assertThat(DuplicateDetectionService.levenshteinDistance("cat", "bat")).isEqualTo(1);
            assertThat(DuplicateDetectionService.levenshteinDistance("cat", "cats")).isEqualTo(1);
            assertThat(DuplicateDetectionService.levenshteinDistance("cats", "cat")).isEqualTo(1);
        }

        @Test
        @DisplayName("distance for known example")
        void knownExample() {
            assertThat(DuplicateDetectionService.levenshteinDistance("kitten", "sitting")).isEqualTo(3);
        }
    }

    // Helper methods

    private Ticket createTicket(String title, Category category, String location) {
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
                .referenceNumber("SR-2025-" + String.format("%06d", (int)(Math.random() * 999999)))
                .build();
        ticket.setPropertyId(PROPERTY_ID);
        // Use reflection or builder to set ID
        ticket.setId(UUID.randomUUID());
        return ticket;
    }

    private Ticket createTicketWithId(UUID id, String title, UUID propertyId) {
        Ticket ticket = Ticket.builder()
                .title(title)
                .category(Category.PLUMBING)
                .location("Test location")
                .description("Test description")
                .priority(Priority.NORMAL)
                .status(TicketStatus.SUBMITTED)
                .slaStatus(SlaStatus.ON_TRACK)
                .submittedBy(UUID.randomUUID())
                .unitId(UUID.randomUUID())
                .referenceNumber("SR-2025-" + String.format("%06d", (int)(Math.random() * 999999)))
                .build();
        ticket.setPropertyId(propertyId);
        ticket.setId(id);
        return ticket;
    }
}
