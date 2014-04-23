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
 * The proxy protocol specification version
 */
public final class ProxyProtocolVersion implements Comparable<ProxyProtocolVersion> {
    /**
     * The ONE proxy protocol version represents a version 1 (human-readable) header
     */
    public static final ProxyProtocolVersion ONE = new ProxyProtocolVersion("ONE", (byte) 0x01);

    /**
     * The TWO proxy protocol version represents a version 2 (binary) header
     */
    public static final ProxyProtocolVersion TWO = new ProxyProtocolVersion("TWO", (byte) 0x02);

    private static final Map<Byte, ProxyProtocolVersion> versionMap =
            new HashMap<Byte, ProxyProtocolVersion>(2);

    static {
        versionMap.put(ONE.byteValue(), ONE);
        versionMap.put(TWO.byteValue(), TWO);
    }

    private final String name;
    private final byte versionByte;

    /**
     * Creates a new instance.
     */
    private ProxyProtocolVersion(String name, byte versionByte) {
        this.name = name;
        this.versionByte = versionByte;
    }

    /**
     * Returns the {@link ProxyProtocolVersion} represented by the specified version byte.
     *
     * @param versionByte  version byte
     * @return             {@link ProxyProtocolVersion} instance OR <code>null</code> if the
     *                     version is not recognized
     */
    public static ProxyProtocolVersion valueOf(byte versionByte) {
        return versionMap.get(versionByte);
    }

    /**
     * Returns the name of this version.
     *
     * @return The name of this version
     */
    public String name() {
        return name;
    }

    /**
     * Returns the byte value of this version.
     *
     * @return The byte value of this version
     */
    public byte byteValue() {
        return versionByte;
    }

    @Override
    public int hashCode() {
        return byteValue();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ProxyProtocolVersion)) {
            return false;
        }

        ProxyProtocolVersion that = (ProxyProtocolVersion) o;
        return byteValue() == that.byteValue();
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public int compareTo(ProxyProtocolVersion o) {
        return Byte.valueOf(byteValue()).compareTo(Byte.valueOf(o.byteValue()));
    }

}
