package com.velorix.sentinel.constants;

/**
 * Reusable, user-facing messages returned by the API.
 */
public final class MessageConstants {

    public static final String GENERIC_SUCCESS = "Request completed successfully";
    public static final String VALIDATION_FAILED = "Validation failed for one or more fields";
    public static final String UNAUTHORIZED = "Authentication is required to access this resource";
    public static final String FORBIDDEN = "You do not have permission to access this resource";
    public static final String RESOURCE_NOT_FOUND = "The requested resource was not found";
    public static final String INTERNAL_ERROR = "An unexpected error occurred. Please try again later";
    public static final String NOT_IMPLEMENTED = "This capability is not available yet";
    public static final String MALFORMED_REQUEST = "The request body is malformed or unreadable";

    private MessageConstants() {
        throw new AssertionError("No instances allowed");
    }
}
