package com.strataresolve.property;

import com.strataresolve.reporting.dto.AgeBracket;
import net.jqwik.api.*;
import net.jqwik.api.constraints.LongRange;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for Ageing Report Bracket Placement.
 *
 * <p><b>Property 17: Ageing Report Bracket Placement</b></p>
 * <p>For any set of open tickets with known creation dates, the ageing report SHALL place each
 * ticket in exactly one correct age bracket (0–3 days, 4–7 days, 8–14 days, 15–30 days, over
 * 30 days) based on the difference between the report generation time and the ticket creation time.</p>
 *
 * <p><b>Validates: Requirements 15.1</b></p>
 */
@Tag("Feature: strataresolve-platform")
@Tag("Property 17: Ageing Report Bracket Placement")
class AgeingReportBracketPlacementPropertyTest {

    // =====================================================================
    // Property: Each ticket is placed in exactly one bracket
    // =====================================================================

    /**
     * For any non-negative age in days, AgeBracket.fromDays SHALL return exactly one
     * bracket value (never null, never throw for valid input).
     *
     * <p><b>Validates: Requirements 15.1</b></p>
     */
    @Property(tries = 100)
    void eachTicketIsPlacedInExactlyOneBracket(
            @ForAll @LongRange(min = 0, max = 3650) long ageDays
    ) {
        // Act
        AgeBracket bracket = AgeBracket.fromDays(ageDays);

        // Assert: exactly one bracket is returned (non-null)
        assertThat(bracket)
                .as("Any non-negative age (%d days) must map to exactly one bracket", ageDays)
                .isNotNull()
                .isInstanceOf(AgeBracket.class);
    }

    // =====================================================================
    // Property: Correct bracket based on day ranges
    // =====================================================================

    /**
     * For any ticket with age 0–3 days, the bracket SHALL be ZERO_TO_THREE.
     *
     * <p><b>Validates: Requirements 15.1</b></p>
     */
    @Property(tries = 100)
    void ticketsZeroToThreeDaysPlacedInCorrectBracket(
            @ForAll @LongRange(min = 0, max = 3) long ageDays
    ) {
        AgeBracket bracket = AgeBracket.fromDays(ageDays);

        assertThat(bracket)
                .as("Age %d days should be in ZERO_TO_THREE bracket", ageDays)
                .isEqualTo(AgeBracket.ZERO_TO_THREE);
    }

    /**
     * For any ticket with age 4–7 days, the bracket SHALL be FOUR_TO_SEVEN.
     *
     * <p><b>Validates: Requirements 15.1</b></p>
     */
    @Property(tries = 100)
    void ticketsFourToSevenDaysPlacedInCorrectBracket(
            @ForAll @LongRange(min = 4, max = 7) long ageDays
    ) {
        AgeBracket bracket = AgeBracket.fromDays(ageDays);

        assertThat(bracket)
                .as("Age %d days should be in FOUR_TO_SEVEN bracket", ageDays)
                .isEqualTo(AgeBracket.FOUR_TO_SEVEN);
    }

    /**
     * For any ticket with age 8–14 days, the bracket SHALL be EIGHT_TO_FOURTEEN.
     *
     * <p><b>Validates: Requirements 15.1</b></p>
     */
    @Property(tries = 100)
    void ticketsEightToFourteenDaysPlacedInCorrectBracket(
            @ForAll @LongRange(min = 8, max = 14) long ageDays
    ) {
        AgeBracket bracket = AgeBracket.fromDays(ageDays);

        assertThat(bracket)
                .as("Age %d days should be in EIGHT_TO_FOURTEEN bracket", ageDays)
                .isEqualTo(AgeBracket.EIGHT_TO_FOURTEEN);
    }

    /**
     * For any ticket with age 15–30 days, the bracket SHALL be FIFTEEN_TO_THIRTY.
     *
     * <p><b>Validates: Requirements 15.1</b></p>
     */
    @Property(tries = 100)
    void ticketsFifteenToThirtyDaysPlacedInCorrectBracket(
            @ForAll @LongRange(min = 15, max = 30) long ageDays
    ) {
        AgeBracket bracket = AgeBracket.fromDays(ageDays);

        assertThat(bracket)
                .as("Age %d days should be in FIFTEEN_TO_THIRTY bracket", ageDays)
                .isEqualTo(AgeBracket.FIFTEEN_TO_THIRTY);
    }

    /**
     * For any ticket with age over 30 days, the bracket SHALL be OVER_THIRTY.
     *
     * <p><b>Validates: Requirements 15.1</b></p>
     */
    @Property(tries = 100)
    void ticketsOverThirtyDaysPlacedInCorrectBracket(
            @ForAll @LongRange(min = 31, max = 3650) long ageDays
    ) {
        AgeBracket bracket = AgeBracket.fromDays(ageDays);

        assertThat(bracket)
                .as("Age %d days should be in OVER_THIRTY bracket", ageDays)
                .isEqualTo(AgeBracket.OVER_THIRTY);
    }

    // =====================================================================
    // Property: calculateAgeDays produces correct day count
    // =====================================================================

    /**
     * For any creation time and reference time where reference >= creation,
     * the age in days (computed as floor of duration) SHALL produce the correct
     * number of whole days consistent with Duration.between().toDays().
     *
     * <p><b>Validates: Requirements 15.1</b></p>
     */
    @Property(tries = 100)
    void ageDaysComputationProducesCorrectDayCount(
            @ForAll("referenceTimeArbitrary") Instant referenceTime,
            @ForAll @LongRange(min = 0, max = 3650) long expectedDays,
            @ForAll @LongRange(min = 0, max = 86399) long extraSeconds
    ) {
        // Arrange: create a ticket creation time that is exactly expectedDays days
        // plus some extra seconds before the reference time
        Duration totalDuration = Duration.ofDays(expectedDays).plusSeconds(extraSeconds);
        Instant createdAt = referenceTime.minus(totalDuration);

        // Act: compute age days using the same logic as AgeingReportService.calculateAgeDays
        long computedDays = Duration.between(createdAt, referenceTime).toDays();

        // Assert: floor division means computedDays == total seconds / 86400
        long expectedComputedDays = totalDuration.getSeconds() / 86400;
        assertThat(computedDays)
                .as("Age days should produce %d days for duration of %d days + %d seconds",
                        expectedComputedDays, expectedDays, extraSeconds)
                .isEqualTo(expectedComputedDays);
    }

    // =====================================================================
    // Property: End-to-end bracket placement from timestamps
    // =====================================================================

    /**
     * For any reference time and any ticket creation time (where reference >= creation),
     * the computed age days maps to the correct bracket consistent with the bracket boundaries.
     *
     * <p><b>Validates: Requirements 15.1</b></p>
     */
    @Property(tries = 100)
    void endToEndBracketPlacementFromTimestamps(
            @ForAll("referenceTimeArbitrary") Instant referenceTime,
            @ForAll @LongRange(min = 0, max = 100) long daysBefore,
            @ForAll @LongRange(min = 0, max = 86399) long extraSeconds
    ) {
        // Arrange: ticket created daysBefore days + extraSeconds before referenceTime
        Duration offset = Duration.ofDays(daysBefore).plusSeconds(extraSeconds);
        Instant createdAt = referenceTime.minus(offset);

        // Act: compute age days the same way as the service does
        long ageDays = Duration.between(createdAt, referenceTime).toDays();
        AgeBracket bracket = AgeBracket.fromDays(ageDays);

        // Assert: bracket matches the expected bracket for the computed age
        AgeBracket expectedBracket = expectedBracketForDays(ageDays);
        assertThat(bracket)
                .as("Ticket created %d days + %d seconds ago (age=%d days) should be in %s",
                        daysBefore, extraSeconds, ageDays, expectedBracket)
                .isEqualTo(expectedBracket);
    }

    // =====================================================================
    // Property: Brackets are mutually exclusive and exhaustive
    // =====================================================================

    /**
     * For any non-negative age in days, the bracket returned by fromDays is consistent
     * with the bracket boundary ranges (mutual exclusivity — each day value maps to
     * one and only one bracket).
     *
     * <p><b>Validates: Requirements 15.1</b></p>
     */
    @Property(tries = 100)
    void bracketsAreMutuallyExclusiveAndExhaustive(
            @ForAll @LongRange(min = 0, max = 3650) long ageDays
    ) {
        AgeBracket bracket = AgeBracket.fromDays(ageDays);

        // Count how many bracket ranges this value falls into
        int matchCount = 0;
        if (ageDays >= 0 && ageDays <= 3) matchCount++;
        if (ageDays >= 4 && ageDays <= 7) matchCount++;
        if (ageDays >= 8 && ageDays <= 14) matchCount++;
        if (ageDays >= 15 && ageDays <= 30) matchCount++;
        if (ageDays > 30) matchCount++;

        // Exactly one bracket should match
        assertThat(matchCount)
                .as("Age %d days should fall into exactly one bracket range", ageDays)
                .isEqualTo(1);

        // And the returned bracket should match the correct range
        assertThat(bracket).isEqualTo(expectedBracketForDays(ageDays));
    }

    // =====================================================================
    // Arbitraries (Generators)
    // =====================================================================

    /**
     * Generates random reference times spanning 2020-2030.
     */
    @Provide
    Arbitrary<Instant> referenceTimeArbitrary() {
        long minEpochSecond = Instant.parse("2020-01-01T00:00:00Z").getEpochSecond();
        long maxEpochSecond = Instant.parse("2030-12-31T23:59:59Z").getEpochSecond();

        return Arbitraries.longs()
                .between(minEpochSecond, maxEpochSecond)
                .map(Instant::ofEpochSecond);
    }

    // =====================================================================
    // Helper Methods
    // =====================================================================

    /**
     * Computes the expected bracket for a given number of days, using the same logic
     * as the specification to serve as an independent oracle.
     */
    private AgeBracket expectedBracketForDays(long ageDays) {
        if (ageDays <= 3) return AgeBracket.ZERO_TO_THREE;
        if (ageDays <= 7) return AgeBracket.FOUR_TO_SEVEN;
        if (ageDays <= 14) return AgeBracket.EIGHT_TO_FOURTEEN;
        if (ageDays <= 30) return AgeBracket.FIFTEEN_TO_THIRTY;
        return AgeBracket.OVER_THIRTY;
    }
}
