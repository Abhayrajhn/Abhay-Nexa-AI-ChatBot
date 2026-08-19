package com.abhay.exception;

/**
 * Custom exception thrown when a requested resource is not found.
 *
 * This will be used when:
 * - Conversation with given ID doesn't exist
 * - Message with given ID doesn't exist
 *
 * Spring will automatically convert this to HTTP 404 when we add proper exception handling.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s not found with %s: '%s'", resourceName, fieldName, fieldValue));
    }

    // Examples of usage:
    // throw new ResourceNotFoundException("Conversation not found with id: " + id);
    // throw new ResourceNotFoundException("Conversation", "id", id);
}
