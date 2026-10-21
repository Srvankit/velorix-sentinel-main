package com.velorix.sentinel.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring task scheduling when {@code velorix.retention.enabled} is true.
 *
 * <p>In test profiles, scheduling is disabled via {@code velorix.retention.enabled: false}
 * so that background tasks do not interfere with test predictability.</p>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "velorix.retention.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
