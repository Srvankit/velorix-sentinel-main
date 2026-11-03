package com.velorix.sentinel.config;

import com.velorix.sentinel.config.properties.InspectionProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Dedicated pool for deep packet inspection runs.
 *
 * <p>Inspection is CPU and IO heavy and can take minutes on a large capture. Giving it
 * its own bounded pool keeps a burst of uploads from starving the request threads, and
 * the bounded queue means an overloaded server rejects work loudly instead of piling up
 * unbounded state until it dies.</p>
 */
@Configuration
public class InspectionExecutorConfig {

    public static final String EXECUTOR_BEAN = "inspectionExecutor";

    private static final Logger log = LoggerFactory.getLogger(InspectionExecutorConfig.class);
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 60;

    @Bean(name = EXECUTOR_BEAN)
    public ThreadPoolTaskExecutor inspectionExecutor(
            InspectionProperties properties,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.concurrency());
        executor.setMaxPoolSize(properties.concurrency());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("dpi-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(SHUTDOWN_TIMEOUT_SECONDS);
        executor.setRejectedExecutionHandler(abortAndLog());
        executor.initialize();
        log.info("Inspection executor ready ({} workers, queue capacity {})",
                properties.concurrency(), properties.queueCapacity());

        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry != null) {
            registerMetrics(executor, properties, meterRegistry);
        }

        return executor;
    }

    private void registerMetrics(ThreadPoolTaskExecutor executor, InspectionProperties properties, MeterRegistry registry) {
        Gauge.builder("inspection.executor.active", executor, ThreadPoolTaskExecutor::getActiveCount)
                .description("Current number of active threads executing inspection tasks")
                .register(registry);

        Gauge.builder("inspection.executor.pool.size", executor, ThreadPoolTaskExecutor::getPoolSize)
                .description("Current number of worker threads in the inspection executor pool")
                .register(registry);

        Gauge.builder("inspection.executor.queue.depth", executor, e -> {
                    var pool = e.getThreadPoolExecutor();
                    return pool != null ? pool.getQueue().size() : 0;
                })
                .description("Current number of tasks queued waiting for inspection execution")
                .register(registry);

        Gauge.builder("inspection.executor.queue.capacity", properties, InspectionProperties::queueCapacity)
                .description("Maximum capacity of the inspection task queue")
                .register(registry);
    }

    /**
     * Rejection is a capacity signal, not a bug: it is logged here and rethrown so the
     * submitting service can mark the run failed instead of leaving it queued forever.
     */
    private RejectedExecutionHandler abortAndLog() {
        return (runnable, pool) -> {
            log.error("Inspection queue is saturated ({} tasks waiting); rejecting the run", pool.getQueue().size());
            throw new RejectedExecutionException("The inspection queue is full; try again shortly");
        };
    }
}
