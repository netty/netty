/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.haproxy;

import java.util.HashMap;
import java.util.Map;

/**
 * The protocol and address family of an HAProxy proxy protocol header
 */
public final class ProxiedProtocolAndFamily implements Comparable<ProxiedProtocolAndFamily> {
    /**
     * Protocol and address family byte constants
     */
    private static final byte UNKNOWN_BYTE = (byte) 0x00;
    private static final byte TCP4_BYTE = (byte) 0x11;
    private static final byte TCP6_BYTE = (byte) 0x21;
    private static final byte UDP4_BYTE = (byte) 0x12;
    private static final byte UDP6_BYTE = (byte) 0x22;
    private static final byte UNIX_STREAM_BYTE = (byte) 0x31;
    private static final byte UNIX_DGRAM_BYTE = (byte) 0x32;

    /**
     * The UNKNOWN protocol and address family represents a connection which was forwarded for an unknown protocol
     * and address family
     */
    public static final ProxiedProtocolAndFamily UNKNOWN = new ProxiedProtocolAndFamily(
            "UNKNOWN", ProxiedAddressFamily.UNSPECIFIED, ProxiedTransportProtocol.UNSPECIFIED, UNKNOWN_BYTE);

    /**
     * The TCP4 protocol and address family represents a connection which was forwarded for an IPV4 client over TCP
     */
    public static final ProxiedProtocolAndFamily TCP4 = new ProxiedProtocolAndFamily(
            "TCP4", ProxiedAddressFamily.IPV4, ProxiedTransportProtocol.STREAM, TCP4_BYTE);

    /**
     * The TCP6 protocol and address family represents a connection which was forwarded for an IPV6 client over TCP
     */
    public static final ProxiedProtocolAndFamily TCP6 = new ProxiedProtocolAndFamily(
            "TCP6", ProxiedAddressFamily.IPV6, ProxiedTransportProtocol.STREAM, TCP6_BYTE);

    /**
     * The UDP4 protocol and address family represents a connection which was forwarded for an IPV4 client over UDP
     */
    public static final ProxiedProtocolAndFamily UDP4 = new ProxiedProtocolAndFamily(
            "UDP4", ProxiedAddressFamily.IPV4, ProxiedTransportProtocol.DGRAM, UDP4_BYTE);

    /**
     * The UDP6 protocol and address family represents a connection which was forwarded for an IPV6 client over UDP
     */
    public static final ProxiedProtocolAndFamily UDP6 = new ProxiedProtocolAndFamily(
            "UDP6", ProxiedAddressFamily.IPV6, ProxiedTransportProtocol.DGRAM, UDP6_BYTE);

    /**
     * The UNIX_STREAM protocol and address family represents a connection which was forwarded for a unix stream socket
     */
    public static final ProxiedProtocolAndFamily UNIX_STREAM = new ProxiedProtocolAndFamily(
            "UNIX_STREAM", ProxiedAddressFamily.UNIX, ProxiedTransportProtocol.STREAM, UNIX_STREAM_BYTE);

    /**
     * The UNIX_DGRAM protocol and address family represents a connection which was forwarded for a unix datagram socket
     */
    public static final ProxiedProtocolAndFamily UNIX_DGRAM = new ProxiedProtocolAndFamily(
            "UNIX_DGRAM", ProxiedAddressFamily.UNIX, ProxiedTransportProtocol.DGRAM, UNIX_DGRAM_BYTE);

    private static final Map<String, ProxiedProtocolAndFamily> PROTO_AND_FAMILY_NAME_MAP =
            new HashMap<String, ProxiedProtocolAndFamily>(7);

    static {
        PROTO_AND_FAMILY_NAME_MAP.put(UNKNOWN.name(), UNKNOWN);
        PROTO_AND_FAMILY_NAME_MAP.put(TCP4.name(), TCP4);
        PROTO_AND_FAMILY_NAME_MAP.put(TCP6.name(), TCP6);
        PROTO_AND_FAMILY_NAME_MAP.put(UDP4.name(), UDP4);
        PROTO_AND_FAMILY_NAME_MAP.put(UDP6.name(), UDP6);
        PROTO_AND_FAMILY_NAME_MAP.put(UNIX_STREAM.name(), UNIX_STREAM);
        PROTO_AND_FAMILY_NAME_MAP.put(UNIX_DGRAM.name(), UNIX_DGRAM);
    }

    private final String name;
    private final byte pafByte;
    private final ProxiedAddressFamily addressFamily;
    private final ProxiedTransportProtocol transportProtocol;

    /**
     * Creates a new instance
     */
    private ProxiedProtocolAndFamily(String name, ProxiedAddressFamily addressFamily,
                                     ProxiedTransportProtocol transportProtocol, byte pafByte) {
        this.name = name;
        this.pafByte = pafByte;
        this.addressFamily = addressFamily;
        this.transportProtocol = transportProtocol;
    }

    /**
     * Returns the {@link ProxiedProtocolAndFamily} represented by the specified name
     *
     * @param name  protocol and address family name
     * @return      {@link ProxiedProtocolAndFamily} instance OR {@code null} if the
     *              name is not recognized
     */
    public static ProxiedProtocolAndFamily valueOf(String name) {
        return PROTO_AND_FAMILY_NAME_MAP.get(name);
    }

    /**
     * Returns the {@link ProxiedProtocolAndFamily} represented by the protocol and family byte
     *
     * @param pafByte  protocol and address family byte
     * @return         {@link ProxiedProtocolAndFamily} instance OR {@code null} if the
     *                 protocol and address family byte is not recognized
     */
    public static ProxiedProtocolAndFamily valueOf(byte pafByte) {
        switch (pafByte) {
            case TCP4_BYTE:
                return TCP4;
            case TCP6_BYTE:
                return TCP6;
            case UNKNOWN_BYTE:
                return UNKNOWN;
            case UDP4_BYTE:
                return UDP4;
            case UDP6_BYTE:
                return UDP6;
            case UNIX_STREAM_BYTE:
                return UNIX_STREAM;
            case UNIX_DGRAM_BYTE:
                return UNIX_DGRAM;
            default:
                return null;
        }
    }

    /**
     * Returns the name of this protocol and address family
     *
     * @return the name of this protocol and address family
     */
    public String name() {
        return name;
    }

    /**
     * Returns the byte value of this protocol and address family
     *
     * @return the byte value of this protocol and address family
     */
    public byte byteValue() {
        return pafByte;
    }

    /**
     * Returns the {@link ProxiedAddressFamily} of this protocol and address family
     *
     * @return the address family
     */
    public ProxiedAddressFamily proxiedAddressFamily() {
        return addressFamily;
    }

    /**
     * Returns the {@link ProxiedTransportProtocol} of this protocol and address family
     *
     * @return the transport protocol
     */
    public ProxiedTransportProtocol proxiedTransportProtocol() {
        return transportProtocol;
    }

    @Override
    public int hashCode() {
        return name().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ProxiedProtocolAndFamily)) {
            return false;
        }

        ProxiedProtocolAndFamily that = (ProxiedProtocolAndFamily) o;
        return name().equals(that.name());
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public int compareTo(ProxiedProtocolAndFamily o) {
        return name().compareTo(o.name());
    }
}
