package io.github.kete1987.pokerbankroll.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.UnknownHostException;

import io.github.kete1987.pokerbankroll.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

/** The application as it is deployed: it never fetches from itself or from a private network. */
class LogoFetchBlockedTests extends ApiIntegrationTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "http://127.0.0.1/logo.png",
        "http://localhost:8080/api/actuator/health",
        "http://[::1]/logo.png",
        "http://10.0.0.5/logo.png",
        "http://192.168.1.1/logo.png",
        "http://172.16.0.1/logo.png",
        // Link-local, where cloud providers serve instance metadata.
        "http://169.254.169.254/latest/meta-data/",
        "http://0.0.0.0/logo.png",
        // The same loopback address written in other ways.
        "http://2130706433/logo.png",
        "http://[::ffff:127.0.0.1]/logo.png"})
    void refusesAddressesThatAreNotPublic(String url) {
        assertThat(postJson("/rooms/logo-fetch", "{\"url\": \"" + url + "\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson()
                .extractingPath("$.code").isEqualTo("LOGO_URL_NOT_PUBLIC");
    }

    @Test
    void theRefusalIsExplained() {
        assertThat(postJson("/rooms/logo-fetch", "{\"url\": \"http://192.168.1.1/logo.png\"}")
                .header("Accept-Language", "es"))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.detail")
                .isEqualTo("Solo se pueden cargar imágenes de internet, no de este equipo ni de una red privada.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "1.1.1.1", "151.101.1.69", "2606:4700:4700::1111", "100.63.255.255",
        "100.128.0.1", "172.32.0.1", "192.169.0.1", "192.0.1.1", "198.51.99.1", "203.0.112.1", "2a00:1450:4003::1",
        "2001:200::1", "2001:4860:4860::8888", "3fff:1000::1"})
    void publicAddressesAreAllowed(String address) throws UnknownHostException {
        assertThat(PublicAddress.isPublic(InetAddress.getByName(address))).as(address).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "127.255.255.254", "0.0.0.0", "0.1.2.3", "10.255.255.255", "172.31.255.255",
        "192.168.0.1", "169.254.0.1", "100.64.0.1", "100.127.255.255", "192.0.0.8", "198.18.0.1",
        "198.19.255.255", "224.0.0.1", "240.0.0.1", "255.255.255.255", "::1", "::", "fe80::1", "fc00::1",
        "fd12:3456:789a::1", "ff02::1",
        // Special-purpose blocks that are not globally reachable.
        "192.0.2.1", "198.51.100.1", "203.0.113.1", "192.88.99.1", "2001:db8::1", "2001::1", "2002:7f00:1::1",
        "64:ff9b::7f00:1", "100::1", "4000::1", "3fff::1", "3fff:fff:ffff::1"})
    void addressesOfThisMachineOrOfPrivateNetworksAreNot(String address) throws UnknownHostException {
        assertThat(PublicAddress.isPublic(InetAddress.getByName(address))).as(address).isFalse();
    }
}
