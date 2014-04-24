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
 * The HAProxy proxy protocol specification version
 */
public final class HAProxyProtocolVersion implements Comparable<HAProxyProtocolVersion> {
    /**
     * Version byte constants.
     */
    private static final byte ONE_BYTE = (byte) 0x01;
    private static final byte TWO_BYTE = (byte) 0x02;

    /**
     * The ONE proxy protocol version represents a version 1 (human-readable) header
     */
    public static final HAProxyProtocolVersion ONE = new HAProxyProtocolVersion("ONE", ONE_BYTE);

    /**
     * The TWO proxy protocol version represents a version 2 (binary) header
     */
    public static final HAProxyProtocolVersion TWO = new HAProxyProtocolVersion("TWO", TWO_BYTE);

    private final String name;
    private final byte versionByte;

    /**
     * Creates a new instance.
     */
    private HAProxyProtocolVersion(String name, byte versionByte) {
        this.name = name;
        this.versionByte = versionByte;
    }

    /**
     * Returns the {@link HAProxyProtocolVersion} represented by the specified version byte.
     *
     * @param versionByte  version byte
     * @return             {@link HAProxyProtocolVersion} instance OR {@code null} if the
     *                     version is not recognized
     */
    public static HAProxyProtocolVersion valueOf(byte versionByte) {
        switch (versionByte) {
            case TWO_BYTE:
                return TWO;
            case ONE_BYTE:
                return ONE;
            default:
                return null;
        }
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
        if (!(o instanceof HAProxyProtocolVersion)) {
            return false;
        }

        HAProxyProtocolVersion that = (HAProxyProtocolVersion) o;
        return byteValue() == that.byteValue();
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public int compareTo(HAProxyProtocolVersion o) {
        return Byte.valueOf(byteValue()).compareTo(Byte.valueOf(o.byteValue()));
    }
}
