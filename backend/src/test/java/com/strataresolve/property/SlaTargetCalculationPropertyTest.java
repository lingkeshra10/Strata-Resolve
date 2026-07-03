package com.strataresolve.property;

import com.strataresolve.sla.service.SlaCalculator;
import com.strataresolve.sla.service.SlaPolicyService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Property-based test for SLA Target Calculation.
 *
 * <p><b>Property 5: SLA Target Calculation</b></p>
 * <p>For any ticket created at time T with an applicable SLA policy defining A acknowledgement
 * hours and R resolution hours, the acknowledgement_due_at SHALL equal T + A hours and
 * resolution_due_at SHALL equal T + R hours, calculated in the property's timezone using
 * calendar hours.</p>
 *
 * <p><b>Validates: Requirements 6.5, 14.2</b></p>
 */
@Tag("Feature: strataresolve-platform")
@Tag("Property 5: SLA Target Calculation")
class SlaTargetCalculationPropertyTest {

    private final SlaCalculator slaCalculator = new SlaCalculator(mock(SlaPolicyService.class));

    // =====================================================================
    // Property: acknowledgement_due_at equals T + A hours in timezone
    // =====================================================================

    /**
     * For any reference time T, any valid acknowledgement hours A, and any timezone,
     * the computed acknowledgement_due_at SHALL equal T + A calendar hours in that timezone.
     *
     * <p><b>Validates: Requirements 6.5, 14.2</b></p>
     */
    @Property(tries = 100)
    void acknowledgementDueAtEqualsReferencePlusAckHoursInTimezone(
            @ForAll("referenceTimeArbitrary") Instant referenceTime,
            @ForAll @IntRange(min = 1, max = 168) int ackHours,
            @ForAll @IntRange(min = 1, max = 168) int resHours,
            @ForAll("timezoneArbitrary") String timezone
    ) {
        // Act
        SlaCalculator.SlaTargets targets = slaCalculator.computeTargets(
                timezone, ackHours, resHours, referenceTime);

        // Assert: acknowledgement_due_at = T + A hours in property timezone
        ZoneId zoneId = ZoneId.of(timezone);
        ZonedDateTime referenceZoned = referenceTime.atZone(zoneId);
        ZonedDateTime expectedAckDue = referenceZoned.plusHours(ackHours);

        assertThat(targets.acknowledgementDueAt())
                .as("acknowledgement_due_at should equal reference time + %d hours in %s", ackHours, timezone)
                .isEqualTo(expectedAckDue.toInstant());
    }

    // =====================================================================
    // Property: resolution_due_at equals T + R hours in timezone
    // =====================================================================

    /**
     * For any reference time T, any valid resolution hours R, and any timezone,
     * the computed resolution_due_at SHALL equal T + R calendar hours in that timezone.
     *
     * <p><b>Validates: Requirements 6.5, 14.2</b></p>
     */
    @Property(tries = 100)
    void resolutionDueAtEqualsReferencePlusResHoursInTimezone(
            @ForAll("referenceTimeArbitrary") Instant referenceTime,
            @ForAll @IntRange(min = 1, max = 168) int ackHours,
            @ForAll @IntRange(min = 1, max = 168) int resHours,
            @ForAll("timezoneArbitrary") String timezone
    ) {
        // Act
        SlaCalculator.SlaTargets targets = slaCalculator.computeTargets(
                timezone, ackHours, resHours, referenceTime);

        // Assert: resolution_due_at = T + R hours in property timezone
        ZoneId zoneId = ZoneId.of(timezone);
        ZonedDateTime referenceZoned = referenceTime.atZone(zoneId);
        ZonedDateTime expectedResDue = referenceZoned.plusHours(resHours);

        assertThat(targets.resolutionDueAt())
                .as("resolution_due_at should equal reference time + %d hours in %s", resHours, timezone)
                .isEqualTo(expectedResDue.toInstant());
    }

    // =====================================================================
    // Property: resolution_due_at >= acknowledgement_due_at when R >= A
    // =====================================================================

    /**
     * For any reference time T and any valid ack/resolution hours where resolution hours >= ack hours,
     * the computed resolution_due_at SHALL always be >= acknowledgement_due_at.
     *
     * <p><b>Validates: Requirements 6.5, 14.2</b></p>
     */
    @Property(tries = 100)
    void resolutionDueIsAfterOrEqualAckDueWhenResHoursGreaterOrEqual(
            @ForAll("referenceTimeArbitrary") Instant referenceTime,
            @ForAll @IntRange(min = 1, max = 168) int ackHours,
            @ForAll("timezoneArbitrary") String timezone
    ) {
        // Ensure resolution hours >= ack hours
        int resHours = ackHours + (int) (Math.random() * 100);

        // Act
        SlaCalculator.SlaTargets targets = slaCalculator.computeTargets(
                timezone, ackHours, resHours, referenceTime);

        // Assert
        assertThat(targets.resolutionDueAt())
                .as("resolution_due_at should be >= acknowledgement_due_at when resHours(%d) >= ackHours(%d)",
                        resHours, ackHours)
                .isAfterOrEqualTo(targets.acknowledgementDueAt());
    }

    // =====================================================================
    // Property: computation works correctly across different timezones
    // =====================================================================

    /**
     * For any reference time T, the computed targets SHALL always be exactly A and R
     * physical hours after T regardless of timezone. Since ZonedDateTime.plusHours()
     * adds to the instant timeline, the resulting instant is always T + N*3600 seconds.
     * This holds even across DST transitions.
     *
     * <p><b>Validates: Requirements 6.5, 14.2</b></p>
     */
    @Property(tries = 100)
    void computationIsConsistentAcrossTimezones(
            @ForAll("referenceTimeArbitrary") Instant referenceTime,
            @ForAll @IntRange(min = 1, max = 168) int ackHours,
            @ForAll @IntRange(min = 1, max = 168) int resHours,
            @ForAll("timezoneArbitrary") String timezone
    ) {
        // Act
        SlaCalculator.SlaTargets targets = slaCalculator.computeTargets(
                timezone, ackHours, resHours, referenceTime);

        // Assert: targets always have valid (non-null) values
        assertThat(targets.hasTargets()).isTrue();
        assertThat(targets.acknowledgementDueAt()).isNotNull();
        assertThat(targets.resolutionDueAt()).isNotNull();

        // Assert: the instant difference is exactly the specified number of hours
        // This verifies consistency regardless of DST transitions
        long ackDiffSeconds = targets.acknowledgementDueAt().getEpochSecond() - referenceTime.getEpochSecond();
        long resDiffSeconds = targets.resolutionDueAt().getEpochSecond() - referenceTime.getEpochSecond();

        assertThat(ackDiffSeconds)
                .as("acknowledgement_due_at should be exactly %d hours (%d seconds) after reference time in timezone %s",
                        ackHours, (long) ackHours * 3600, timezone)
                .isEqualTo((long) ackHours * 3600);
        assertThat(resDiffSeconds)
                .as("resolution_due_at should be exactly %d hours (%d seconds) after reference time in timezone %s",
                        resHours, (long) resHours * 3600, timezone)
                .isEqualTo((long) resHours * 3600);
    }

    // =====================================================================
    // Arbitraries (Generators)
    // =====================================================================

    /**
     * Generates random reference times spanning a wide range of dates,
     * including DST transition periods.
     */
    @Provide
    Arbitrary<Instant> referenceTimeArbitrary() {
        // Generate instants between 2020-01-01 and 2030-12-31
        long minEpochSecond = Instant.parse("2020-01-01T00:00:00Z").getEpochSecond();
        long maxEpochSecond = Instant.parse("2030-12-31T23:59:59Z").getEpochSecond();

        return Arbitraries.longs()
                .between(minEpochSecond, maxEpochSecond)
                .map(Instant::ofEpochSecond);
    }

    /**
     * Generates timezone identifiers covering various UTC offsets and DST rules.
     */
    @Provide
    Arbitrary<String> timezoneArbitrary() {
        return Arbitraries.of(
                "UTC",
                "Asia/Kuala_Lumpur",      // UTC+8, no DST
                "America/New_York",       // UTC-5/-4, DST
                "Europe/London",          // UTC+0/+1, DST
                "Australia/Sydney",       // UTC+10/+11, DST
                "Asia/Tokyo",             // UTC+9, no DST
                "America/Los_Angeles",    // UTC-8/-7, DST
                "Europe/Berlin",          // UTC+1/+2, DST
                "Pacific/Auckland",       // UTC+12/+13, DST
                "Asia/Kolkata"            // UTC+5:30, no DST (half-hour offset)
        );
    }
}
