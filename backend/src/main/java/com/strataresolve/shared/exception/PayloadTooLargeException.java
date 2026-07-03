package com.strataresolve.shared.exception;

/**
 * Thrown when a request or file exceeds the configured size limit.
 */
public class PayloadTooLargeException extends BaseBusinessException {

    public PayloadTooLargeException(String message) {
        super(message, "PAYLOAD_TOO_LARGE");
    }
}
