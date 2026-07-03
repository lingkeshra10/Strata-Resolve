package com.strataresolve.reporting.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for the ageing report, containing the property context,
 * generation timestamp, total open ticket count, and the breakdown by age brackets.
 */
public record AgeingReportResponse(
        UUID propertyId,
        Instant generatedAt,
        int totalOpenTickets,
        List<AgeingBracketEntry> brackets
) {
}
