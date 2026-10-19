package com.velorix.sentinel.entity.enums;

/**
 * Impact ranking of a detected threat, ordered from least to most severe.
 */
public enum ThreatSeverity {

    LOW(1),
    MEDIUM(2),
    HIGH(3),
    CRITICAL(4);

    private final int weight;

    ThreatSeverity(int weight) {
        this.weight = weight;
    }

    /** Relative weight used by risk scoring. */
    public int weight() {
        return weight;
    }
}
