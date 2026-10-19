package com.velorix.sentinel.dto.analysis;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A single ranked entry of a "top N" listing (talker, destination, port).
 *
 * @param label      the address or port rendered as text
 * @param packets    frames attributed to it
 * @param bytes      bytes attributed to it
 * @param percentage share of the run's frames, rounded to two decimals
 */
@Schema(name = "TopEntry", description = "One entry of a top-N ranking")
public record TopEntry(String label, long packets, long bytes, double percentage) {

    public static TopEntry of(String label, long packets, long bytes, long totalPackets) {
        double share = totalPackets <= 0 ? 0d : Math.round((packets * 10_000d) / totalPackets) / 100d;
        return new TopEntry(label, packets, bytes, share);
    }
}
