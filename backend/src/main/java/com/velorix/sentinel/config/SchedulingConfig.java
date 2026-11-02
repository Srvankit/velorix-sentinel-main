package com.velorix.sentinel.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring task scheduling across the application.
 *
 * <p>Can be disabled in test environments via {@code velorix.scheduling.enabled: false}
 * to prevent background tasks from interfering with test predictability.</p>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "velorix.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
