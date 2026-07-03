package com.strataresolve.ticket.dto;

import java.util.List;
import java.util.UUID;

/**
 * Result DTO for duplicate detection check.
 * Contains information about whether a ticket was flagged as a potential duplicate
 * and which existing tickets it may be duplicating.
 */
public record DuplicateDetectionResult(
        boolean flaggedAsDuplicate,
        List<PotentialDuplicate> potentialDuplicates
) {

    /**
     * Represents a potential duplicate ticket match with its similarity score.
     */
    public record PotentialDuplicate(
            UUID ticketId,
            String referenceNumber,
            String title,
            String location,
            double similarityScore
    ) {
    }

    /**
     * Creates a result indicating no duplicates were found.
     */
    public static DuplicateDetectionResult noDuplicates() {
        return new DuplicateDetectionResult(false, List.of());
    }
}
