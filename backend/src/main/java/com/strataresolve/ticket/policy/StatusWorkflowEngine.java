package com.strataresolve.ticket.policy;

import com.strataresolve.shared.exception.InvalidTransitionException;
import com.strataresolve.ticket.domain.TicketStatus;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Enforces the ticket status workflow by validating transitions against
 * the allowed transition policy map. Invalid transitions are rejected
 * with descriptive error messages.
 *
 * <p>The transition policy is defined as an adjacency map where each status
 * maps to its set of allowed target statuses. Terminal statuses (REJECTED, CANCELLED)
 * have no allowed transitions.
 */
@Component
public class StatusWorkflowEngine {

    private static final Map<TicketStatus, Set<TicketStatus>> TRANSITION_POLICY;

    static {
        EnumMap<TicketStatus, Set<TicketStatus>> policy = new EnumMap<>(TicketStatus.class);

        policy.put(TicketStatus.SUBMITTED, EnumSet.of(
                TicketStatus.ACKNOWLEDGED, TicketStatus.REJECTED, TicketStatus.CANCELLED));

        policy.put(TicketStatus.ACKNOWLEDGED, EnumSet.of(
                TicketStatus.UNDER_REVIEW, TicketStatus.ASSIGNED, TicketStatus.REJECTED, TicketStatus.CANCELLED));

        policy.put(TicketStatus.UNDER_REVIEW, EnumSet.of(
                TicketStatus.ASSIGNED, TicketStatus.REJECTED, TicketStatus.CANCELLED));

        policy.put(TicketStatus.ASSIGNED, EnumSet.of(
                TicketStatus.IN_PROGRESS, TicketStatus.AWAITING_VENDOR, TicketStatus.CANCELLED));

        policy.put(TicketStatus.IN_PROGRESS, EnumSet.of(
                TicketStatus.AWAITING_VENDOR, TicketStatus.AWAITING_RESIDENT,
                TicketStatus.READY_FOR_VERIFICATION, TicketStatus.CANCELLED));

        policy.put(TicketStatus.AWAITING_VENDOR, EnumSet.of(
                TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED));

        policy.put(TicketStatus.AWAITING_RESIDENT, EnumSet.of(
                TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED));

        policy.put(TicketStatus.READY_FOR_VERIFICATION, EnumSet.of(
                TicketStatus.RESOLVED, TicketStatus.IN_PROGRESS));

        policy.put(TicketStatus.RESOLVED, EnumSet.of(
                TicketStatus.CLOSED, TicketStatus.REOPENED));

        policy.put(TicketStatus.CLOSED, EnumSet.of(
                TicketStatus.REOPENED));

        policy.put(TicketStatus.REOPENED, EnumSet.of(
                TicketStatus.ACKNOWLEDGED, TicketStatus.ASSIGNED, TicketStatus.CANCELLED));

        // Terminal statuses — no transitions allowed
        policy.put(TicketStatus.REJECTED, Collections.emptySet());
        policy.put(TicketStatus.CANCELLED, Collections.emptySet());

        TRANSITION_POLICY = Collections.unmodifiableMap(policy);
    }

    /**
     * Validates whether a transition from the current status to the target status is allowed.
     *
     * @param currentStatus the ticket's current status
     * @param targetStatus  the desired target status
     * @throws InvalidTransitionException if the transition is not permitted
     */
    public void validateTransition(TicketStatus currentStatus, TicketStatus targetStatus) {
        if (currentStatus == null) {
            throw new InvalidTransitionException("Current status cannot be null");
        }
        if (targetStatus == null) {
            throw new InvalidTransitionException("Target status cannot be null");
        }
        if (currentStatus == targetStatus) {
            throw new InvalidTransitionException(
                    String.format("Transition from %s to %s is not allowed: ticket is already in status %s",
                            currentStatus, targetStatus, currentStatus));
        }

        Set<TicketStatus> allowedTargets = TRANSITION_POLICY.get(currentStatus);
        if (allowedTargets == null || !allowedTargets.contains(targetStatus)) {
            throw new InvalidTransitionException(currentStatus.name(), targetStatus.name());
        }
    }

    /**
     * Checks whether a transition from the current status to the target status is allowed
     * without throwing an exception.
     *
     * @param currentStatus the ticket's current status
     * @param targetStatus  the desired target status
     * @return true if the transition is allowed, false otherwise
     */
    public boolean isTransitionAllowed(TicketStatus currentStatus, TicketStatus targetStatus) {
        if (currentStatus == null || targetStatus == null || currentStatus == targetStatus) {
            return false;
        }
        Set<TicketStatus> allowedTargets = TRANSITION_POLICY.get(currentStatus);
        return allowedTargets != null && allowedTargets.contains(targetStatus);
    }

    /**
     * Returns the set of statuses a ticket can transition to from the given current status.
     *
     * @param currentStatus the ticket's current status
     * @return unmodifiable set of allowed target statuses (empty for terminal statuses)
     */
    public Set<TicketStatus> getAllowedTransitions(TicketStatus currentStatus) {
        if (currentStatus == null) {
            return Collections.emptySet();
        }
        Set<TicketStatus> allowed = TRANSITION_POLICY.get(currentStatus);
        return allowed != null ? Collections.unmodifiableSet(allowed) : Collections.emptySet();
    }

    /**
     * Returns true if the given status is a terminal status (no further transitions possible).
     *
     * @param status the status to check
     * @return true if terminal
     */
    public boolean isTerminal(TicketStatus status) {
        if (status == null) {
            return false;
        }
        Set<TicketStatus> allowed = TRANSITION_POLICY.get(status);
        return allowed != null && allowed.isEmpty();
    }
}
