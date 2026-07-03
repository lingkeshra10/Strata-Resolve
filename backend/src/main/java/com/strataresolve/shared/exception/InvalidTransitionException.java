package com.strataresolve.shared.exception;

/**
 * Thrown when a ticket status transition is not allowed by the workflow policy.
 */
public class InvalidTransitionException extends BaseBusinessException {

    public InvalidTransitionException(String message) {
        super(message, "INVALID_TRANSITION");
    }

    public InvalidTransitionException(String fromStatus, String toStatus) {
        super(String.format("Transition from %s to %s is not allowed", fromStatus, toStatus), "INVALID_TRANSITION");
    }
}
