package com.strataresolve.shared.exception;

/**
 * Thrown when an access token has expired.
 */
public class TokenExpiredException extends BaseBusinessException {

    public TokenExpiredException(String message) {
        super(message, "TOKEN_EXPIRED");
    }

    public TokenExpiredException() {
        super("Access token has expired", "TOKEN_EXPIRED");
    }
}
