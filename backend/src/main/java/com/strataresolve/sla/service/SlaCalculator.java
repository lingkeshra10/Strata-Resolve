package com.strataresolve.sla.service;

import com.strataresolve.sla.domain.SlaPolicy;
import com.strataresolve.ticket.domain.Category;
import com.strataresolve.ticket.domain.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Calculates SLA target timestamps (acknowledgement_due_at and resolution_due_at)
 * using calendar-hour calculations based on the property's timezone.
 *
 * <p>Calendar hours means wall-clock hours in the property's timezone — no business-hours
 * exclusion for MVP. For example, if a ticket is created at 14:00 with a 4-hour
 * acknowledgement target, the deadline is 18:00 in the property's local time,
 * regardless of weekends or holidays.
 *
 * <p>If no applicable SLA policy is found for the ticket's category/priority combination,
 * the calculator returns null targets (no SLA enforcement).
 */
@Service
public class SlaCalculator {

    private static final Logger log = LoggerFactory.getLogger(SlaCalculator.class);

    private final SlaPolicyService slaPolicyService;

    public SlaCalculator(SlaPolicyService slaPolicyService) {
        this.slaPolicyService = slaPolicyService;
    }

    /**
     * Calculates SLA target timestamps for a new ticket or when recalculating after
     * a category/priority change.
     *
     * @param propertyId the property the ticket belongs to
     * @param timezone   the property's timezone (e.g., "Asia/Kuala_Lumpur")
     * @param category   the ticket's category
     * @param priority   the ticket's priority
     * @return the calculated SLA targets, with null timestamps if no policy applies
     */
    public SlaTargets calculateTargets(UUID propertyId, String timezone, Category category, Priority priority) {
        return calculateTargetsAtTime(propertyId, timezone, category, priority, Instant.now());
    }

    /**
     * Calculates SLA target timestamps from a specific point in time.
     * This overload is useful for testing and for scenarios where the calculation
     * needs to be based on a specific reference time.
     *
     * @param propertyId the property the ticket belongs to
     * @param timezone   the property's timezone (e.g., "Asia/Kuala_Lumpur")
     * @param category   the ticket's category
     * @param priority   the ticket's priority
     * @param referenceTime the point in time from which to calculate targets
     * @return the calculated SLA targets, with null timestamps if no policy applies
     */
    public SlaTargets calculateTargetsAtTime(UUID propertyId, String timezone, Category category,
                                             Priority priority, Instant referenceTime) {
        Optional<SlaPolicy> policyOpt = slaPolicyService.resolvePolicy(propertyId, category, priority);

        if (policyOpt.isEmpty()) {
            log.debug("No SLA policy found for property={}, category={}, priority={}. " +
                    "SLA targets will not be set.", propertyId, category, priority);
            return SlaTargets.none();
        }

        SlaPolicy policy = policyOpt.get();
        return computeTargets(timezone, policy.getAcknowledgementHours(),
                policy.getResolutionHours(), referenceTime);
    }

    /**
     * Computes SLA targets given explicit hours and timezone, without policy lookup.
     * This is a pure computation method useful for direct testing.
     *
     * @param timezone             the property's timezone
     * @param acknowledgementHours hours until acknowledgement deadline
     * @param resolutionHours      hours until resolution deadline
     * @param referenceTime        the reference point in time
     * @return the calculated SLA targets
     */
    public SlaTargets computeTargets(String timezone, int acknowledgementHours,
                                     int resolutionHours, Instant referenceTime) {
        ZoneId zoneId = ZoneId.of(timezone);
        ZonedDateTime now = referenceTime.atZone(zoneId);

        ZonedDateTime ackDue = now.plusHours(acknowledgementHours);
        ZonedDateTime resDue = now.plusHours(resolutionHours);

        return new SlaTargets(ackDue.toInstant(), resDue.toInstant());
    }

    /**
     * Value record holding calculated SLA target timestamps.
     *
     * <p>Both fields may be null if no applicable SLA policy was found.
     */
    public record SlaTargets(Instant acknowledgementDueAt, Instant resolutionDueAt) {

        /**
         * Returns an SlaTargets instance with null timestamps, indicating no SLA enforcement.
         */
        public static SlaTargets none() {
            return new SlaTargets(null, null);
        }

        /**
         * Returns true if SLA targets were successfully calculated.
         */
        public boolean hasTargets() {
            return acknowledgementDueAt != null && resolutionDueAt != null;
        }
    }
}
