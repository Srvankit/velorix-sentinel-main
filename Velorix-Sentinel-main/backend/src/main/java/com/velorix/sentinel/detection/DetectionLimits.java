package com.velorix.sentinel.detection;

/**
 * Shared ceilings applied to every rule so one pathological capture cannot exhaust a
 * worker.
 *
 * @param maxFindingsPerRule findings a rule may emit for one run before it stops
 * @param maxSampleFrames    packet numbers retained as evidence per finding
 * @param maxTrackedKeys     distinct keys (hosts, pairs, ports) a rule may track
 */
public record DetectionLimits(int maxFindingsPerRule, int maxSampleFrames, int maxTrackedKeys) {

    public static DetectionLimits defaults() {
        return new DetectionLimits(200, 20, 200_000);
    }
}
