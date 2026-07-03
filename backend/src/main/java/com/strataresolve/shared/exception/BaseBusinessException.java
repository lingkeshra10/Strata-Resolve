package com.strataresolve.shared.exception;

/**
 * Base class for all business exceptions in the StrataResolve platform.
 * Carries an application-specific error code used in the error response.
 */
public abstract class BaseBusinessException extends RuntimeException {

    private final String errorCode;

    protected BaseBusinessException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    protected BaseBusinessException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
