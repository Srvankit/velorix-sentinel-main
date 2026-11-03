package com.velorix.sentinel.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.velorix.sentinel.config.properties.InspectionProperties;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@DisplayName("Task 6.5 — InspectionQueueHealthIndicator Unit Test")
class InspectionQueueHealthIndicatorUnitTest {

    @Test
    @DisplayName("Idle executor pool returns UP with zero active workers and empty queue")
    void idlePoolReturnsUp() {
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        ThreadPoolExecutor threadPoolExecutor = mock(ThreadPoolExecutor.class);
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(50);

        when(executor.getActiveCount()).thenReturn(0);
        when(executor.getPoolSize()).thenReturn(2);
        when(executor.getMaxPoolSize()).thenReturn(2);
        when(executor.getThreadPoolExecutor()).thenReturn(threadPoolExecutor);
        when(threadPoolExecutor.getQueue()).thenReturn(queue);

        InspectionProperties properties = mock(InspectionProperties.class);
        when(properties.queueCapacity()).thenReturn(50);

        InspectionQueueHealthIndicator indicator = new InspectionQueueHealthIndicator(executor, properties);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("activeWorkers", 0);
        assertThat(health.getDetails()).containsEntry("queueDepth", 0);
        assertThat(health.getDetails()).containsEntry("queueCapacity", 50);
        assertThat(health.getDetails()).containsEntry("status", "OPTIMAL");
    }

    @Test
    @DisplayName("Active non-saturated executor pool returns UP with accurate saturation ratio")
    void activeNonSaturatedPoolReturnsUp() {
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        ThreadPoolExecutor threadPoolExecutor = mock(ThreadPoolExecutor.class);
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(50);
        for (int i = 0; i < 25; i++) {
            queue.offer(mock(Runnable.class));
        }

        when(executor.getActiveCount()).thenReturn(2);
        when(executor.getPoolSize()).thenReturn(2);
        when(executor.getMaxPoolSize()).thenReturn(2);
        when(executor.getThreadPoolExecutor()).thenReturn(threadPoolExecutor);
        when(threadPoolExecutor.getQueue()).thenReturn(queue);

        InspectionProperties properties = mock(InspectionProperties.class);
        when(properties.queueCapacity()).thenReturn(50);

        InspectionQueueHealthIndicator indicator = new InspectionQueueHealthIndicator(executor, properties);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("activeWorkers", 2);
        assertThat(health.getDetails()).containsEntry("queueDepth", 25);
        assertThat(health.getDetails()).containsEntry("queueCapacity", 50);
        assertThat(health.getDetails()).containsEntry("saturationRatio", "0.50");
        assertThat(health.getDetails()).containsEntry("status", "OPTIMAL");
    }

    @Test
    @DisplayName("Saturated queue returns DOWN with saturation error detail")
    void saturatedQueueReturnsDown() {
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        ThreadPoolExecutor threadPoolExecutor = mock(ThreadPoolExecutor.class);
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(50);
        for (int i = 0; i < 50; i++) {
            queue.offer(mock(Runnable.class));
        }

        when(executor.getActiveCount()).thenReturn(2);
        when(executor.getPoolSize()).thenReturn(2);
        when(executor.getMaxPoolSize()).thenReturn(2);
        when(executor.getThreadPoolExecutor()).thenReturn(threadPoolExecutor);
        when(threadPoolExecutor.getQueue()).thenReturn(queue);

        InspectionProperties properties = mock(InspectionProperties.class);
        when(properties.queueCapacity()).thenReturn(50);

        InspectionQueueHealthIndicator indicator = new InspectionQueueHealthIndicator(executor, properties);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("queueDepth", 50);
        assertThat(health.getDetails()).containsEntry("queueCapacity", 50);
        assertThat(health.getDetails()).containsEntry("saturationRatio", "1.00");
        assertThat(health.getDetails()).containsEntry("status", "SATURATED");
        assertThat(health.getDetails()).containsKey("error");
    }
}
