/*
 * Copyright 2019 The Netty Project
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
package io.netty.handler.codec.quic;

import io.netty.buffer.ByteBuf;

import java.util.Arrays;

public enum Version {

    DRAFT_20;

    Version(byte... raw) {
        if (raw.length != 4) throw new IllegalArgumentException("Version codes must be 4 bytes long got " + raw.length + " bytes");
        this.raw = raw;
    }

    private byte[] raw;

    public byte[] getRawVersion() {
        return raw;
    }

    public void write(ByteBuf buf) {
        buf.writeBytes(raw);
    }

    public static Version readVersion(ByteBuf buf) {
        byte[] rawVersion = new byte[4];
        buf.readBytes(rawVersion);

        for (Version version : values()) {
            if (Arrays.equals(version.raw, rawVersion)) {
                return version;
            }
        }
        throw new IllegalArgumentException("Unknown Version: " + Arrays.toString(rawVersion));
    }

}
