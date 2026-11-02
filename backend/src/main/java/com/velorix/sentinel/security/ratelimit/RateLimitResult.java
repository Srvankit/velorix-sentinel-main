package com.velorix.sentinel.security.ratelimit;

/**
 * Outcome of a rate limiting probe check.
 *
 * @param allowed whether the request is allowed to proceed
 * @param retryAfterSeconds number of seconds the client must wait before retrying (minimum 1 when rejected)
 */
public record RateLimitResult(boolean allowed, long retryAfterSeconds) {

    public static RateLimitResult allow() {
        return new RateLimitResult(true, 0);
    }

    public static RateLimitResult reject(long retryAfterSeconds) {
        return new RateLimitResult(false, Math.max(1, retryAfterSeconds));
    }
}

