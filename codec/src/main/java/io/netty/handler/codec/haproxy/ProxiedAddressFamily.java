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

/**
 * The address family of an HAProxy proxy protocol header.
 */
public final class ProxiedAddressFamily implements Comparable<ProxiedAddressFamily> {
    /**
     * The highest 4 bits of the transport protocol and address family byte contain the address family
     */
    private static final byte FAMILY_MASK = (byte) 0xf0;

    /**
     * Address family byte constants.
     */
    private static final byte UNSPECIFIED_BYTE = (byte) 0x00;
    private static final byte IPV4_BYTE = (byte) 0x10;
    private static final byte IPV6_BYTE = (byte) 0x20;
    private static final byte UNIX_BYTE = (byte) 0x30;

    /**
     * The UNSPECIFIED address family represents a connection which was forwarded for an unkown protocol
     */
    public static final ProxiedAddressFamily UNSPECIFIED = new ProxiedAddressFamily("UNSPECIFIED", UNSPECIFIED_BYTE);

    /**
     * The IPV4 address family represents a connection which was forwarded for an IPV4 client
     */
    public static final ProxiedAddressFamily IPV4 = new ProxiedAddressFamily("IPV4", IPV4_BYTE);

    /**
     * The IPV6 address family represents a connection which was forwarded for an IPV6 client
     */
    public static final ProxiedAddressFamily IPV6 = new ProxiedAddressFamily("IPV6", IPV6_BYTE);

    /**
     * The UNIX address family represents a connection which was forwarded for a unix socket
     */
    public static final ProxiedAddressFamily UNIX = new ProxiedAddressFamily("UNIX", UNIX_BYTE);

    private final String name;
    private final byte addressFamilyByte;

    /**
     * Creates a new instance.
     */
    private ProxiedAddressFamily(String name, byte addressFamilyByte) {
        this.name = name;
        this.addressFamilyByte = addressFamilyByte;
    }

    /**
     * Returns the {@link ProxiedAddressFamily} represented by the specified address family byte.
     *
     * @param addressFamilyByte  Address family byte
     * @return                   {@link ProxiedAddressFamily} instance OR {@code null} if the
     *                           address family is not recognized
     */
    public static ProxiedAddressFamily valueOf(byte addressFamilyByte) {
        switch((byte) (addressFamilyByte & FAMILY_MASK)) {
            case IPV4_BYTE:
                return IPV4;
            case IPV6_BYTE:
                return IPV6;
            case UNSPECIFIED_BYTE:
                return UNSPECIFIED;
            case UNIX_BYTE:
                return UNIX;
            default:
                return null;
        }
    }

    /**
     * Returns the name of this address family.
     *
     * @return The name of this address family
     */
    public String name() {
        return name;
    }

    /**
     * Returns the byte value of this address family.
     *
     * @return The byte value of this address family
     */
    public byte byteValue() {
        return addressFamilyByte;
    }

    @Override
    public int hashCode() {
        return byteValue();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ProxiedAddressFamily)) {
            return false;
        }

        ProxiedAddressFamily that = (ProxiedAddressFamily) o;
        return byteValue() == that.byteValue();
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public int compareTo(ProxiedAddressFamily o) {
        return Byte.valueOf(byteValue()).compareTo(Byte.valueOf(o.byteValue()));
    }
}
