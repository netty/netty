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
 * The transport protocol of a proxy protocol header
 */
public final class ProxiedTransportProtocol implements Comparable<ProxiedTransportProtocol> {
    /**
     * The transport protocol is specified in the lowest 4 bits of the transport protocol and address family byte
     */
    private static final byte TRANSPORT_MASK = (byte) 0x0f;

    /**
     * The UNSPECIFIED transport protocol represents a connection which was forwarded for an unkown protocol
     */
    public static final ProxiedTransportProtocol UNSPECIFIED = new ProxiedTransportProtocol("UNSPECIFIED", (byte) 0x00);

    /**
     * The STREAM transport protocol represents a connection which was forwarded for a TCP connection
     */
    public static final ProxiedTransportProtocol STREAM = new ProxiedTransportProtocol("STREAM", (byte) 0x01);

    /**
     * The DGRAM transport protocol represents a connection which was forwarded for a UDP connection
     */
    public static final ProxiedTransportProtocol DGRAM = new ProxiedTransportProtocol("DGRAM", (byte) 0x02);

    private static final Map<Byte, ProxiedTransportProtocol> transportMap =
            new HashMap<Byte, ProxiedTransportProtocol>(3);

    static {
        transportMap.put(UNSPECIFIED.byteValue(), UNSPECIFIED);
        transportMap.put(STREAM.byteValue(), STREAM);
        transportMap.put(DGRAM.byteValue(), DGRAM);
    }

    private final String name;
    private final byte transportByte;

    /**
     * Creates a new instance.
     */
    private ProxiedTransportProtocol(String name, byte transportByte) {
        this.name = name;
        this.transportByte = transportByte;
    }

    /**
     * Returns the {@link ProxiedTransportProtocol} represented by the specified transport protocol byte.
     *
     * @param addressFamilyByte  transport protocol byte
     * @return                   {@link ProxiedTransportProtocol} instance OR <code>null</code> if the
     *                           transport protocol is not recognized
     */
    public static ProxiedTransportProtocol valueOf(byte transportByte) {
        return transportMap.get((byte) (transportByte & TRANSPORT_MASK));
    }

    /**
     * Returns the name of this transport protocol.
     *
     * @return The name of this transport protocol
     */
    public String name() {
        return name;
    }

    /**
     * Returns the byte value of this transport protocol.
     *
     * @return The byte value of this transport protocol
     */
    public byte byteValue() {
        return transportByte;
    }

    @Override
    public int hashCode() {
        return byteValue();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ProxiedTransportProtocol)) {
            return false;
        }

        ProxiedTransportProtocol that = (ProxiedTransportProtocol) o;
        return byteValue() == that.byteValue();
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public int compareTo(ProxiedTransportProtocol o) {
        return Byte.valueOf(byteValue()).compareTo(Byte.valueOf(o.byteValue()));
    }

}
