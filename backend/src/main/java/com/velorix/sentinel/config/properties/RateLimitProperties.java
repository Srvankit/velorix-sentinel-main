package com.velorix.sentinel.config.properties;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Rate limiting and DoS defense configuration bound from {@code velorix.ratelimit.*}.
 */
@ConfigurationProperties(prefix = "velorix.ratelimit")
public record RateLimitProperties(
        boolean enabled,
        int loginIpCapacity,
        Duration loginIpDuration,
        int loginEmailCapacity,
        Duration loginEmailDuration,
        int registerIpCapacity,
        Duration registerIpDuration,
        int forgotPasswordCapacity,
        Duration forgotPasswordDuration,
        int fileUploadCapacity,
        Duration fileUploadDuration,
        int generalApiCapacity,
        Duration generalApiDuration,
        int maxBuckets) {

    public RateLimitProperties {
        loginIpCapacity = loginIpCapacity <= 0 ? 5 : loginIpCapacity;
        loginIpDuration = loginIpDuration == null ? Duration.ofMinutes(1) : loginIpDuration;
        loginEmailCapacity = loginEmailCapacity <= 0 ? 10 : loginEmailCapacity;
        loginEmailDuration = loginEmailDuration == null ? Duration.ofMinutes(1) : loginEmailDuration;
        registerIpCapacity = registerIpCapacity <= 0 ? 3 : registerIpCapacity;
        registerIpDuration = registerIpDuration == null ? Duration.ofHours(1) : registerIpDuration;
        forgotPasswordCapacity = forgotPasswordCapacity <= 0 ? 3 : forgotPasswordCapacity;
        forgotPasswordDuration = forgotPasswordDuration == null ? Duration.ofMinutes(15) : forgotPasswordDuration;
        fileUploadCapacity = fileUploadCapacity <= 0 ? 10 : fileUploadCapacity;
        fileUploadDuration = fileUploadDuration == null ? Duration.ofHours(1) : fileUploadDuration;
        generalApiCapacity = generalApiCapacity <= 0 ? 120 : generalApiCapacity;
        generalApiDuration = generalApiDuration == null ? Duration.ofMinutes(1) : generalApiDuration;
        maxBuckets = maxBuckets <= 0 ? 50_000 : maxBuckets;
    }
}

