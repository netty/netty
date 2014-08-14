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

package io.netty.handler.codec.socksx.v5;

public enum SocksV5CmdType {
    CONNECT((byte) 0x01),
    BIND((byte) 0x02),
    UDP((byte) 0x03),
    UNKNOWN((byte) 0xff);

    private final byte b;

    SocksV5CmdType(byte b) {
        this.b = b;
    }

    public static SocksV5CmdType valueOf(byte b) {
        for (SocksV5CmdType code : values()) {
            if (code.b == b) {
                return code;
            }
        }
        return UNKNOWN;
    }

    public byte byteValue() {
        return b;
    }
}

