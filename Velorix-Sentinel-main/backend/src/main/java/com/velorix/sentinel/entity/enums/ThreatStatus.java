package com.velorix.sentinel.entity.enums;

/**
 * Triage state of a detected threat.
 *
 * <p>Every finding starts {@link #OPEN}; analyst workflows move it forward. The engine
 * never writes anything but {@code OPEN}.</p>
 */
public enum ThreatStatus {

    OPEN,
    ACKNOWLEDGED,
    RESOLVED,
    FALSE_POSITIVE
}
