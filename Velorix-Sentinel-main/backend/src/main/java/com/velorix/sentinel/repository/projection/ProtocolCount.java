package com.velorix.sentinel.repository.projection;

import com.velorix.sentinel.entity.enums.Protocol;

/**
 * One bucket of the per-analysis protocol histogram, produced by a GROUP BY query.
 */
public interface ProtocolCount {

    Protocol getProtocol();

    long getTotal();
}
