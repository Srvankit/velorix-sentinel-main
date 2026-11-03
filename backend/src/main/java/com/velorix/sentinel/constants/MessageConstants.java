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
    public static final String METHOD_NOT_ALLOWED = "The requested HTTP method is not supported for this endpoint";
    public static final String UNSUPPORTED_MEDIA_TYPE = "The request content type is not supported";

    private MessageConstants() {
        throw new AssertionError("No instances allowed");
    }
}
