package com.strataresolve.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Global exception handler that maps all exceptions to safe, consistent HTTP responses.
 * Stack traces and internal details are logged server-side but never exposed to clients.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // --- Business Exceptions ---

    @ExceptionHandler(InvalidTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransition(InvalidTransitionException ex) {
        log.warn("Invalid transition: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), ex.getErrorCode());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), ex.getErrorCode());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(RateLimitExceededException ex) {
        log.warn("Rate limit exceeded: {}", ex.getMessage());
        return buildResponse(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), ex.getErrorCode());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(DuplicateResourceException ex) {
        log.warn("Duplicate resource: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), ex.getErrorCode());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage(), ex.getErrorCode());
    }

    @ExceptionHandler(AuthenticationRequiredException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationRequired(AuthenticationRequiredException ex) {
        log.warn("Authentication required: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), ex.getErrorCode());
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<ErrorResponse> handleTokenExpired(TokenExpiredException ex) {
        log.warn("Token expired: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), ex.getErrorCode());
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    public ResponseEntity<ErrorResponse> handlePayloadTooLarge(PayloadTooLargeException ex) {
        log.warn("Payload too large: {}", ex.getMessage());
        return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE, ex.getMessage(), ex.getErrorCode());
    }

    @ExceptionHandler(UnsupportedFileTypeException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedFileType(UnsupportedFileTypeException ex) {
        log.warn("Unsupported file type: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage(), ex.getErrorCode());
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRuleViolation(BusinessRuleViolationException ex) {
        log.warn("Business rule violation: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), ex.getErrorCode());
    }

    // --- Spring Framework Exceptions ---

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        log.warn("Validation failed: {}", ex.getMessage());
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Validation failed",
                "VALIDATION_FAILED",
                fieldErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "Malformed request body", "VALIDATION_FAILED");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("Missing request parameter: {}", ex.getParameterName());
        return buildResponse(HttpStatus.BAD_REQUEST,
                String.format("Required parameter '%s' is missing", ex.getParameterName()),
                "VALIDATION_FAILED");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not supported: {}", ex.getMethod());
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED,
                String.format("HTTP method '%s' is not supported for this endpoint", ex.getMethod()),
                "VALIDATION_FAILED");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        log.warn("Media type not supported: {}", ex.getContentType());
        return buildResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Unsupported media type",
                "UNSUPPORTED_FILE_TYPE");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("Upload size exceeded: {}", ex.getMessage());
        return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE,
                "File upload exceeds the maximum allowed size",
                "PAYLOAD_TOO_LARGE");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("No resource found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "The requested resource was not found", "RESOURCE_NOT_FOUND");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Bad credentials attempt");
        return buildResponse(HttpStatus.UNAUTHORIZED, "Invalid credentials", "AUTHENTICATION_REQUIRED");
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleSpringAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        log.warn("Spring Security access denied: {}", ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "You do not have permission to perform this action", "ACCESS_DENIED");
    }

    // --- Catch-all for unexpected errors ---

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.",
                "INTERNAL_ERROR");
    }

    // --- Helper ---

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message, String code) {
        ErrorResponse response = ErrorResponse.of(status.value(), status.getReasonPhrase(), message, code);
        return ResponseEntity.status(status).body(response);
    }
}
