package com.strataresolve.sla.service;

import com.strataresolve.sla.domain.SlaPolicy;
import com.strataresolve.ticket.domain.Category;
import com.strataresolve.ticket.domain.Priority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SlaCalculator}.
 * Validates Requirements 6.5 and 14.2: SLA target timestamp calculation using
 * calendar hours in the property timezone.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SlaCalculator")
class SlaCalculatorTest {

    @Mock
    private SlaPolicyService slaPolicyService;

    private SlaCalculator slaCalculator;

    private static final UUID PROPERTY_ID = UUID.randomUUID();
    private static final String TIMEZONE_KL = "Asia/Kuala_Lumpur";
    private static final String TIMEZONE_UTC = "UTC";
    private static final String TIMEZONE_NY = "America/New_York";

    @BeforeEach
    void setUp() {
        slaCalculator = new SlaCalculator(slaPolicyService);
    }

    @Nested
    @DisplayName("calculateTargets")
    class CalculateTargetsTests {

        @Test
        @DisplayName("should calculate targets from resolved policy")
        void shouldCalculateTargetsFromPolicy() {
            SlaPolicy policy = SlaPolicy.builder()
                    .id(UUID.randomUUID())
                    .category(Category.PLUMBING)
                    .priority(Priority.HIGH)
                    .acknowledgementHours(4)
                    .resolutionHours(24)
                    .isDefault(false)
                    .build();
            policy.setPropertyId(PROPERTY_ID);

            when(slaPolicyService.resolvePolicy(PROPERTY_ID, Category.PLUMBING, Priority.HIGH))
                    .thenReturn(Optional.of(policy));

            SlaCalculator.SlaTargets result = slaCalculator.calculateTargets(
                    PROPERTY_ID, TIMEZONE_KL, Category.PLUMBING, Priority.HIGH);

            assertThat(result.hasTargets()).isTrue();
            assertThat(result.acknowledgementDueAt()).isNotNull();
            assertThat(result.resolutionDueAt()).isNotNull();
            // Ack due should be approximately 4 hours from now
            assertThat(result.acknowledgementDueAt())
                    .isCloseTo(Instant.now().plus(4, ChronoUnit.HOURS), within(5, ChronoUnit.SECONDS));
            // Resolution due should be approximately 24 hours from now
            assertThat(result.resolutionDueAt())
                    .isCloseTo(Instant.now().plus(24, ChronoUnit.HOURS), within(5, ChronoUnit.SECONDS));
        }

        @Test
        @DisplayName("should return null targets when no policy exists")
        void shouldReturnNullTargetsWhenNoPolicyExists() {
            when(slaPolicyService.resolvePolicy(PROPERTY_ID, Category.ELECTRICAL, Priority.LOW))
                    .thenReturn(Optional.empty());

            SlaCalculator.SlaTargets result = slaCalculator.calculateTargets(
                    PROPERTY_ID, TIMEZONE_KL, Category.ELECTRICAL, Priority.LOW);

            assertThat(result.hasTargets()).isFalse();
            assertThat(result.acknowledgementDueAt()).isNull();
            assertThat(result.resolutionDueAt()).isNull();
        }

        @Test
        @DisplayName("should use property timezone for calendar-hour calculation")
        void shouldUsePropertyTimezoneForCalculation() {
            SlaPolicy policy = SlaPolicy.builder()
                    .id(UUID.randomUUID())
                    .category(Category.LIFT)
                    .priority(Priority.URGENT)
                    .acknowledgementHours(2)
                    .resolutionHours(8)
                    .isDefault(false)
                    .build();
            policy.setPropertyId(PROPERTY_ID);

            when(slaPolicyService.resolvePolicy(PROPERTY_ID, Category.LIFT, Priority.URGENT))
                    .thenReturn(Optional.of(policy));

            SlaCalculator.SlaTargets result = slaCalculator.calculateTargets(
                    PROPERTY_ID, TIMEZONE_KL, Category.LIFT, Priority.URGENT);

            // Verify the targets are correct in the property's timezone
            ZonedDateTime nowInKL = ZonedDateTime.now(ZoneId.of(TIMEZONE_KL));
            ZonedDateTime expectedAck = nowInKL.plusHours(2);
            ZonedDateTime expectedRes = nowInKL.plusHours(8);

            assertThat(result.acknowledgementDueAt())
                    .isCloseTo(expectedAck.toInstant(), within(5, ChronoUnit.SECONDS));
            assertThat(result.resolutionDueAt())
                    .isCloseTo(expectedRes.toInstant(), within(5, ChronoUnit.SECONDS));
        }

        @Test
        @DisplayName("resolution_due_at should always be after acknowledgement_due_at when resolution hours > ack hours")
        void resolutionDueShouldBeAfterAcknowledgementDue() {
            SlaPolicy policy = SlaPolicy.builder()
                    .id(UUID.randomUUID())
                    .category(Category.SECURITY)
                    .priority(Priority.EMERGENCY)
                    .acknowledgementHours(1)
                    .resolutionHours(4)
                    .isDefault(false)
                    .build();
            policy.setPropertyId(PROPERTY_ID);

            when(slaPolicyService.resolvePolicy(PROPERTY_ID, Category.SECURITY, Priority.EMERGENCY))
                    .thenReturn(Optional.of(policy));

            SlaCalculator.SlaTargets result = slaCalculator.calculateTargets(
                    PROPERTY_ID, TIMEZONE_NY, Category.SECURITY, Priority.EMERGENCY);

            assertThat(result.resolutionDueAt()).isAfter(result.acknowledgementDueAt());
        }
    }

    @Nested
    @DisplayName("calculateTargetsAtTime")
    class CalculateTargetsAtTimeTests {

        @Test
        @DisplayName("should calculate targets from a specific reference time")
        void shouldCalculateTargetsFromReferenceTime() {
            SlaPolicy policy = SlaPolicy.builder()
                    .id(UUID.randomUUID())
                    .category(Category.CLEANING)
                    .priority(Priority.NORMAL)
                    .acknowledgementHours(8)
                    .resolutionHours(48)
                    .isDefault(false)
                    .build();
            policy.setPropertyId(PROPERTY_ID);

            when(slaPolicyService.resolvePolicy(PROPERTY_ID, Category.CLEANING, Priority.NORMAL))
                    .thenReturn(Optional.of(policy));

            // Use a fixed reference time
            Instant referenceTime = Instant.parse("2025-06-15T10:00:00Z");

            SlaCalculator.SlaTargets result = slaCalculator.calculateTargetsAtTime(
                    PROPERTY_ID, TIMEZONE_UTC, Category.CLEANING, Priority.NORMAL, referenceTime);

            // In UTC timezone: 10:00 + 8h = 18:00, 10:00 + 48h = next day 10:00 + 2 days
            ZonedDateTime refInUtc = referenceTime.atZone(ZoneId.of(TIMEZONE_UTC));
            assertThat(result.acknowledgementDueAt())
                    .isEqualTo(refInUtc.plusHours(8).toInstant());
            assertThat(result.resolutionDueAt())
                    .isEqualTo(refInUtc.plusHours(48).toInstant());
        }

        @Test
        @DisplayName("should handle DST transitions correctly with calendar hours")
        void shouldHandleDstTransitionsCorrectly() {
            SlaPolicy policy = SlaPolicy.builder()
                    .id(UUID.randomUUID())
                    .category(Category.ELECTRICAL)
                    .priority(Priority.HIGH)
                    .acknowledgementHours(4)
                    .resolutionHours(24)
                    .isDefault(false)
                    .build();
            policy.setPropertyId(PROPERTY_ID);

            when(slaPolicyService.resolvePolicy(PROPERTY_ID, Category.ELECTRICAL, Priority.HIGH))
                    .thenReturn(Optional.of(policy));

            // Use a time just before DST spring-forward in New York (2025-03-09 02:00)
            // At 01:00 EST, clocks spring forward to 03:00 EDT
            Instant beforeDst = ZonedDateTime.of(2025, 3, 9, 0, 0, 0, 0, ZoneId.of(TIMEZONE_NY)).toInstant();

            SlaCalculator.SlaTargets result = slaCalculator.calculateTargetsAtTime(
                    PROPERTY_ID, TIMEZONE_NY, Category.ELECTRICAL, Priority.HIGH, beforeDst);

            // With calendar hours: 00:00 + 4h = 04:00 (wall clock), but DST jump means
            // only 3 actual hours pass (00:00 -> 01:00 -> 03:00 -> 04:00)
            ZonedDateTime expectedAck = beforeDst.atZone(ZoneId.of(TIMEZONE_NY)).plusHours(4);
            assertThat(result.acknowledgementDueAt()).isEqualTo(expectedAck.toInstant());
        }
    }

    @Nested
    @DisplayName("computeTargets (direct computation)")
    class ComputeTargetsTests {

        @Test
        @DisplayName("should compute targets without policy lookup")
        void shouldComputeTargetsDirectly() {
            Instant referenceTime = Instant.parse("2025-01-15T14:00:00Z");

            SlaCalculator.SlaTargets result = slaCalculator.computeTargets(
                    TIMEZONE_UTC, 4, 24, referenceTime);

            assertThat(result.acknowledgementDueAt()).isEqualTo(Instant.parse("2025-01-15T18:00:00Z"));
            assertThat(result.resolutionDueAt()).isEqualTo(Instant.parse("2025-01-16T14:00:00Z"));
        }

        @Test
        @DisplayName("should compute correctly for different timezones")
        void shouldComputeForDifferentTimezones() {
            // 2025-01-15T14:00:00Z = 2025-01-15T22:00:00 in KL (UTC+8)
            Instant referenceTime = Instant.parse("2025-01-15T14:00:00Z");

            SlaCalculator.SlaTargets result = slaCalculator.computeTargets(
                    TIMEZONE_KL, 4, 24, referenceTime);

            // In KL: 22:00 + 4h = 02:00 next day (2025-01-16T02:00:00 KL = 2025-01-15T18:00:00Z)
            ZonedDateTime expectedAck = referenceTime.atZone(ZoneId.of(TIMEZONE_KL)).plusHours(4);
            ZonedDateTime expectedRes = referenceTime.atZone(ZoneId.of(TIMEZONE_KL)).plusHours(24);

            assertThat(result.acknowledgementDueAt()).isEqualTo(expectedAck.toInstant());
            assertThat(result.resolutionDueAt()).isEqualTo(expectedRes.toInstant());
        }
    }

    @Nested
    @DisplayName("SlaTargets record")
    class SlaTargetsRecordTests {

        @Test
        @DisplayName("none() should return targets with null values")
        void noneShouldReturnNullTargets() {
            SlaCalculator.SlaTargets none = SlaCalculator.SlaTargets.none();
            assertThat(none.acknowledgementDueAt()).isNull();
            assertThat(none.resolutionDueAt()).isNull();
            assertThat(none.hasTargets()).isFalse();
        }

        @Test
        @DisplayName("hasTargets() should return true when both values are set")
        void hasTargetsShouldReturnTrueWhenBothSet() {
            SlaCalculator.SlaTargets targets = new SlaCalculator.SlaTargets(
                    Instant.now().plusSeconds(3600),
                    Instant.now().plusSeconds(7200));
            assertThat(targets.hasTargets()).isTrue();
        }

        @Test
        @DisplayName("hasTargets() should return false when acknowledgement is null")
        void hasTargetsShouldReturnFalseWhenAckNull() {
            SlaCalculator.SlaTargets targets = new SlaCalculator.SlaTargets(
                    null, Instant.now().plusSeconds(7200));
            assertThat(targets.hasTargets()).isFalse();
        }
    }
}
