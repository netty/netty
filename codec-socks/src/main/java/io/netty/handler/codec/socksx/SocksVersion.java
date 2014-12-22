/*
 * Copyright 2013 The Netty Project
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

package io.netty.handler.codec.socksx;

/**
 * The version of SOCKS protocol.
 */
public enum SocksVersion {
    /**
     * SOCKS protocol version 4a (or 4)
     */
    SOCKS4a((byte) 0x04),
    /**
     * SOCKS protocol version 5
     */
    SOCKS5((byte) 0x05),
    /**
     * Unknown protocol version
     */
    UNKNOWN((byte) 0xff);

    /**
     * Returns the {@link SocksVersion} that corresponds to the specified version field value,
     * as defined in the protocol specification.
     *
     * @return {@link #UNKNOWN} if the specified value does not represent a known SOCKS protocol version
     */
    public static SocksVersion valueOf(byte b) {
        if (b == SOCKS4a.byteValue()) {
            return SOCKS4a;
        }
        if (b == SOCKS5.byteValue()) {
            return SOCKS5;
        }
        return UNKNOWN;
    }

    private final byte b;

    SocksVersion(byte b) {
        this.b = b;
    }

    /**
     * Returns the value of the version field, as defined in the protocol specification.
     */
    public byte byteValue() {
        return b;
    }
}
