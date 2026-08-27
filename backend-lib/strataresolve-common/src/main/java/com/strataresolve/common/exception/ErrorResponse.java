package com.strataresolve.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Consistent error response format returned by all API endpoints.
 * Internal details and stack traces are never exposed to clients.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String code,
        List<FieldError> details
) {

    /**
     * Represents a single field-level validation error.
     */
    public record FieldError(
            String field,
            String message
    ) {
    }

    public static ErrorResponse of(int status, String error, String message, String code) {
        return new ErrorResponse(Instant.now(), status, error, message, code, null);
    }

    public static ErrorResponse of(int status, String error, String message, String code, List<FieldError> details) {
        return new ErrorResponse(Instant.now(), status, error, message, code, details);
    }
}
