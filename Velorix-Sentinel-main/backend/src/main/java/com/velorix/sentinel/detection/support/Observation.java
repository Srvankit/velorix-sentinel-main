package com.velorix.sentinel.detection.support;

import com.velorix.sentinel.detection.PacketRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Rolling observation of one tracked key (a host, a host pair, a port…).
 *
 * <p>Holds only counters plus a bounded sample of frame numbers, so a rule tracking a
 * hundred thousand keys still costs a predictable amount of memory.</p>
 */
public final class Observation {

    private final int maxSamples;
    private final List<Long> sampleFrames;

    private long count;
    private long bytes;
    private Instant firstSeen;
    private Instant lastSeen;
    private String lastSourceIp;
    private String lastDestinationIp;

    public Observation(int maxSamples) {
        this.maxSamples = Math.max(1, maxSamples);
        this.sampleFrames = new ArrayList<>(Math.min(this.maxSamples, 16));
    }

    public void record(PacketRecord packet) {
        count++;
        bytes += packet.packetLength();
        Instant at = packet.timestamp();
        if (at != null) {
            if (firstSeen == null || at.isBefore(firstSeen)) {
                firstSeen = at;
            }
            if (lastSeen == null || at.isAfter(lastSeen)) {
                lastSeen = at;
            }
        }
        lastSourceIp = packet.sourceIp();
        lastDestinationIp = packet.destinationIp();
        if (sampleFrames.size() < maxSamples) {
            sampleFrames.add(packet.number());
        }
    }

    public long count() {
        return count;
    }

    public long bytes() {
        return bytes;
    }

    public Instant firstSeen() {
        return firstSeen;
    }

    public Instant lastSeen() {
        return lastSeen;
    }

    public String lastSourceIp() {
        return lastSourceIp;
    }

    public String lastDestinationIp() {
        return lastDestinationIp;
    }

    public List<Long> samples() {
        return List.copyOf(sampleFrames);
    }

    /** Observation span in seconds, floored at one so rates never divide by zero. */
    public double spanSeconds() {
        if (firstSeen == null || lastSeen == null) {
            return 1.0d;
        }
        double seconds = (lastSeen.toEpochMilli() - firstSeen.toEpochMilli()) / 1000.0d;
        return seconds <= 1.0d ? 1.0d : seconds;
    }

    /** Events per second across the observed span. */
    public double rate() {
        return count / spanSeconds();
    }
}
