package com.velorix.sentinel.detection.support;

/**
 * Address classification helpers used by rules that care about the direction of traffic.
 *
 * <p>Kept deliberately dependency free and allocation light: these run once per frame on
 * captures that can hold millions of them.</p>
 */
public final class NetworkAddresses {

    private NetworkAddresses() {
    }

    /**
     * True for RFC1918, loopback, link-local and CGNAT space, plus IPv6 unique local and
     * link-local prefixes. Anything else is treated as external.
     */
    public static boolean isPrivate(String ip) {
        if (ip == null || ip.isBlank()) {
            return true;
        }
        if (ip.indexOf(':') >= 0) {
            String lower = ip.toLowerCase();
            return lower.equals("::1")
                    || lower.startsWith("fc")
                    || lower.startsWith("fd")
                    || lower.startsWith("fe80");
        }
        int[] octets = parseIpv4(ip);
        if (octets == null) {
            return true;
        }
        int a = octets[0];
        int b = octets[1];
        return a == 10
                || a == 127
                || a == 0
                || (a == 192 && b == 168)
                || (a == 172 && b >= 16 && b <= 31)
                || (a == 169 && b == 254)
                || (a == 100 && b >= 64 && b <= 127);
    }

    public static boolean isExternal(String ip) {
        return !isPrivate(ip) && !isMulticastOrBroadcast(ip);
    }

    public static boolean isMulticastOrBroadcast(String ip) {
        if (ip == null) {
            return false;
        }
        if (ip.equals("255.255.255.255")) {
            return true;
        }
        if (ip.indexOf(':') >= 0) {
            return ip.toLowerCase().startsWith("ff");
        }
        int[] octets = parseIpv4(ip);
        return octets != null && octets[0] >= 224 && octets[0] <= 239;
    }

    private static int[] parseIpv4(String ip) {
        int[] out = new int[4];
        int index = 0;
        int value = -1;
        for (int i = 0; i < ip.length(); i++) {
            char c = ip.charAt(i);
            if (c == '.') {
                if (value < 0 || index > 2) {
                    return null;
                }
                out[index++] = value;
                value = -1;
            } else if (c >= '0' && c <= '9') {
                value = (value < 0 ? 0 : value) * 10 + (c - '0');
                if (value > 255) {
                    return null;
                }
            } else {
                return null;
            }
        }
        if (index != 3 || value < 0) {
            return null;
        }
        out[3] = value;
        return out;
    }
}
