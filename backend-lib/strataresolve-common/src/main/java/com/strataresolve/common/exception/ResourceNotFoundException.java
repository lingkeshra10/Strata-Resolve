package com.strataresolve.common.exception;

/**
 * Thrown when a requested entity does not exist in the accessible scope.
 */
public class ResourceNotFoundException extends BaseBusinessException {

    public ResourceNotFoundException(String message) {
        super(message, "RESOURCE_NOT_FOUND");
    }

    public ResourceNotFoundException(String entityType, Object id) {
        super(String.format("%s not found with id: %s", entityType, id), "RESOURCE_NOT_FOUND");
    }
}
