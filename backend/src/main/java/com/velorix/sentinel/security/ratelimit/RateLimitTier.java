package com.velorix.sentinel.security.ratelimit;

/**
 * Functional rate limiting tiers defining protection boundaries.
 */
public enum RateLimitTier {
    LOGIN_IP,
    LOGIN_EMAIL,
    REGISTER_IP,
    FORGOT_PASSWORD_IP,
    FORGOT_PASSWORD_EMAIL,
    FILE_UPLOAD,
    GENERAL_API
}

