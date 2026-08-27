package com.strataresolve.common.exception;

/**
 * Thrown when a request is missing or has an invalid authentication token.
 */
public class AuthenticationRequiredException extends BaseBusinessException {

    public AuthenticationRequiredException(String message) {
        super(message, "AUTHENTICATION_REQUIRED");
    }

    public AuthenticationRequiredException() {
        super("Authentication is required to access this resource", "AUTHENTICATION_REQUIRED");
    }
}
