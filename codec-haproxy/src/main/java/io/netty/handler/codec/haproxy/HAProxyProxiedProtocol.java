/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.haproxy;

import static io.netty.handler.codec.haproxy.HAProxyConstants.*;

/**
 * A protocol proxied by HAProxy which is represented by its transport protocol and address family.
 */
public enum HAProxyProxiedProtocol {
    /**
     * The UNKNOWN represents a connection which was forwarded for an unknown protocol and an unknown address family.
     */
    UNKNOWN(TPAF_UNKNOWN_BYTE, AddressFamily.AF_UNSPEC, TransportProtocol.UNSPEC),
    /**
     * The TCP4 represents a connection which was forwarded for an IPv4 client over TCP.
     */
    TCP4(TPAF_TCP4_BYTE, AddressFamily.AF_IPv4, TransportProtocol.STREAM),
    /**
     * The TCP6 represents a connection which was forwarded for an IPv6 client over TCP.
     */
    TCP6(TPAF_TCP6_BYTE, AddressFamily.AF_IPv6, TransportProtocol.STREAM),
    /**
     * The UDP4 represents a connection which was forwarded for an IPv4 client over UDP.
     */
    UDP4(TPAF_UDP4_BYTE, AddressFamily.AF_IPv4, TransportProtocol.DGRAM),
    /**
     * The UDP6 represents a connection which was forwarded for an IPv6 client over UDP.
     */
    UDP6(TPAF_UDP6_BYTE, AddressFamily.AF_IPv6, TransportProtocol.DGRAM),
    /**
     * The UNIX_STREAM represents a connection which was forwarded for a UNIX stream socket.
     */
    UNIX_STREAM(TPAF_UNIX_STREAM_BYTE, AddressFamily.AF_UNIX, TransportProtocol.STREAM),
    /**
     * The UNIX_DGRAM represents a connection which was forwarded for a UNIX datagram socket.
     */
    UNIX_DGRAM(TPAF_UNIX_DGRAM_BYTE, AddressFamily.AF_UNIX, TransportProtocol.DGRAM);

    private final byte byteValue;
    private final AddressFamily addressFamily;
    private final TransportProtocol transportProtocol;

    /**
     * Creates a new instance.
     */
    HAProxyProxiedProtocol(
            byte byteValue,
            AddressFamily addressFamily,
            TransportProtocol transportProtocol) {

        this.byteValue = byteValue;
        this.addressFamily = addressFamily;
        this.transportProtocol = transportProtocol;
    }

    /**
     * Returns the {@link HAProxyProxiedProtocol} represented by the specified byte.
     *
     * @param tpafByte transport protocol and address family byte
     */
    public static HAProxyProxiedProtocol valueOf(byte tpafByte) {
        switch (tpafByte) {
            case TPAF_TCP4_BYTE:
                return TCP4;
            case TPAF_TCP6_BYTE:
                return TCP6;
            case TPAF_UNKNOWN_BYTE:
                return UNKNOWN;
            case TPAF_UDP4_BYTE:
                return UDP4;
            case TPAF_UDP6_BYTE:
                return UDP6;
            case TPAF_UNIX_STREAM_BYTE:
                return UNIX_STREAM;
            case TPAF_UNIX_DGRAM_BYTE:
                return UNIX_DGRAM;
            default:
                throw new IllegalArgumentException(
                        "unknown transport protocol + address family: " + (tpafByte & 0xFF));
        }
    }

    /**
     * Returns the byte value of this protocol and address family.
     */
    public byte byteValue() {
        return byteValue;
    }

    /**
     * Returns the {@link AddressFamily} of this protocol and address family.
     */
    public AddressFamily addressFamily() {
        return addressFamily;
    }

    /**
     * Returns the {@link TransportProtocol} of this protocol and address family.
     */
    public TransportProtocol transportProtocol() {
        return transportProtocol;
    }

    /**
     * The address family of an HAProxy proxy protocol header.
     */
    public enum AddressFamily {
        /**
         * The UNSPECIFIED address family represents a connection which was forwarded for an unknown protocol.
         */
        AF_UNSPEC(AF_UNSPEC_BYTE),
        /**
         * The IPV4 address family represents a connection which was forwarded for an IPV4 client.
         */
        AF_IPv4(AF_IPV4_BYTE),
        /**
         * The IPV6 address family represents a connection which was forwarded for an IPV6 client.
         */
        AF_IPv6(AF_IPV6_BYTE),
        /**
         * The UNIX address family represents a connection which was forwarded for a unix socket.
         */
        AF_UNIX(AF_UNIX_BYTE);

        /**
         * The highest 4 bits of the transport protocol and address family byte contain the address family
         */
        private static final byte FAMILY_MASK = (byte) 0xf0;

        private final byte byteValue;

        /**
         * Creates a new instance
         */
        AddressFamily(byte byteValue) {
            this.byteValue = byteValue;
        }

        /**
         * Returns the {@link AddressFamily} represented by the highest 4 bits of the specified byte.
         *
         * @param tpafByte transport protocol and address family byte
         */
        public static AddressFamily valueOf(byte tpafByte) {
            int addressFamily = tpafByte & FAMILY_MASK;
            switch((byte) addressFamily) {
                case AF_IPV4_BYTE:
                    return AF_IPv4;
                case AF_IPV6_BYTE:
                    return AF_IPv6;
                case AF_UNSPEC_BYTE:
                    return AF_UNSPEC;
                case AF_UNIX_BYTE:
                    return AF_UNIX;
                default:
                    throw new IllegalArgumentException("unknown address family: " + addressFamily);
            }
        }

        /**
         * Returns the byte value of this address family.
         */
        public byte byteValue() {
            return byteValue;
        }
    }

    /**
     * The transport protocol of an HAProxy proxy protocol header
     */
    public enum TransportProtocol {
        /**
         * The UNSPEC transport protocol represents a connection which was forwarded for an unknown protocol.
         */
        UNSPEC(TRANSPORT_UNSPEC_BYTE),
        /**
         * The STREAM transport protocol represents a connection which was forwarded for a TCP connection.
         */
        STREAM(TRANSPORT_STREAM_BYTE),
        /**
         * The DGRAM transport protocol represents a connection which was forwarded for a UDP connection.
         */
        DGRAM(TRANSPORT_DGRAM_BYTE);

        /**
         * The transport protocol is specified in the lowest 4 bits of the transport protocol and address family byte
         */
        private static final byte TRANSPORT_MASK = 0x0f;

        private final byte transportByte;

        /**
         * Creates a new instance.
         */
        TransportProtocol(byte transportByte) {
            this.transportByte = transportByte;
        }

        /**
         * Returns the {@link TransportProtocol} represented by the lowest 4 bits of the specified byte.
         *
         * @param tpafByte transport protocol and address family byte
         */
        public static TransportProtocol valueOf(byte tpafByte) {
            int transportProtocol = tpafByte & TRANSPORT_MASK;
            switch ((byte) transportProtocol) {
                case TRANSPORT_STREAM_BYTE:
                    return STREAM;
                case TRANSPORT_UNSPEC_BYTE:
                    return UNSPEC;
                case TRANSPORT_DGRAM_BYTE:
                    return DGRAM;
                default:
                    throw new IllegalArgumentException("unknown transport protocol: " + transportProtocol);
            }
        }

        /**
         * Returns the byte value of this transport protocol.
         */
        public byte byteValue() {
            return transportByte;
        }
    }
}
