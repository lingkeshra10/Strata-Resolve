package com.strataresolve.common.exception;

/**
 * Thrown when a uniqueness constraint is violated (e.g., duplicate email, duplicate property code).
 */
public class DuplicateResourceException extends BaseBusinessException {

    public DuplicateResourceException(String message) {
        super(message, "DUPLICATE_RESOURCE");
    }
}
