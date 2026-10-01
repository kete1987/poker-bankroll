package io.github.kete1987.pokerbankroll.room;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * Tells the addresses of the public internet from the ones that only make sense inside a network
 * (this machine, the LAN, link-local...). The application has no login: fetching a URL on behalf of
 * whoever asks must not become a way to reach what only the server can reach.
 */
final class PublicAddress {

    private PublicAddress() {
    }

    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            // "This network", carrier-grade NAT (100.64/10), protocol assignments (192.0.0/24),
            // benchmarking (198.18/15) and the reserved block from 240 up, broadcast included.
            return first != 0
                    && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 192 && second == 0 && (bytes[2] & 0xFF) == 0)
                    && !(first == 198 && (second == 18 || second == 19))
                    && first < 240;
        }
        if (address instanceof Inet6Address) {
            // Unique local addresses (fc00::/7), the private range of IPv6.
            return (bytes[0] & 0xFE) != 0xFC;
        }
        return false;
    }
}
