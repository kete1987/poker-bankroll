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
            int third = bytes[2] & 0xFF;
            // The blocks that are not globally reachable (IANA special-purpose registry) and that
            // InetAddress does not name: "this network", carrier-grade NAT (100.64/10), protocol
            // assignments (192.0.0/24), documentation (192.0.2/24, 198.51.100/24, 203.0.113/24),
            // the old 6to4 relays (192.88.99/24), benchmarking (198.18/15) and the reserved block
            // from 240 up, broadcast included.
            return first != 0
                    && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 192 && second == 0 && (third == 0 || third == 2))
                    && !(first == 192 && second == 88 && third == 99)
                    && !(first == 198 && (second == 18 || second == 19))
                    && !(first == 198 && second == 51 && third == 100)
                    && !(first == 203 && second == 0 && third == 113)
                    && first < 240;
        }
        if (address instanceof Inet6Address) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            int third = bytes[2] & 0xFF;
            int fourth = bytes[3] & 0xFF;
            // Only global unicast (2000::/3): that leaves out unique local addresses (fc00::/7) and
            // every other special block. Within it, protocol assignments (2001::/23, Teredo among
            // them), documentation (2001:db8::/32 and 3fff::/20) and 6to4 (2002::/16), which carries
            // an IPv4 address inside, are not public either.
            return (first & 0xE0) == 0x20
                    && !(first == 0x20 && second == 0x01 && third <= 0x01)
                    && !(first == 0x20 && second == 0x01 && third == 0x0D && fourth == 0xB8)
                    && !(first == 0x20 && second == 0x02)
                    && !(first == 0x3F && second == 0xFF && (third & 0xF0) == 0);
        }
        return false;
    }
}
