package com.strataresolve.shared.exception;

/**
 * Thrown when a user lacks the required role or membership for an operation.
 */
public class AccessDeniedException extends BaseBusinessException {

    public AccessDeniedException(String message) {
        super(message, "ACCESS_DENIED");
    }

    public AccessDeniedException() {
        super("You do not have permission to perform this action", "ACCESS_DENIED");
    }
}
