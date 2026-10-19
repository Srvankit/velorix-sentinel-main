package com.velorix.sentinel.repository.projection;

/**
 * One row of the "top ports" ranking.
 */
public interface PortCount {

    Integer getPort();

    long getTotal();

    long getBytes();
}
