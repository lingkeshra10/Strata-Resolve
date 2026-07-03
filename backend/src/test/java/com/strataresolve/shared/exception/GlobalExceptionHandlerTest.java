package com.strataresolve.shared.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GlobalExceptionHandler verifying:
 * - Correct HTTP status codes are returned
 * - Correct error codes are mapped
 * - Stack traces and internal details are never exposed
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest("GET", "/api/test");
    }

    @Test
    void handleInvalidTransition_returns400WithCorrectCode() {
        var ex = new InvalidTransitionException("SUBMITTED", "CLOSED");
        ResponseEntity<ErrorResponse> response = handler.handleInvalidTransition(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertErrorResponse(response.getBody(), 400, "INVALID_TRANSITION");
        assertNoInternalDetails(response.getBody());
    }

    @Test
    void handleResourceNotFound_returns404WithCorrectCode() {
        var ex = new ResourceNotFoundException("Ticket", "abc-123");
        ResponseEntity<ErrorResponse> response = handler.handleResourceNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertErrorResponse(response.getBody(), 404, "RESOURCE_NOT_FOUND");
        assertThat(response.getBody().message()).contains("Ticket");
        assertNoInternalDetails(response.getBody());
    }

    @Test
    void handleRateLimitExceeded_returns429WithCorrectCode() {
        var ex = new RateLimitExceededException("You have exceeded the maximum of 5 submissions per hour");
        ResponseEntity<ErrorResponse> response = handler.handleRateLimitExceeded(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertErrorResponse(response.getBody(), 429, "RATE_LIMITED");
        assertNoInternalDetails(response.getBody());
    }

    @Test
    void handleDuplicateResource_returns409WithCorrectCode() {
        var ex = new DuplicateResourceException("A property with code 'PROP-01' already exists");
        ResponseEntity<ErrorResponse> response = handler.handleDuplicateResource(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertErrorResponse(response.getBody(), 409, "DUPLICATE_RESOURCE");
        assertNoInternalDetails(response.getBody());
    }

    @Test
    void handleAccessDenied_returns403WithCorrectCode() {
        var ex = new AccessDeniedException();
        ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertErrorResponse(response.getBody(), 403, "ACCESS_DENIED");
        assertNoInternalDetails(response.getBody());
    }

    @Test
    void handleAuthenticationRequired_returns401WithCorrectCode() {
        var ex = new AuthenticationRequiredException();
        ResponseEntity<ErrorResponse> response = handler.handleAuthenticationRequired(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertErrorResponse(response.getBody(), 401, "AUTHENTICATION_REQUIRED");
        assertNoInternalDetails(response.getBody());
    }

    @Test
    void handleTokenExpired_returns401WithCorrectCode() {
        var ex = new TokenExpiredException();
        ResponseEntity<ErrorResponse> response = handler.handleTokenExpired(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertErrorResponse(response.getBody(), 401, "TOKEN_EXPIRED");
        assertNoInternalDetails(response.getBody());
    }

    @Test
    void handlePayloadTooLarge_returns413WithCorrectCode() {
        var ex = new PayloadTooLargeException("File exceeds 10MB limit");
        ResponseEntity<ErrorResponse> response = handler.handlePayloadTooLarge(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertErrorResponse(response.getBody(), 413, "PAYLOAD_TOO_LARGE");
        assertNoInternalDetails(response.getBody());
    }

    @Test
    void handleUnsupportedFileType_returns415WithCorrectCode() {
        var ex = new UnsupportedFileTypeException("File type 'text/plain' is not allowed");
        ResponseEntity<ErrorResponse> response = handler.handleUnsupportedFileType(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertErrorResponse(response.getBody(), 415, "UNSUPPORTED_FILE_TYPE");
        assertNoInternalDetails(response.getBody());
    }

    @Test
    void handleBusinessRuleViolation_returns422WithCorrectCode() {
        var ex = new BusinessRuleViolationException("Cannot reopen ticket outside time window");
        ResponseEntity<ErrorResponse> response = handler.handleBusinessRuleViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertErrorResponse(response.getBody(), 422, "BUSINESS_RULE_VIOLATION");
        assertNoInternalDetails(response.getBody());
    }

    @Test
    void handleUnexpectedException_returns500AndHidesInternalDetails() {
        var ex = new NullPointerException("com.strataresolve.ticket.service.TicketService.submit(TicketService.java:42)");
        ResponseEntity<ErrorResponse> response = handler.handleUnexpectedException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertErrorResponse(response.getBody(), 500, "INTERNAL_ERROR");
        // The generic message must be used, NOT the original exception message
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred. Please try again later.");
        assertThat(response.getBody().message()).doesNotContain("NullPointerException");
        assertThat(response.getBody().message()).doesNotContain("TicketService");
        assertThat(response.getBody().message()).doesNotContain(".java");
        assertNoInternalDetails(response.getBody());
    }

    @Test
    void handleUnexpectedException_neverExposesStackTrace() {
        var ex = new RuntimeException("SQL error: relation 'tickets' does not exist");
        try {
            throw ex;
        } catch (RuntimeException caught) {
            ResponseEntity<ErrorResponse> response = handler.handleUnexpectedException(caught, request);
            String responseBody = response.getBody().toString();
            assertThat(responseBody).doesNotContain("SQL");
            assertThat(responseBody).doesNotContain("relation");
            assertThat(responseBody).doesNotContain("StackTrace");
            assertThat(responseBody).doesNotContain(".java:");
        }
    }

    @Test
    void errorResponse_hasTimestamp() {
        var ex = new ResourceNotFoundException("Property", "test-id");
        ResponseEntity<ErrorResponse> response = handler.handleResourceNotFound(ex);

        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    void errorResponse_detailsIsNullForNonValidationErrors() {
        var ex = new ResourceNotFoundException("Property", "test-id");
        ResponseEntity<ErrorResponse> response = handler.handleResourceNotFound(ex);

        assertThat(response.getBody().details()).isNull();
    }

    // --- Helpers ---

    private void assertErrorResponse(ErrorResponse body, int expectedStatus, String expectedCode) {
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(expectedStatus);
        assertThat(body.code()).isEqualTo(expectedCode);
        assertThat(body.error()).isNotBlank();
        assertThat(body.message()).isNotBlank();
        assertThat(body.timestamp()).isNotNull();
    }

    private void assertNoInternalDetails(ErrorResponse body) {
        String fullResponse = body.toString();
        assertThat(fullResponse).doesNotContain("Exception");
        assertThat(fullResponse).doesNotContain(".java:");
        assertThat(fullResponse).doesNotContain("at com.");
        assertThat(fullResponse).doesNotContain("stackTrace");
    }
}
