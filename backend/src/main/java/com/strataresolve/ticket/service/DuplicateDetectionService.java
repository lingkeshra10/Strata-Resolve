package com.strataresolve.ticket.service;

import com.strataresolve.shared.exception.BusinessRuleViolationException;
import com.strataresolve.shared.exception.ResourceNotFoundException;
import com.strataresolve.ticket.config.TicketProperties;
import com.strataresolve.ticket.domain.Category;
import com.strataresolve.ticket.domain.Ticket;
import com.strataresolve.ticket.domain.TicketDuplicateLink;
import com.strataresolve.ticket.dto.DuplicateDetectionResult;
import com.strataresolve.ticket.dto.DuplicateLinkResponse;
import com.strataresolve.ticket.repository.TicketDuplicateLinkRepository;
import com.strataresolve.ticket.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Service responsible for detecting potential duplicate tickets and managing duplicate links.
 *
 * <p>Duplicate detection works by checking for tickets in the same property with the same
 * category, similar title, and similar location, created within a configurable recent time window.
 *
 * <p>When duplicates are detected, the new ticket is flagged for Property Manager review
 * but is NOT blocked from submission (Requirement 16.2).
 *
 * <p>Property Managers can manually link confirmed duplicate tickets to a primary ticket
 * (Requirement 16.3).
 */
@Service
@Transactional
public class DuplicateDetectionService {

    private static final Logger log = LoggerFactory.getLogger(DuplicateDetectionService.class);

    private final TicketRepository ticketRepository;
    private final TicketDuplicateLinkRepository duplicateLinkRepository;
    private final TicketProperties ticketProperties;

    public DuplicateDetectionService(TicketRepository ticketRepository,
                                     TicketDuplicateLinkRepository duplicateLinkRepository,
                                     TicketProperties ticketProperties) {
        this.ticketRepository = ticketRepository;
        this.duplicateLinkRepository = duplicateLinkRepository;
        this.ticketProperties = ticketProperties;
    }

    /**
     * Checks for potential duplicates of a ticket being submitted.
     *
     * <p>A ticket is considered a potential duplicate if within the same property and recent time window:
     * <ul>
     *   <li>It shares the same category</li>
     *   <li>Its title has a similarity score above the configured threshold</li>
     *   <li>Its location matches (if both have a location specified)</li>
     * </ul>
     *
     * @param propertyId the property ID
     * @param title      the title of the new ticket
     * @param category   the category of the new ticket
     * @param location   the location of the new ticket (may be null)
     * @return the duplicate detection result with potential duplicates
     */
    @Transactional(readOnly = true)
    public DuplicateDetectionResult checkForDuplicates(UUID propertyId, String title, Category category, String location) {
        TicketProperties.DuplicateDetection config = ticketProperties.duplicateDetection();
        Duration timeWindow = Duration.ofHours(config.timeWindowHours());
        Instant since = Instant.now().minus(timeWindow);

        // Find candidates: same property, same category, within time window
        List<Ticket> candidates = ticketRepository.findPotentialDuplicateCandidates(propertyId, category, since);

        if (candidates.isEmpty()) {
            return DuplicateDetectionResult.noDuplicates();
        }

        List<DuplicateDetectionResult.PotentialDuplicate> potentialDuplicates = new ArrayList<>();
        double threshold = config.similarityThreshold();

        for (Ticket candidate : candidates) {
            double titleSimilarity = calculateTitleSimilarity(title, candidate.getTitle());
            boolean locationMatch = isLocationMatch(location, candidate.getLocation());

            // A candidate is a potential duplicate if:
            // 1. Title similarity >= threshold, AND
            // 2. Location matches (if both are specified)
            if (titleSimilarity >= threshold && locationMatch) {
                potentialDuplicates.add(new DuplicateDetectionResult.PotentialDuplicate(
                        candidate.getId(),
                        candidate.getReferenceNumber(),
                        candidate.getTitle(),
                        candidate.getLocation(),
                        titleSimilarity
                ));
            }
        }

        if (potentialDuplicates.isEmpty()) {
            return DuplicateDetectionResult.noDuplicates();
        }

        log.info("Detected {} potential duplicate(s) for new ticket in property {} with title '{}'",
                potentialDuplicates.size(), propertyId, title);

        return new DuplicateDetectionResult(true, potentialDuplicates);
    }

    /**
     * Flags a ticket as a potential duplicate.
     * Called during ticket submission when duplicates are detected.
     *
     * @param ticket the ticket to flag
     */
    public void flagAsDuplicate(Ticket ticket) {
        ticket.setDuplicateFlag(true);
        ticketRepository.save(ticket);
    }

    /**
     * Manually links a duplicate ticket to a primary ticket.
     * Only Property Managers should be able to call this.
     *
     * @param primaryTicketId   the ID of the primary (original) ticket
     * @param duplicateTicketId the ID of the duplicate ticket
     * @param linkedBy          the ID of the Property Manager performing the link
     * @return the created duplicate link response
     * @throws ResourceNotFoundException if either ticket is not found
     * @throws BusinessRuleViolationException if linking would create a self-reference or already exists
     */
    public DuplicateLinkResponse linkDuplicate(UUID primaryTicketId, UUID duplicateTicketId, UUID linkedBy) {
        if (primaryTicketId.equals(duplicateTicketId)) {
            throw new BusinessRuleViolationException("A ticket cannot be linked as a duplicate of itself.");
        }

        // Validate both tickets exist
        Ticket primaryTicket = ticketRepository.findById(primaryTicketId)
                .orElseThrow(() -> new ResourceNotFoundException("Primary Ticket", primaryTicketId));
        Ticket duplicateTicket = ticketRepository.findById(duplicateTicketId)
                .orElseThrow(() -> new ResourceNotFoundException("Duplicate Ticket", duplicateTicketId));

        // Validate both tickets belong to the same property
        if (!primaryTicket.getPropertyId().equals(duplicateTicket.getPropertyId())) {
            throw new BusinessRuleViolationException(
                    "Cannot link tickets from different properties as duplicates.");
        }

        // Check if the link already exists
        if (duplicateLinkRepository.existsByPrimaryTicketIdAndDuplicateTicketId(primaryTicketId, duplicateTicketId)) {
            throw new BusinessRuleViolationException(
                    "This duplicate link already exists.");
        }

        // Create the link
        TicketDuplicateLink link = TicketDuplicateLink.builder()
                .primaryTicketId(primaryTicketId)
                .duplicateTicketId(duplicateTicketId)
                .linkedBy(linkedBy)
                .build();
        TicketDuplicateLink savedLink = duplicateLinkRepository.save(link);

        // Update the duplicate ticket to reference the primary
        duplicateTicket.setLinkedToTicketId(primaryTicketId);
        duplicateTicket.setDuplicateFlag(true);
        ticketRepository.save(duplicateTicket);

        log.info("Ticket {} linked as duplicate of primary ticket {} by user {}",
                duplicateTicketId, primaryTicketId, linkedBy);

        return DuplicateLinkResponse.from(savedLink);
    }

    /**
     * Gets all duplicate links for a ticket (as primary or duplicate).
     *
     * @param ticketId the ticket ID
     * @return list of duplicate link responses
     */
    @Transactional(readOnly = true)
    public List<DuplicateLinkResponse> getDuplicateLinks(UUID ticketId) {
        List<TicketDuplicateLink> asPrimary = duplicateLinkRepository.findByPrimaryTicketId(ticketId);
        List<TicketDuplicateLink> asDuplicate = duplicateLinkRepository.findByDuplicateTicketId(ticketId);

        List<DuplicateLinkResponse> results = new ArrayList<>();
        asPrimary.stream().map(DuplicateLinkResponse::from).forEach(results::add);
        asDuplicate.stream().map(DuplicateLinkResponse::from).forEach(results::add);
        return results;
    }

    /**
     * Gets all tickets flagged as potential duplicates for a property.
     *
     * @param propertyId the property ID
     * @return list of flagged tickets
     */
    @Transactional(readOnly = true)
    public List<Ticket> getFlaggedDuplicates(UUID propertyId) {
        return ticketRepository.findByPropertyIdAndDuplicateFlagTrue(propertyId);
    }

    /**
     * Calculates the similarity between two titles using a normalized Levenshtein distance approach.
     * The score ranges from 0.0 (completely different) to 1.0 (identical).
     *
     * <p>The comparison is case-insensitive and trims whitespace.
     *
     * @param title1 the first title
     * @param title2 the second title
     * @return similarity score between 0.0 and 1.0
     */
    double calculateTitleSimilarity(String title1, String title2) {
        if (title1 == null || title2 == null) {
            return 0.0;
        }

        String normalized1 = title1.toLowerCase(Locale.ROOT).trim();
        String normalized2 = title2.toLowerCase(Locale.ROOT).trim();

        if (normalized1.equals(normalized2)) {
            return 1.0;
        }

        if (normalized1.isEmpty() || normalized2.isEmpty()) {
            return 0.0;
        }

        int distance = levenshteinDistance(normalized1, normalized2);
        int maxLength = Math.max(normalized1.length(), normalized2.length());

        return 1.0 - ((double) distance / maxLength);
    }

    /**
     * Checks if two locations match for duplicate detection purposes.
     * If either location is null or blank, this is considered a match (not enough info to distinguish).
     * If both are present, they are compared case-insensitively with tolerance for minor differences.
     *
     * @param location1 the first location
     * @param location2 the second location
     * @return true if locations are considered matching
     */
    boolean isLocationMatch(String location1, String location2) {
        // If either location is missing, we can't distinguish — treat as a match
        if (location1 == null || location1.isBlank() || location2 == null || location2.isBlank()) {
            return true;
        }

        String normalized1 = location1.toLowerCase(Locale.ROOT).trim();
        String normalized2 = location2.toLowerCase(Locale.ROOT).trim();

        // Exact match
        if (normalized1.equals(normalized2)) {
            return true;
        }

        // Check if one contains the other (partial match for location descriptions)
        if (normalized1.contains(normalized2) || normalized2.contains(normalized1)) {
            return true;
        }

        // Use similarity threshold for close matches
        double similarity = calculateTitleSimilarity(location1, location2);
        return similarity >= 0.7; // Slightly higher threshold for location matching
    }

    /**
     * Computes the Levenshtein distance between two strings.
     * This is the minimum number of single-character edits (insertions, deletions, or substitutions)
     * required to change one string into the other.
     *
     * @param s the first string
     * @param t the second string
     * @return the Levenshtein distance
     */
    static int levenshteinDistance(String s, String t) {
        int m = s.length();
        int n = t.length();

        // Use two rows for space optimization
        int[] previousRow = new int[n + 1];
        int[] currentRow = new int[n + 1];

        // Initialize the first row
        for (int j = 0; j <= n; j++) {
            previousRow[j] = j;
        }

        for (int i = 1; i <= m; i++) {
            currentRow[0] = i;

            for (int j = 1; j <= n; j++) {
                int cost = (s.charAt(i - 1) == t.charAt(j - 1)) ? 0 : 1;
                currentRow[j] = Math.min(
                        Math.min(currentRow[j - 1] + 1, previousRow[j] + 1),
                        previousRow[j - 1] + cost
                );
            }

            // Swap rows
            int[] temp = previousRow;
            previousRow = currentRow;
            currentRow = temp;
        }

        return previousRow[n];
    }
}
