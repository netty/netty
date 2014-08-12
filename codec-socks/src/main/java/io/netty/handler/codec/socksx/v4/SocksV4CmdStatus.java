/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.socksx.v4;

public enum SocksV4CmdStatus {
    SUCCESS((byte) 0x5a),
    REJECTED_OR_FAILED((byte) 0x5b),
    IDENTD_UNREACHABLE((byte) 0x5c),
    IDENTD_AUTH_FAILURE((byte) 0x5d),
    UNASSIGNED((byte) 0xff);

    private final byte b;

    SocksV4CmdStatus(byte b) {
        this.b = b;
    }

    public static SocksV4CmdStatus valueOf(byte b) {
        for (SocksV4CmdStatus code : values()) {
            if (code.b == b) {
                return code;
            }
        }
        return UNASSIGNED;
    }

    public byte byteValue() {
        return b;
    }
}
