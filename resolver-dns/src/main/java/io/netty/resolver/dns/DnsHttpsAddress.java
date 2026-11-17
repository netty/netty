/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.resolver.dns;

import io.netty.util.internal.UnstableApi;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.netty.util.internal.ObjectUtil.checkInRange;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * An address to connect to for a {@link DnsHttpsQuery}, with the port and the connection parameters to use.
 * <p>
 * It is resolved through HTTPS records (<a href="https://www.rfc-editor.org/rfc/rfc9460.html">RFC 9460</a>), as an
 * address of an alternative endpoint of the service, or else it is an address of the host of the query, to connect to
 * as without HTTPS records.
 */
@UnstableApi
public final class DnsHttpsAddress {

    private static final List<String> HTTP_1_1 = Collections.singletonList("http/1.1");

    private final InetAddress address;
    private final int port;
    private final List<String> protocols;
    private final byte[] ech;
    private final int priority;
    private final Duration ttl;

    DnsHttpsAddress(InetAddress address, int port, List<String> protocols, byte[] ech, int priority, Duration ttl) {
        this.address = checkNotNull(address, "address");
        this.port = checkInRange(port, 0, 65535, "port");
        this.protocols = Collections.unmodifiableList(new ArrayList<String>(protocols));
        this.ech = ech == null ? null : ech.clone();
        this.priority = checkInRange(priority, -1, 65535, "priority");
        this.ttl = ttl;
    }

    /**
     * Returns an address not resolved through an HTTPS record, to connect to with {@code http/1.1} as without HTTPS
     * records.
     *
     * @param address the address to connect to
     * @param port the port to connect to
     */
    public static DnsHttpsAddress of(InetAddress address, int port) {
        return of(address, port, null);
    }

    static DnsHttpsAddress of(InetAddress address, int port, Duration ttl) {
        return new DnsHttpsAddress(address, port, HTTP_1_1, null, -1, ttl);
    }

    /**
     * Returns an address not resolved through an HTTPS record, to connect to with {@code http/1.1} as without HTTPS
     * records.
     *
     * @param address the resolved socket address to connect to
     */
    public static DnsHttpsAddress of(InetSocketAddress address) {
        if (checkNotNull(address, "address").isUnresolved()) {
            throw new IllegalArgumentException("address is unresolved: " + address);
        }
        return of(address.getAddress(), address.getPort());
    }

    /**
     * Returns the address to connect to.
     */
    public InetAddress address() {
        return address;
    }

    /**
     * Returns the port to connect to.
     */
    public int port() {
        return port;
    }

    /**
     * Returns the protocols that can be used, as ALPN protocol identifiers, e.g. {@code "h2"} or {@code "h3"}.
     */
    public List<String> protocols() {
        return protocols;
    }

    /**
     * Returns the Encrypted ClientHello configuration list to use, or {@code null} if none.
     */
    public byte[] ech() {
        return ech == null ? null : ech.clone();
    }

    /**
     * Returns the priority of the HTTPS record the address was resolved through, or {@code -1} if it was not resolved
     * through an HTTPS record. Any value {@code >= 0} is the original priority returned by the record. Only values
     * {@code > 0} should be used to tell entries apart, lower values first.
     */
    public int priority() {
        return priority;
    }

    /**
     * Returns how long this address can be used before resolving it again, counted from when the resolution
     * completed: the time left before the records it was resolved from expire from the resolver's caches, ignoring
     * those whose expiration time is unknown. The caches may remove them earlier, see
     * {@link DnsCacheEntry#deadlineNanos()}. Returns {@code null} if none is known, e.g.
     * for an address not resolved through DNS, or with a cache not reporting its expiration times.
     */
    public Duration ttl() {
        return ttl;
    }

    /**
     * Returns whether the address was resolved through an HTTPS record, i.e. {@code priority() != -1}.
     */
    public boolean isHttpsResolved() {
        return priority != -1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DnsHttpsAddress)) {
            return false;
        }
        DnsHttpsAddress that = (DnsHttpsAddress) o;
        return port == that.port && priority == that.priority && address.equals(that.address) &&
                protocols.equals(that.protocols) && Arrays.equals(ech, that.ech);
    }

    @Override
    public int hashCode() {
        int hash = address.hashCode();
        hash = hash * 31 + port;
        hash = hash * 31 + protocols.hashCode();
        return hash * 31 + priority;
    }

    @Override
    public String toString() {
        return "DnsHttpsAddress(" + address.getHostAddress() + ':' + port + " protocols=" + protocols +
                (ech != null ? " ech" : "") + " priority=" + priority + (ttl != null ? " ttl=" + ttl : "") + ')';
    }
}
