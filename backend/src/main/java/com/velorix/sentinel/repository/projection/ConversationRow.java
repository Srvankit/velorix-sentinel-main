package com.velorix.sentinel.repository.projection;

/**
 * One aggregated flow of a capture: every frame sharing the same
 * (source, destination, protocol) triple.
 *
 * <p>Timestamps are exposed as epoch milliseconds because the projection is produced by a
 * native GROUP BY, where driver level temporal mapping is not guaranteed.</p>
 */
public interface ConversationRow {

    String getClientIp();

    String getServerIp();

    String getProtocol();

    Integer getServerPort();

    long getPackets();

    long getBytes();

    long getSuspiciousPackets();

    Double getStartedAtMs();

    Double getEndedAtMs();
}
