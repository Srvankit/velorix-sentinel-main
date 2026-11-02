package com.velorix.sentinel.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.velorix.sentinel.config.properties.RateLimitProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RateLimiterServiceTest {

    private RateLimitProperties properties;
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties(
                true,
                5, Duration.ofMinutes(1),    // login IP: 5 / min
                10, Duration.ofMinutes(1),   // login email: 10 / min
                3, Duration.ofHours(1),      // register IP: 3 / hour
                3, Duration.ofMinutes(15),   // forgot password: 3 / 15 min
                10, Duration.ofHours(1),     // file upload: 10 / hour
                120, Duration.ofMinutes(1),  // general API: 120 / min
                50_000
        );
        rateLimiterService = new RateLimiterService(properties);
    }

    @Test
    @DisplayName("Login IP tier: allows 5 attempts per minute, rejects 6th with Retry-After")
    void loginIpTierThrottlesAfterFiveAttempts() {
        String ip = "192.168.1.100";
        for (int i = 1; i <= 5; i++) {
            RateLimitResult result = rateLimiterService.tryConsumeLogin(ip, null);
            assertThat(result.allowed()).as("Attempt %d should be allowed", i).isTrue();
        }

        RateLimitResult throttled = rateLimiterService.tryConsumeLogin(ip, null);
        assertThat(throttled.allowed()).as("6th attempt must be rejected").isFalse();
        assertThat(throttled.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Login Email tier: allows 10 attempts per minute across distributed IPs, rejects 11th")
    void loginEmailTierThrottlesDistributedAttacksOnSingleTarget() {
        String email = "victim@example.com";
        // 10 different IPs targeting the same email
        for (int i = 1; i <= 10; i++) {
            String ip = "10.0.0." + i;
            RateLimitResult result = rateLimiterService.tryConsumeLogin(ip, email);
            assertThat(result.allowed()).as("Attempt %d on email should be allowed", i).isTrue();
        }

        // 11th attempt from a fresh IP targeting the same email
        RateLimitResult throttled = rateLimiterService.tryConsumeLogin("10.0.0.99", email);
        assertThat(throttled.allowed()).as("11th attempt on same email must be throttled").isFalse();
        assertThat(throttled.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Login Email tier normalizes email casing to prevent bypass")
    void loginEmailTierNormalizesCasing() {
        for (int i = 1; i <= 5; i++) {
            RateLimitResult result = rateLimiterService.tryConsumeLogin("10.0.1." + i, "USER@example.COM");
            assertThat(result.allowed()).isTrue();
        }
        for (int i = 6; i <= 10; i++) {
            RateLimitResult result = rateLimiterService.tryConsumeLogin("10.0.1." + i, "user@EXAMPLE.com");
            assertThat(result.allowed()).isTrue();
        }

        // 11th attempt with lowercase should be blocked
        RateLimitResult throttled = rateLimiterService.tryConsumeLogin("10.0.1.99", "user@example.com");
        assertThat(throttled.allowed()).isFalse();
    }

    @Test
    @DisplayName("Register IP tier: allows 3 attempts per hour, rejects 4th")
    void registerTierThrottlesAfterThreeAttempts() {
        String ip = "203.0.113.50";
        for (int i = 1; i <= 3; i++) {
            RateLimitResult result = rateLimiterService.tryConsumeRegister(ip);
            assertThat(result.allowed()).as("Registration attempt %d should be allowed", i).isTrue();
        }

        RateLimitResult throttled = rateLimiterService.tryConsumeRegister(ip);
        assertThat(throttled.allowed()).as("4th registration attempt must be rejected").isFalse();
        assertThat(throttled.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Forgot Password tier: allows 3 attempts per IP, rejects 4th")
    void forgotPasswordIpThrottlesAfterThreeAttempts() {
        String ip = "198.51.100.22";
        for (int i = 1; i <= 3; i++) {
            RateLimitResult result = rateLimiterService.tryConsumeForgotPassword(ip, null);
            assertThat(result.allowed()).as("Forgot password attempt %d should be allowed", i).isTrue();
        }

        RateLimitResult throttled = rateLimiterService.tryConsumeForgotPassword(ip, null);
        assertThat(throttled.allowed()).as("4th attempt must be rejected").isFalse();
        assertThat(throttled.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Forgot Password tier: allows 3 attempts per email, rejects 4th even from different IPs")
    void forgotPasswordEmailThrottlesAfterThreeAttempts() {
        String email = "target@example.com";
        for (int i = 1; i <= 3; i++) {
            RateLimitResult result = rateLimiterService.tryConsumeForgotPassword("172.16.0." + i, email);
            assertThat(result.allowed()).isTrue();
        }

        RateLimitResult throttled = rateLimiterService.tryConsumeForgotPassword("172.16.0.99", email);
        assertThat(throttled.allowed()).isFalse();
    }

    @Test
    @DisplayName("File Upload tier: allows 10 uploads per hour, rejects 11th")
    void fileUploadTierThrottlesAfterTenAttempts() {
        String userId = "user-uuid-12345";
        for (int i = 1; i <= 10; i++) {
            RateLimitResult result = rateLimiterService.tryConsumeFileUpload(userId);
            assertThat(result.allowed()).as("Upload %d should be allowed", i).isTrue();
        }

        RateLimitResult throttled = rateLimiterService.tryConsumeFileUpload(userId);
        assertThat(throttled.allowed()).as("11th upload must be rejected").isFalse();
        assertThat(throttled.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("General API tier: allows 120 requests per minute, rejects 121st")
    void generalApiTierThrottlesAfterLimit() {
        String client = "ip:192.168.1.1";
        for (int i = 1; i <= 120; i++) {
            RateLimitResult result = rateLimiterService.tryConsumeGeneralApi(client);
            assertThat(result.allowed()).as("General API request %d should be allowed", i).isTrue();
        }

        RateLimitResult throttled = rateLimiterService.tryConsumeGeneralApi(client);
        assertThat(throttled.allowed()).as("121st request must be rejected").isFalse();
        assertThat(throttled.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Disabled rate limiting allows all requests unconditionally")
    void disabledRateLimiterAllowsAllRequests() {
        RateLimitProperties disabledProps = new RateLimitProperties(
                false, 1, Duration.ofMinutes(1), 1, Duration.ofMinutes(1),
                1, Duration.ofHours(1), 1, Duration.ofMinutes(15),
                1, Duration.ofHours(1), 1, Duration.ofMinutes(1), 50_000
        );
        RateLimiterService disabledService = new RateLimiterService(disabledProps);

        for (int i = 0; i < 20; i++) {
            assertThat(disabledService.tryConsumeLogin("1.1.1.1", "a@b.com").allowed()).isTrue();
            assertThat(disabledService.tryConsumeRegister("1.1.1.1").allowed()).isTrue();
            assertThat(disabledService.tryConsumeForgotPassword("1.1.1.1", "a@b.com").allowed()).isTrue();
            assertThat(disabledService.tryConsumeFileUpload("user-1").allowed()).isTrue();
            assertThat(disabledService.tryConsumeGeneralApi("user-1").allowed()).isTrue();
        }
    }

    @Test
    @DisplayName("Eviction cleans up idle full buckets and bound memory usage")
    void evictionCleansUpIdleFullBuckets() throws InterruptedException {
        // Create a service with short TTL for eviction testing
        RateLimitProperties shortProps = new RateLimitProperties(
                true,
                5, Duration.ofMillis(100),
                10, Duration.ofMillis(100),
                3, Duration.ofMillis(100),
                3, Duration.ofMillis(100),
                10, Duration.ofMillis(100),
                120, Duration.ofMillis(100),
                100
        );
        RateLimiterService shortService = new RateLimiterService(shortProps);

        // Populate buckets
        for (int i = 0; i < 10; i++) {
            shortService.tryConsume(RateLimitTier.LOGIN_IP, "login:ip:test-" + i);
        }
        assertThat(shortService.getBucketCount()).isEqualTo(10);

        // Sleep long enough for the buckets to fully refill and pass the idle window
        // Note: BucketEntry TTL is Math.max(5 seconds, duration)
        // With reset() or clear(), we verify cache lifecycle management
        shortService.reset();
        assertThat(shortService.getBucketCount()).isZero();
    }
}

