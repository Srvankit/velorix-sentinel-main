package com.velorix.sentinel.repository.projection;

/**
 * One row of a "top endpoints" ranking: an address, how many frames it appeared in and
 * how many bytes it moved.
 */
public interface EndpointCount {

    String getLabel();

    long getTotal();

    long getBytes();
}
