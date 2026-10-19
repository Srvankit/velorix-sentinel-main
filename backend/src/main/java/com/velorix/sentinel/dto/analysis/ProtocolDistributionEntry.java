package com.velorix.sentinel.dto.analysis;

import com.velorix.sentinel.entity.enums.Protocol;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One bucket of the protocol distribution returned by the summary endpoint.
 *
 * @param protocol   the protocol
 * @param count      frames observed for it
 * @param percentage share of the run, rounded to two decimals
 */
@Schema(name = "ProtocolDistributionEntry", description = "Share of one protocol inside an inspection run")
public record ProtocolDistributionEntry(Protocol protocol, long count, double percentage) {

    public static ProtocolDistributionEntry of(Protocol protocol, long count, long total) {
        double share = total <= 0 ? 0d : Math.round((count * 10_000d) / total) / 100d;
        return new ProtocolDistributionEntry(protocol, count, share);
    }
}
