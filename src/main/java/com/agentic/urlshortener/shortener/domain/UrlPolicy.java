package com.agentic.urlshortener.shortener.domain;

import java.net.IDN;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;

import com.agentic.urlshortener.common.exception.ApiException;
import com.agentic.urlshortener.common.exception.ErrorCode;

/**
 * Validates and normalizes target URLs (FR-LNK-02..04). Only {@code http} and {@code https} are
 * accepted; credentials, missing hosts, over-long URLs, local names, loopback, private, link-local,
 * unspecified, shared-address and multicast addresses in any notation, and the shortener's own hosts
 * are rejected. No DNS resolution takes place (research R-07): the decision uses the literal only, and
 * anything ambiguous (for example a percent-encoded host) is rejected rather than interpreted.
 */
public class UrlPolicy {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final Set<String> LOCAL_SUFFIXES = Set.of(".localhost", ".local", ".internal", ".localdomain", ".home.arpa");

    private final int maxLength;
    private final Set<String> selfHosts;

    public UrlPolicy(int maxLength, Set<String> selfHosts) {
        this.maxLength = maxLength;
        this.selfHosts = selfHosts.stream().map(h -> h.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    public NormalizedUrl validate(String input) {
        String raw = input == null ? "" : input.strip();
        if (raw.isEmpty()) {
            throw reject(ErrorCode.URL_INVALID, "The URL is empty.");
        }
        int colon = raw.indexOf(':');
        if (colon <= 0 || !raw.substring(0, colon).matches("[A-Za-z][A-Za-z0-9+.-]*")) {
            throw reject(ErrorCode.URL_INVALID, "The URL must be absolute (scheme and host).");
        }
        String scheme = raw.substring(0, colon).toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            throw reject(ErrorCode.URL_SCHEME_NOT_ALLOWED, "Only http and https URLs can be shortened; got '" + scheme + "'.");
        }
        if (raw.length() > maxLength) {
            throw reject(ErrorCode.URL_TOO_LONG, "The URL exceeds " + maxLength + " characters.");
        }
        URI uri = parse(toAsciiHost(raw));
        String authority = uri.getRawAuthority();
        if (authority == null || authority.isEmpty()) {
            throw reject(ErrorCode.URL_HOST_MISSING, "The URL has no host.");
        }
        if (uri.getRawUserInfo() != null || authority.contains("@")) {
            throw reject(ErrorCode.URL_CREDENTIALS_NOT_ALLOWED, "The URL must not contain user credentials.");
        }
        // The authority is split here rather than taken from URI#getHost, which returns null for
        // numeric forms such as 127.1 or 2130706433 that browsers still resolve.
        HostPort hostPort = HostPort.split(authority);
        if (hostPort.host().isEmpty()) {
            throw reject(ErrorCode.URL_HOST_MISSING, "The URL has no host.");
        }
        String host = hostPort.host().toLowerCase(Locale.ROOT);
        checkHost(host);

        String normalized = rebuild(scheme, host, hostPort.port(), uri);
        if (normalized.length() > maxLength) {
            throw reject(ErrorCode.URL_TOO_LONG, "The normalized URL exceeds " + maxLength + " characters.");
        }
        return new NormalizedUrl(normalized, host);
    }

    private void checkHost(String host) {
        if (host.startsWith("[")) {
            checkIpv6(host.substring(1, host.length() - 1));
            return;
        }
        if (host.contains("%")) {
            throw reject(ErrorCode.URL_INVALID, "Percent-encoded hosts are not accepted.");
        }
        String bare = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
        if (selfHosts.contains(bare)) {
            throw notAllowed("The URL points at this shortener.");
        }
        OptionalLong ipv4 = Ipv4LiteralParser.parse(bare);
        if (ipv4.isPresent()) {
            if (isBlockedIpv4(ipv4.getAsLong())) {
                throw notAllowed("The URL points at a non-public IPv4 address.");
            }
            return;
        }
        if (bare.equals("localhost") || LOCAL_SUFFIXES.stream().anyMatch(bare::endsWith)) {
            throw notAllowed("The URL points at a local host name.");
        }
        if (!bare.contains(".")) {
            throw notAllowed("Single-label host names are not public.");
        }
    }

    private static void checkIpv6(String literal) {
        byte[] bytes;
        try {
            InetAddress address = InetAddress.getByName(literal); // literal only: no lookup for IPv6 text
            bytes = address.getAddress();
            if (address instanceof Inet6Address v6 && (v6.isLoopbackAddress() || v6.isAnyLocalAddress()
                    || v6.isLinkLocalAddress() || v6.isSiteLocalAddress() || v6.isMulticastAddress())) {
                throw notAllowed("The URL points at a non-public IPv6 address.");
            }
        } catch (UnknownHostException e) {
            throw reject(ErrorCode.URL_INVALID, "The IPv6 address is malformed.");
        }
        if (bytes.length == 4) { // IPv4-mapped addresses are returned as IPv4 by the JDK
            if (isBlockedIpv4(toLong(bytes, 0))) {
                throw notAllowed("The URL points at a non-public IPv4-mapped address.");
            }
            return;
        }
        boolean uniqueLocal = (bytes[0] & 0xFE) == 0xFC; // fc00::/7
        boolean mappedOrCompatible = isZero(bytes, 0, 10) && ((bytes[10] == (byte) 0xFF && bytes[11] == (byte) 0xFF)
                || (bytes[10] == 0 && bytes[11] == 0));
        if (uniqueLocal || (mappedOrCompatible && isBlockedIpv4(toLong(bytes, 12)))) {
            throw notAllowed("The URL points at a non-public IPv6 address.");
        }
    }

    static boolean isBlockedIpv4(long a) {
        return inRange(a, 0x00000000L, 8)      // 0.0.0.0/8 unspecified / "this network"
                || inRange(a, 0x0A000000L, 8)  // 10.0.0.0/8 private
                || inRange(a, 0x64400000L, 10) // 100.64.0.0/10 shared address space (CGNAT)
                || inRange(a, 0x7F000000L, 8)  // 127.0.0.0/8 loopback
                || inRange(a, 0xA9FE0000L, 16) // 169.254.0.0/16 link-local and cloud metadata
                || inRange(a, 0xAC100000L, 12) // 172.16.0.0/12 private
                || inRange(a, 0xC0000000L, 24) // 192.0.0.0/24 IETF protocol assignments
                || inRange(a, 0xC0A80000L, 16) // 192.168.0.0/16 private
                || inRange(a, 0xC6120000L, 15) // 198.18.0.0/15 benchmarking
                || inRange(a, 0xE0000000L, 4)  // 224.0.0.0/4 multicast
                || inRange(a, 0xF0000000L, 4); // 240.0.0.0/4 reserved and broadcast
    }

    private static boolean inRange(long address, long network, int prefix) {
        long mask = (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
        return (address & mask) == network;
    }

    private static long toLong(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFFL) << 24) | ((bytes[offset + 1] & 0xFFL) << 16) | ((bytes[offset + 2] & 0xFFL) << 8)
                | (bytes[offset + 3] & 0xFFL);
    }

    private static boolean isZero(byte[] bytes, int from, int to) {
        for (int i = from; i < to; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }

    /** Host and port of an authority without user information; port -1 when absent. */
    private record HostPort(String host, int port) {

        static HostPort split(String authority) {
            String host = authority;
            String port = null;
            if (authority.startsWith("[")) {
                int close = authority.indexOf(']');
                if (close < 0) {
                    throw reject(ErrorCode.URL_INVALID, "The IPv6 address is malformed.");
                }
                host = authority.substring(0, close + 1);
                String rest = authority.substring(close + 1);
                if (!rest.isEmpty()) {
                    if (!rest.startsWith(":")) {
                        throw reject(ErrorCode.URL_INVALID, "The URL authority is malformed.");
                    }
                    port = rest.substring(1);
                }
            } else {
                int colon = authority.lastIndexOf(':');
                if (colon >= 0) {
                    host = authority.substring(0, colon);
                    port = authority.substring(colon + 1);
                }
            }
            return new HostPort(host, parsePort(port));
        }

        private static int parsePort(String port) {
            if (port == null || port.isEmpty()) {
                return -1;
            }
            if (!port.matches("\\d{1,5}") || Integer.parseInt(port) > 65_535) {
                throw reject(ErrorCode.URL_INVALID, "The URL port is invalid.");
            }
            return Integer.parseInt(port);
        }
    }

    /** Converts an internationalized host to its ASCII (punycode) form before URI parsing. */
    private static String toAsciiHost(String raw) {
        int start = raw.indexOf("//");
        if (start < 0) {
            return raw;
        }
        start += 2;
        int end = start;
        while (end < raw.length() && "/?#".indexOf(raw.charAt(end)) < 0) {
            end++;
        }
        String authority = raw.substring(start, end);
        int at = authority.lastIndexOf('@');
        String hostPort = authority.substring(at + 1);
        if (hostPort.startsWith("[") || hostPort.chars().allMatch(c -> c < 0x80)) {
            return raw;
        }
        int portSeparator = hostPort.lastIndexOf(':');
        String host = portSeparator >= 0 ? hostPort.substring(0, portSeparator) : hostPort;
        String port = portSeparator >= 0 ? hostPort.substring(portSeparator) : "";
        try {
            String ascii = IDN.toASCII(host, IDN.ALLOW_UNASSIGNED);
            return raw.substring(0, start) + authority.substring(0, at + 1) + ascii + port + raw.substring(end);
        } catch (IllegalArgumentException e) {
            throw reject(ErrorCode.URL_INVALID, "The internationalized host name is invalid.");
        }
    }

    private static URI parse(String raw) {
        try {
            URI uri = new URI(raw);
            if (!uri.isAbsolute() || uri.isOpaque()) {
                throw reject(ErrorCode.URL_INVALID, "The URL must be absolute (scheme and host).");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw reject(ErrorCode.URL_INVALID, "The URL is not well-formed.");
        }
    }

    private static String rebuild(String scheme, String host, int port, URI uri) {
        StringBuilder url = new StringBuilder(scheme).append("://").append(host);
        boolean defaultPort = (scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443);
        if (port != -1 && !defaultPort) {
            url.append(':').append(port);
        }
        if (uri.getRawPath() != null) {
            url.append(uri.getRawPath());
        }
        if (uri.getRawQuery() != null) {
            url.append('?').append(uri.getRawQuery());
        }
        if (uri.getRawFragment() != null) {
            url.append('#').append(uri.getRawFragment());
        }
        return url.toString();
    }

    private static ApiException notAllowed(String detail) {
        return reject(ErrorCode.URL_HOST_NOT_ALLOWED, detail);
    }

    private static ApiException reject(ErrorCode code, String detail) {
        return new ApiException(code, detail);
    }
}
