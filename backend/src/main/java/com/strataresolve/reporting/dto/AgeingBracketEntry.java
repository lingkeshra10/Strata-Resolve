package com.strataresolve.reporting.dto;

import java.util.List;
import java.util.UUID;

/**
 * Represents a single bracket entry in the ageing report,
 * containing the bracket name, the count of tickets in that bracket,
 * and the list of ticket IDs.
 */
public record AgeingBracketEntry(
        AgeBracket bracket,
        String label,
        int count,
        List<UUID> ticketIds
) {

    /**
     * Creates a bracket entry from the given bracket and ticket IDs.
     */
    public static AgeingBracketEntry of(AgeBracket bracket, List<UUID> ticketIds) {
        return new AgeingBracketEntry(bracket, bracket.getLabel(), ticketIds.size(), ticketIds);
    }
}
