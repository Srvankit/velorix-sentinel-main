package com.velorix.sentinel.config.properties;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Deep packet inspection engine settings bound from {@code velorix.inspection.*}.
 *
 * <p>Every value is a safety valve around an unbounded workload: captures are attacker
 * supplied, so the engine never trusts them to terminate, to stay small or to be well
 * formed.</p>
 *
 * @param engineVersion    stamped onto every analysis so results stay traceable to the
 *                         decoder that produced them
 * @param batchSize        rows accumulated before a JDBC batch flush
 * @param maxPackets       hard ceiling on frames decoded per run; the run completes with
 *                         a truncation note rather than running forever
 * @param maxDuration      wall clock budget for one run
 * @param progressInterval how many frames to process between progress writes
 * @param concurrency      number of captures inspected in parallel
 * @param queueCapacity    runs allowed to wait when every worker is busy
 */
@ConfigurationProperties(prefix = "velorix.inspection")
public record InspectionProperties(
        String engineVersion,
        Integer batchSize,
        Long maxPackets,
        Duration maxDuration,
        Integer progressInterval,
        Integer concurrency,
        Integer queueCapacity) {

    public InspectionProperties {
        engineVersion = blankToDefault(engineVersion, "dpi-1.0.0");
        batchSize = positiveOr(batchSize, 1_000);
        maxPackets = maxPackets == null || maxPackets <= 0 ? 5_000_000L : maxPackets;
        maxDuration = maxDuration == null || maxDuration.isZero() || maxDuration.isNegative()
                ? Duration.ofMinutes(15)
                : maxDuration;
        progressInterval = positiveOr(progressInterval, 2_000);
        concurrency = positiveOr(concurrency, 2);
        queueCapacity = positiveOr(queueCapacity, 50);
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Integer positiveOr(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }
}
