package com.strataresolve.shared.exception;

/**
 * Thrown when a user exceeds the configured rate limit for an operation.
 */
public class RateLimitExceededException extends BaseBusinessException {

    public RateLimitExceededException(String message) {
        super(message, "RATE_LIMITED");
    }
}
