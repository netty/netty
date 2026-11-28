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

import io.netty.handler.codec.dns.DnsSvcbRecord;
import io.netty.handler.codec.dns.ProtocolId;
import io.netty.handler.codec.dns.SvcParam;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * An element of a resolution result, one of the nested classes: an {@link Endpoint} extracted from an HTTPS record, an
 * {@link IpAddress} of the resolved name, an {@link HttpsAnswer}, a {@link ServiceUnavailable} indication, or an
 * {@link HttpsRecord} while the resolution is in progress. Use {@code instanceof} to tell them apart.
 */
abstract class DnsResolveResult {

    DnsResolveResult() {
    }

    /**
     * An alternative endpoint of the service, extracted from an HTTPS record.
     */
    static final class Endpoint extends DnsResolveResult {

        private final int priority;
        private final String targetName;
        private final int port;
        private final List<ProtocolId> protocols;
        private final byte[] ech;
        private final List<Inet4Address> ipv4Hints;
        private final List<Inet6Address> ipv6Hints;
        private final List<InetAddress> resolvedAddresses;
        private final long deadlineNanos;

        Endpoint(int priority, String targetName, int port, List<ProtocolId> protocols, byte[] ech,
                 List<Inet4Address> ipv4Hints, List<Inet6Address> ipv6Hints, List<InetAddress> resolvedAddresses,
                 long deadlineNanos) {
            this.priority = priority;
            this.targetName = checkNotNull(targetName, "targetName");
            this.port = port;
            this.protocols = Collections.unmodifiableList(new ArrayList<ProtocolId>(protocols));
            this.ech = ech == null ? null : ech.clone();
            this.ipv4Hints = Collections.unmodifiableList(new ArrayList<Inet4Address>(ipv4Hints));
            this.ipv6Hints = Collections.unmodifiableList(new ArrayList<Inet6Address>(ipv6Hints));
            this.resolvedAddresses = Collections.unmodifiableList(new ArrayList<InetAddress>(resolvedAddresses));
            this.deadlineNanos = deadlineNanos;
        }

        /**
         * Extracts the endpoint of a ServiceMode HTTPS record, with the {@code resolvedAddresses} of its target,
         * expiring at {@code deadlineNanos}.
         */
        static Endpoint of(DnsSvcbRecord record, List<InetAddress> resolvedAddresses, long deadlineNanos) {
            int port = -1;
            List<ProtocolId> protocols = new ArrayList<ProtocolId>();
            boolean noDefaultAlpn = false;
            byte[] ech = null;
            List<Inet4Address> ipv4Hints = Collections.emptyList();
            List<Inet6Address> ipv6Hints = Collections.emptyList();
            for (SvcParam param : record.params()) {
                if (param instanceof SvcParam.Port) {
                    port = ((SvcParam.Port) param).port();
                } else if (param instanceof SvcParam.Alpn) {
                    protocols.addAll(((SvcParam.Alpn) param).protocols());
                } else if (param instanceof SvcParam.NoDefaultAlpn) {
                    noDefaultAlpn = true;
                } else if (param instanceof SvcParam.Ech) {
                    ech = ((SvcParam.Ech) param).echConfigList();
                } else if (param instanceof SvcParam.Ipv4Hint) {
                    ipv4Hints = ((SvcParam.Ipv4Hint) param).addresses();
                } else if (param instanceof SvcParam.Ipv6Hint) {
                    ipv6Hints = ((SvcParam.Ipv6Hint) param).addresses();
                }
            }
            if (!noDefaultAlpn && !protocols.contains(ProtocolId.HTTP_1_1)) {
                protocols.add(ProtocolId.HTTP_1_1);
            }
            return new Endpoint(record.priority(), targetName(record), port, protocols, ech, ipv4Hints,
                    ipv6Hints, resolvedAddresses, deadlineNanos);
        }

        /**
         * Returns the name to resolve the addresses of the endpoint of {@code record} for: its TargetName, or its owner
         * name if the TargetName is {@code "."}.
         */
        static String targetName(DnsSvcbRecord record) {
            return ".".equals(record.targetName()) ? record.name() : record.targetName();
        }

        /**
         * Returns the SvcPriority: a smaller value is a more preferred endpoint.
         */
        public int priority() {
            return priority;
        }

        /**
         * Returns the name to resolve the addresses of this endpoint for. A TargetName of {@code "."} is already
         * replaced by the owner name of the record.
         */
        public String targetName() {
            return targetName;
        }

        /**
         * Returns the port of this endpoint, or {@code -1} if the record does not specify one, in which case the
         * port of the origin applies.
         */
        public int port() {
            return port;
        }

        /**
         * Returns the ALPN protocol identifiers supported by this endpoint, including the default {@code http/1.1}
         * unless the record excludes it.
         */
        public List<ProtocolId> protocols() {
            return protocols;
        }

        /**
         * Returns a copy of the ECHConfigList of this endpoint, or {@code null} if none.
         */
        public byte[] ech() {
            return ech == null ? null : ech.clone();
        }

        /**
         * Returns the addresses of {@link #targetName()}, resolved from its A/AAAA records, in the order of preference
         * of the resolver. They do not include the hints. It is empty if they could not be resolved, in which case the
         * hints may be used.
         */
        public List<InetAddress> resolvedAddresses() {
            return resolvedAddresses;
        }

        /**
         * Returns the IPv4 address hints, which may be used while the addresses of {@link #targetName()} are not
         * available.
         */
        public List<Inet4Address> ipv4Hints() {
            return ipv4Hints;
        }

        /**
         * Returns the IPv6 address hints, which may be used while the addresses of {@link #targetName()} are not
         * available.
         */
        public List<Inet6Address> ipv6Hints() {
            return ipv6Hints;
        }

        /**
         * Returns when the endpoint expires from the caches, the HTTPS records or the addresses of its target,
         * whichever expires first, as a deadline in nanoseconds on the ticker of the executor of the resolver, or
         * {@code -1} if unknown.
         */
        public long deadlineNanos() {
            return deadlineNanos;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Endpoint)) {
                return false;
            }
            Endpoint that = (Endpoint) o;
            return priority == that.priority && port == that.port && targetName.equalsIgnoreCase(that.targetName) &&
                    protocols.equals(that.protocols) && Arrays.equals(ech, that.ech) &&
                    ipv4Hints.equals(that.ipv4Hints) && ipv6Hints.equals(that.ipv6Hints) &&
                    resolvedAddresses.equals(that.resolvedAddresses);
        }

        @Override
        public int hashCode() {
            int hash = priority;
            hash = hash * 31 + targetName.toLowerCase(Locale.ROOT).hashCode();
            hash = hash * 31 + port;
            return hash * 31 + protocols.hashCode();
        }

        @Override
        public String toString() {
            return "Endpoint(" + priority + ' ' + targetName + " port=" + port + " protocols=" + protocols +
                    (ech != null ? " ech" : "") + " ipv4Hints=" + ipv4Hints + " ipv6Hints=" + ipv6Hints +
                    " resolvedAddresses=" + resolvedAddresses + ')';
        }
    }

    /**
     * An IP address of the resolved name, from its A (IPv4) or AAAA (IPv6) records.
     */
    static final class IpAddress extends DnsResolveResult {

        private final String name;
        private final InetAddress address;
        private final long deadlineNanos;

        IpAddress(String name, InetAddress address, long deadlineNanos) {
            this.name = checkNotNull(name, "name");
            this.address = checkNotNull(address, "address");
            this.deadlineNanos = deadlineNanos;
        }

        /**
         * Returns the name the address was resolved for.
         */
        public String name() {
            return name;
        }

        /**
         * Returns the address.
         */
        public InetAddress address() {
            return address;
        }

        /**
         * Returns when the address expires from the cache, as a deadline in nanoseconds on the ticker of the executor
         * of the resolver, {@code -1} if unknown, or {@link Long#MAX_VALUE} if never.
         */
        public long deadlineNanos() {
            return deadlineNanos;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof IpAddress && name.equalsIgnoreCase(((IpAddress) o).name) &&
                    address.equals(((IpAddress) o).address);
        }

        @Override
        public int hashCode() {
            return address.hashCode();
        }

        @Override
        public String toString() {
            return "IpAddress(" + name + ' ' + address.getHostAddress() + ')';
        }
    }

    /**
     * An HTTPS record collected during a resolution. It is internal to the resolution and is never part of a
     * completed result, which holds {@link Endpoint}s instead.
     */
    static final class HttpsRecord extends DnsResolveResult {

        private final DnsSvcbRecord record;

        HttpsRecord(DnsSvcbRecord record) {
            this.record = checkNotNull(record, "record");
        }

        DnsSvcbRecord record() {
            return record;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof HttpsRecord && record.equals(((HttpsRecord) o).record);
        }

        @Override
        public int hashCode() {
            return record.hashCode();
        }

        @Override
        public String toString() {
            return record.toString();
        }
    }

    /**
     * The HTTPS answer the result is based on, with or without HTTPS records, and when it expires from the cache: the
     * result as a whole stays valid at most until then.
     */
    static final class HttpsAnswer extends DnsResolveResult {

        private final long deadlineNanos;

        HttpsAnswer(long deadlineNanos) {
            this.deadlineNanos = deadlineNanos;
        }

        /**
         * Returns when the HTTPS answer expires from the cache, as a deadline in nanoseconds on the ticker of the
         * executor of the resolver.
         */
        long deadlineNanos() {
            return deadlineNanos;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof HttpsAnswer &&
                    deadlineNanos == ((HttpsAnswer) o).deadlineNanos;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(deadlineNanos);
        }

        @Override
        public String toString() {
            return "HttpsAnswer(" + deadlineNanos + ')';
        }
    }

    /**
     * The resolution found an AliasMode record with the TargetName {@code "."}: the service is declared as not
     * available (<a href="https://www.rfc-editor.org/rfc/rfc9460.html#section-2.5.1">RFC 9460, Section 2.5.1</a>). This
     * indication is advisory, so the addresses of the name may follow it in the result.
     */
    static final class ServiceUnavailable extends DnsResolveResult {

        private final String name;

        ServiceUnavailable(String name) {
            this.name = checkNotNull(name, "name");
        }

        /**
         * Returns the name declared as not available.
         */
        String name() {
            return name;
        }

        @Override
        public String toString() {
            return "ServiceUnavailable(" + name + ')';
        }
    }
}
