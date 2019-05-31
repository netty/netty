/*
 *
 *  * Copyright 2019 The Netty Project
 *  *
 *  * The Netty Project licenses this file to you under the Apache License,
 *  * version 2.0 (the "License"); you may not use this file except in compliance
 *  * with the License. You may obtain a copy of the License at:
 *  *
 *  *   http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  * License for the specific language governing permissions and limitations
 *  * under the License.
 *
 */

package io.netty.handler.codec.quic.packet;

import io.netty.buffer.ByteBuf;

public final class HeaderUtil {

    private HeaderUtil() {}

    public static byte[] read(ByteBuf buf, int length) {
        byte[] binary = new byte[length];
        buf.readBytes(binary);
        return binary;
    }

    public static byte[][] readConnectionIDInfo(ByteBuf buf) {
        byte[][] connectionIDS = new byte[2][];
        int cil = buf.readByte() & 0xFF;
        int firstLength = ((cil & 0xf0) >> 4);
        int lastLength = ((cil & 0xf));
        if (firstLength > 0) {
            connectionIDS[0] = read(buf, firstLength + 3);
        }
        if (lastLength > 0) {
            connectionIDS[1] = read(buf, lastLength + 3);
        }
        return connectionIDS;
    }

}
