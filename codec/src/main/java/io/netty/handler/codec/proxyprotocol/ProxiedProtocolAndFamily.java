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
package io.netty.handler.codec.proxyprotocol;

import java.util.HashMap;
import java.util.Map;

/**
 * The protocol and address family of a proxy protocol header
 */
public final class ProxiedProtocolAndFamily implements Comparable<ProxiedProtocolAndFamily> {
    /**
     * The UNKNOWN protocol and address family represents a connection which was forwarded for an unknown protocol
     * and address family.
     */
    public static final ProxiedProtocolAndFamily UNKNOWN = new ProxiedProtocolAndFamily(
            "UNKNOWN", ProxiedAddressFamily.UNSPECIFIED, ProxiedTransportProtocol.UNSPECIFIED, (byte) 0x00);

    /**
     * The TCP4 protocol and address family represents a connection which was forwarded for an IPV4 client over TCP
     */
    public static final ProxiedProtocolAndFamily TCP4 = new ProxiedProtocolAndFamily(
            "TCP4", ProxiedAddressFamily.IPV4, ProxiedTransportProtocol.STREAM, (byte) 0x11);

    /**
     * The TCP6 protocol and address family represents a connection which was forwarded for an IPV6 client over TCP
     */
    public static final ProxiedProtocolAndFamily TCP6 = new ProxiedProtocolAndFamily(
            "TCP6", ProxiedAddressFamily.IPV6, ProxiedTransportProtocol.STREAM, (byte) 0x21);

    /**
     * The UDP4 protocol and address family represents a connection which was forwarded for an IPV4 client over UDP
     */
    public static final ProxiedProtocolAndFamily UDP4 = new ProxiedProtocolAndFamily(
            "UDP4", ProxiedAddressFamily.IPV4, ProxiedTransportProtocol.DGRAM, (byte) 0x12);

    /**
     * The UDP6 protocol and address family represents a connection which was forwarded for an IPV6 client over UDP
     */
    public static final ProxiedProtocolAndFamily UDP6 = new ProxiedProtocolAndFamily(
            "UDP6", ProxiedAddressFamily.IPV6, ProxiedTransportProtocol.DGRAM, (byte) 0x22);

    /**
     * The UNIX_STREAM protocol and address family represents a connection which was forwarded for a unix stream socket
     */
    public static final ProxiedProtocolAndFamily UNIX_STREAM = new ProxiedProtocolAndFamily(
            "UNIX_STREAM", ProxiedAddressFamily.UNIX, ProxiedTransportProtocol.STREAM, (byte) 0x31);

    /**
     * The UNIX_DGRAM protocol and address family represents a connection which was forwarded for a unix datagram socket
     */
    public static final ProxiedProtocolAndFamily UNIX_DGRAM = new ProxiedProtocolAndFamily(
            "UNIX_DGRAM", ProxiedAddressFamily.UNIX, ProxiedTransportProtocol.DGRAM, (byte) 0x32);

    private static final Map<String, ProxiedProtocolAndFamily> protoAndFamilyNameMap =
            new HashMap<String, ProxiedProtocolAndFamily>(7);

    private static final Map<Byte, ProxiedProtocolAndFamily> protoAndFamilyByteMap =
            new HashMap<Byte, ProxiedProtocolAndFamily>(7);

    static {
        protoAndFamilyNameMap.put(UNKNOWN.name(), UNKNOWN);
        protoAndFamilyByteMap.put(UNKNOWN.byteValue(), UNKNOWN);

        protoAndFamilyNameMap.put(TCP4.name(), TCP4);
        protoAndFamilyByteMap.put(TCP4.byteValue(), TCP4);

        protoAndFamilyNameMap.put(TCP6.name(), TCP6);
        protoAndFamilyByteMap.put(TCP6.byteValue(), TCP6);

        protoAndFamilyNameMap.put(UDP4.name(), UDP4);
        protoAndFamilyByteMap.put(UDP4.byteValue(), UDP4);

        protoAndFamilyNameMap.put(UDP6.name(), UDP6);
        protoAndFamilyByteMap.put(UDP6.byteValue(), UDP6);

        protoAndFamilyNameMap.put(UNIX_STREAM.name(), UNIX_STREAM);
        protoAndFamilyByteMap.put(UNIX_STREAM.byteValue(), UNIX_STREAM);

        protoAndFamilyNameMap.put(UNIX_DGRAM.name(), UNIX_DGRAM);
        protoAndFamilyByteMap.put(UNIX_DGRAM.byteValue(), UNIX_DGRAM);
    }

    private final String name;
    private final byte pafByte;
    private final ProxiedAddressFamily addressFamily;
    private final ProxiedTransportProtocol transportProtocol;

    /**
     * Creates a new instance.
     */
    private ProxiedProtocolAndFamily(String name, ProxiedAddressFamily addressFamily,
                                     ProxiedTransportProtocol transportProtocol, byte pafByte) {
        this.name = name;
        this.pafByte = pafByte;
        this.addressFamily = addressFamily;
        this.transportProtocol = transportProtocol;
    }

    /**
     * Returns the {@link ProxiedProtocolAndFamily} represented by the specified name.
     *
     * @param name  Protocol and address family name
     * @return      {@link ProxiedProtocolAndFamily} instance OR <code>null</code> if the
     *              name is not recognized
     */
    public static ProxiedProtocolAndFamily valueOf(String name) {
        return protoAndFamilyNameMap.get(name);
    }

    /**
     * Returns the {@link ProxiedProtocolAndFamily} represented by the protocol and family byte.
     *
     * @param pafByte  Protocol and address family byte
     * @return         {@link ProxiedProtocolAndFamily} instance OR <code>null</code> if the
     *                 protocol and address family byte is not recognized
     */
    public static ProxiedProtocolAndFamily valueOf(byte pafByte) {
        return protoAndFamilyByteMap.get(pafByte);
    }

    /**
     * Returns the name of this protocol and address family.
     *
     * @return The name of this protocol and address family
     */
    public String name() {
        return name;
    }

    /**
     * Returns the byte value of this protocol and address family.
     *
     * @return The byte value of this protocol and address family
     */
    public byte byteValue() {
        return pafByte;
    }

    /**
     * Returns the {@link ProxiedAddressFamily} of this protocol and address family.
     *
     * @return The address family
     */
    public ProxiedAddressFamily getProxiedAddressFamily() {
        return addressFamily;
    }

    /**
     * Returns the {@link ProxiedTransportProtocol} of this protocol and address family.
     *
     * @return The transport protocol
     */
    public ProxiedTransportProtocol getProxiedTransportProtocol() {
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
