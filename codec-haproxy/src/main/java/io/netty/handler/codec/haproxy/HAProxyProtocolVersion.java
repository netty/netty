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
 * The HAProxy proxy protocol specification version.
 */
public enum HAProxyProtocolVersion {
    /**
     * The ONE proxy protocol version represents a version 1 (human-readable) header.
     */
    V1(VERSION_ONE_BYTE),
    /**
     * The TWO proxy protocol version represents a version 2 (binary) header.
     */
    V2(VERSION_TWO_BYTE);

    /**
     * The highest 4 bits of the protocol version and command byte contain the version
     */
    private static final byte VERSION_MASK = (byte) 0xf0;

    private final byte byteValue;

    /**
     * Creates a new instance
     */
    HAProxyProtocolVersion(byte byteValue) {
        this.byteValue = byteValue;
    }

    /**
     * Returns the {@link HAProxyProtocolVersion} represented by the highest 4 bits of the specified byte.
     *
     * @param verCmdByte protocol version and command byte
     */
    public static HAProxyProtocolVersion valueOf(byte verCmdByte) {
        int version = verCmdByte & VERSION_MASK;
        switch ((byte) version) {
            case VERSION_TWO_BYTE:
                return V2;
            case VERSION_ONE_BYTE:
                return V1;
            default:
                throw new IllegalArgumentException("unknown version: " + version);
        }
    }

    /**
     * Returns the byte value of this version.
     */
    public byte byteValue() {
        return byteValue;
    }
}
