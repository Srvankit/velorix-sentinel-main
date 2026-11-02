package com.velorix.sentinel.security.ratelimit;

import com.velorix.sentinel.config.properties.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * In-memory token bucket rate limiting service powered by Bucket4j.
 *
 * <p>Provides multi-tier rate limiting for sensitive endpoints (login, registration,
 * password recovery, capture upload) and general API protection against CPU-DoS.</p>
 *
 * <p>Implements bounded cache size and periodic eviction of idle full buckets to prevent
 * memory exhaustion from distributed attacks.</p>
 */
@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private final RateLimitProperties properties;
    private final ConcurrentMap<String, BucketEntry> buckets = new ConcurrentHashMap<>();

    public RateLimiterService(RateLimitProperties properties) {
        this.properties = properties;
    }

    /**
     * Evaluates login rate limits.
     * Enforces 5 attempts/minute per IP and 10 attempts/minute per target email.
     */
    public RateLimitResult tryConsumeLogin(String ip, String email) {
        if (!properties.enabled()) {
            return RateLimitResult.allow();
        }

        // 1. Check IP limit first
        String ipKey = ip != null ? ip.trim() : "unknown";
        RateLimitResult ipResult = tryConsume(RateLimitTier.LOGIN_IP, "login:ip:" + ipKey);
        if (!ipResult.allowed()) {
            log.warn("Rate limit exceeded for login by IP: {}", ipKey);
            return ipResult;
        }

        // 2. Check email limit if email is provided
        if (email != null && !email.isBlank()) {
            String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
            RateLimitResult emailResult = tryConsume(RateLimitTier.LOGIN_EMAIL, "login:email:" + normalizedEmail);
            if (!emailResult.allowed()) {
                log.warn("Rate limit exceeded for login by target email: {}", normalizedEmail);
                return emailResult;
            }
        }

        return RateLimitResult.allow();
    }

    /**
     * Evaluates registration rate limits.
     * Enforces 3 attempts/hour per IP.
     */
    public RateLimitResult tryConsumeRegister(String ip) {
        if (!properties.enabled()) {
            return RateLimitResult.allow();
        }
        String ipKey = ip != null ? ip.trim() : "unknown";
        RateLimitResult result = tryConsume(RateLimitTier.REGISTER_IP, "register:ip:" + ipKey);
        if (!result.allowed()) {
            log.warn("Rate limit exceeded for registration by IP: {}", ipKey);
        }
        return result;
    }

    /**
     * Evaluates password recovery rate limits.
     * Enforces 3 requests/15 minutes per IP and 3 requests/15 minutes per email.
     */
    public RateLimitResult tryConsumeForgotPassword(String ip, String email) {
        if (!properties.enabled()) {
            return RateLimitResult.allow();
        }

        String ipKey = ip != null ? ip.trim() : "unknown";
        RateLimitResult ipResult = tryConsume(RateLimitTier.FORGOT_PASSWORD_IP, "forgot:ip:" + ipKey);
        if (!ipResult.allowed()) {
            log.warn("Rate limit exceeded for forgot-password by IP: {}", ipKey);
            return ipResult;
        }

        if (email != null && !email.isBlank()) {
            String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
            RateLimitResult emailResult = tryConsume(RateLimitTier.FORGOT_PASSWORD_EMAIL, "forgot:email:" + normalizedEmail);
            if (!emailResult.allowed()) {
                log.warn("Rate limit exceeded for forgot-password by email: {}", normalizedEmail);
                return emailResult;
            }
        }

        return RateLimitResult.allow();
    }

    /**
     * Evaluates file upload rate limits.
     * Enforces 10 uploads/hour per authenticated user (or per IP if anonymous).
     */
    public RateLimitResult tryConsumeFileUpload(String userOrIp) {
        if (!properties.enabled()) {
            return RateLimitResult.allow();
        }
        String key = userOrIp != null ? userOrIp.trim() : "unknown";
        RateLimitResult result = tryConsume(RateLimitTier.FILE_UPLOAD, "upload:" + key);
        if (!result.allowed()) {
            log.warn("Rate limit exceeded for file upload by {}: retry in {}s", key, result.retryAfterSeconds());
        }
        return result;
    }

    /**
     * Evaluates general API rate limits.
     * Enforces 120 requests/minute per user (or per IP if anonymous).
     */
    public RateLimitResult tryConsumeGeneralApi(String userOrIp) {
        if (!properties.enabled()) {
            return RateLimitResult.allow();
        }
        String key = userOrIp != null ? userOrIp.trim() : "unknown";
        RateLimitResult result = tryConsume(RateLimitTier.GENERAL_API, "general:" + key);
        if (!result.allowed()) {
            log.warn("Rate limit exceeded for general API by {}: retry in {}s", key, result.retryAfterSeconds());
        }
        return result;
    }

    /**
     * Evaluates consumption against a specific tier bucket.
     */
    public RateLimitResult tryConsume(RateLimitTier tier, String bucketKey) {
        if (!properties.enabled()) {
            return RateLimitResult.allow();
        }

        if (buckets.size() >= properties.maxBuckets()) {
            cleanupExpiredBuckets();
            // If still exceeding max capacity after cleanup, protect against memory exhaustion
            if (buckets.size() >= properties.maxBuckets() && !buckets.containsKey(bucketKey)) {
                log.error("Rate limiter bucket capacity exceeded ({} buckets). Rejecting request to prevent OOM.", properties.maxBuckets());
                return RateLimitResult.reject(60);
            }
        }

        BucketEntry entry = buckets.computeIfAbsent(bucketKey, k -> createBucketEntry(tier));
        entry.touch();

        ConsumptionProbe probe = entry.bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return RateLimitResult.allow();
        }

        long retryAfterNanos = probe.getNanosToWaitForRefill();
        long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(retryAfterNanos));
        return RateLimitResult.reject(retryAfterSeconds);
    }

    private BucketEntry createBucketEntry(RateLimitTier tier) {
        long capacity;
        Duration duration;
        switch (tier) {
            case LOGIN_IP -> {
                capacity = properties.loginIpCapacity();
                duration = properties.loginIpDuration();
            }
            case LOGIN_EMAIL -> {
                capacity = properties.loginEmailCapacity();
                duration = properties.loginEmailDuration();
            }
            case REGISTER_IP -> {
                capacity = properties.registerIpCapacity();
                duration = properties.registerIpDuration();
            }
            case FORGOT_PASSWORD_IP -> {
                capacity = properties.forgotPasswordCapacity();
                duration = properties.forgotPasswordDuration();
            }
            case FORGOT_PASSWORD_EMAIL -> {
                capacity = properties.forgotPasswordCapacity();
                duration = properties.forgotPasswordDuration();
            }
            case FILE_UPLOAD -> {
                capacity = properties.fileUploadCapacity();
                duration = properties.fileUploadDuration();
            }
            case GENERAL_API -> {
                capacity = properties.generalApiCapacity();
                duration = properties.generalApiDuration();
            }
            default -> throw new IllegalArgumentException("Unsupported rate limit tier: " + tier);
        }

        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, duration)
                .build();
        Bucket bucket = Bucket.builder()
                .addLimit(limit)
                .build();
        return new BucketEntry(bucket, capacity, duration);
    }

    /**
     * Periodic scheduled eviction of idle full buckets to bound memory usage.
     */
    @Scheduled(fixedDelay = 60000)
    public void scheduledEviction() {
        if (properties.enabled()) {
            int evicted = cleanupExpiredBuckets();
            if (evicted > 0) {
                log.debug("Evicted {} idle rate limit buckets. Remaining: {}", evicted, buckets.size());
            }
        }
    }

    /**
     * Evicts buckets that have been idle and have fully refilled to maximum capacity.
     *
     * @return count of evicted buckets
     */
    public int cleanupExpiredBuckets() {
        long now = System.nanoTime();
        int removed = 0;
        Iterator<Map.Entry<String, BucketEntry>> iterator = buckets.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, BucketEntry> entry = iterator.next();
            if (entry.getValue().isIdleAndFull(now)) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    public int getBucketCount() {
        return buckets.size();
    }

    public void reset() {
        buckets.clear();
    }

    private static class BucketEntry {
        final Bucket bucket;
        final long capacity;
        final long ttlNanos;
        volatile long lastAccessNanos;

        BucketEntry(Bucket bucket, long capacity, Duration duration) {
            this.bucket = bucket;
            this.capacity = capacity;
            this.ttlNanos = Math.max(TimeUnit.SECONDS.toNanos(5), duration.toNanos());
            this.lastAccessNanos = System.nanoTime();
        }

        void touch() {
            this.lastAccessNanos = System.nanoTime();
        }

        boolean isIdleAndFull(long nowNanos) {
            return (nowNanos - lastAccessNanos >= ttlNanos) && (bucket.getAvailableTokens() >= capacity);
        }
    }
}

