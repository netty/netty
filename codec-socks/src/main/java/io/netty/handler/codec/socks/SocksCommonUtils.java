/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.socks;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.StringUtil;

final class SocksCommonUtils {
    public static final SocksRequest UNKNOWN_SOCKS_REQUEST = new UnknownSocksRequest();
    public static final SocksResponse UNKNOWN_SOCKS_RESPONSE = new UnknownSocksResponse();

    /**
     * A constructor to stop this class being constructed.
     */
    private SocksCommonUtils() {
        // NOOP
    }

    private static final char ipv6hextetSeparator = ':';

    /**
     * Converts numeric IPv6 to standard (non-compressed) format.
     */
    public static String ipv6toStr(byte[] src) {
        assert src.length == 16;
        StringBuilder sb = new StringBuilder(39);
        ipv6toStr(sb, src, 0, 8);
        return sb.toString();
    }

    private static void ipv6toStr(StringBuilder sb, byte[] src, int fromHextet, int toHextet) {
        int i;
        toHextet --;
        for (i = fromHextet; i < toHextet; i++) {
            appendHextet(sb, src, i);
            sb.append(ipv6hextetSeparator);
        }

        appendHextet(sb, src, i);
    }

    private static void appendHextet(StringBuilder sb, byte[] src, int i) {
        StringUtil.toHexString(sb, src, i << 1, 2);
    }

    static String readUsAscii(ByteBuf buffer, int length) {
        String s = buffer.toString(buffer.readerIndex(), length, CharsetUtil.US_ASCII);
        buffer.skipBytes(length);
        return s;
    }
}
