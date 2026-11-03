package com.velorix.sentinel.health;

import com.velorix.sentinel.config.InspectionExecutorConfig;
import com.velorix.sentinel.config.properties.InspectionProperties;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Custom Actuator HealthIndicator monitoring the Deep Packet Inspection (DPI) worker pool.
 *
 * <p>Reports active worker count, queue depth, queue capacity, and pool sizing from
 * the bounded {@code inspectionExecutor}. Rejection or full queue saturation is accurately
 * reflected as an unhealthy state.</p>
 */
@Component("inspectionQueue")
public class InspectionQueueHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(InspectionQueueHealthIndicator.class);

    private final ThreadPoolTaskExecutor executor;
    private final InspectionProperties properties;

    public InspectionQueueHealthIndicator(
            @Qualifier(InspectionExecutorConfig.EXECUTOR_BEAN) ThreadPoolTaskExecutor executor,
            InspectionProperties properties) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public Health health() {
        try {
            int activeWorkers = executor.getActiveCount();
            int poolSize = executor.getPoolSize();
            int maxPoolSize = executor.getMaxPoolSize();
            int queueCapacity = properties.queueCapacity();
            int queueDepth = (executor.getThreadPoolExecutor() != null)
                    ? executor.getThreadPoolExecutor().getQueue().size()
                    : 0;

            double saturationRatio = queueCapacity > 0 ? (double) queueDepth / queueCapacity : 0.0;
            boolean isSaturated = queueCapacity > 0 && queueDepth >= queueCapacity;

            Health.Builder builder = isSaturated ? Health.down() : Health.up();

            builder.withDetail("activeWorkers", activeWorkers)
                    .withDetail("poolSize", poolSize)
                    .withDetail("maxPoolSize", maxPoolSize)
                    .withDetail("queueDepth", queueDepth)
                    .withDetail("queueCapacity", queueCapacity)
                    .withDetail("saturationRatio", String.format(Locale.ROOT, "%.2f", saturationRatio))
                    .withDetail("status", isSaturated ? "SATURATED" : "OPTIMAL");

            if (isSaturated) {
                builder.withDetail("error", "Inspection task queue is fully saturated; incoming runs will be rejected");
            }

            return builder.build();
        } catch (Exception ex) {
            log.error("Failed to inspect inspection queue health: {}", ex.getMessage());
            return Health.down(ex).build();
        }
    }
}
