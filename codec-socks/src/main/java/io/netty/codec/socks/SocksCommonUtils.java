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
package io.netty.codec.socks;

class SocksCommonUtils {
    public static final SocksRequest UNKNOWN_SOCKS_REQUEST = new UnknownSocksRequest();
    public static final SocksResponse UNKNOWN_SOCKS_RESPONSE = new UnknownSocksResponse();

    private static final int SECOND_ADDRESS_OCTET_SHIFT = 16;
    private static final int FIRST_ADDRESS_OCTET_SHIFT = 24;
    private static final int THIRD_ADDRESS_OCTET_SHIFT = 8;
    private static final int XOR_DEFAULT_VALUE = 0xff;

    public static String intToIp(int i) {
        return new StringBuilder().append((i >> FIRST_ADDRESS_OCTET_SHIFT) & XOR_DEFAULT_VALUE).append(".")
                .append((i >> SECOND_ADDRESS_OCTET_SHIFT) & XOR_DEFAULT_VALUE).append(".")
                .append((i >> THIRD_ADDRESS_OCTET_SHIFT) & XOR_DEFAULT_VALUE).append(".")
                .append(i & XOR_DEFAULT_VALUE).toString();
    }

    private static final char[] ipv6conseqZeroFiller = {':', ':'};
    private static final char ipv6hextetSeparator = ':';

    /*
    * Convert numeric IPv6 to compressed format, where
    * the longest sequence of 0's (with 2 or more 0's) is replaced with "::"
    */
    public static String ipv6toCompressedForm(byte[] src) {
        assert src.length == 16;
        //Find the longest sequence of 0's
        int cmprHextet = -1; //start of compressed region (hextet index)
        int cmprSize = 0; //length of compressed region
        for (int hextet = 0; hextet < 8; ) {
            int curByte = hextet * 2;
            int size = 0;
            while (curByte < src.length && src[curByte] == 0
                    && src[curByte + 1] == 0) {
                curByte += 2;
                size++;
            }
            if (size > cmprSize) {
                cmprHextet = hextet;
                cmprSize = size;
            }
            hextet = (curByte / 2) + 1;
        }
        if (cmprHextet == -1 || cmprSize < 2) {
            //No compression can be applied
            return ipv6toStr(src);
        }
        StringBuilder sb = new StringBuilder(39);
        ipv6toStr(sb, src, 0, cmprHextet);
        sb.append(ipv6conseqZeroFiller);
        ipv6toStr(sb, src, cmprHextet + cmprSize, 8);
        return sb.toString();
    }

    /*
    * Convert numeric IPv6 to standard (non-compressed) format.
    *
    * Borrowed from Inet6Address.java #numericToTextFormat(byte[])
    * Changed StringBuffer -> StringBuilder and ":" -> ':' for performance.
    */
    public static String ipv6toStr(byte[] src) {
        assert src.length == 16;
        StringBuilder sb = new StringBuilder(39);
        ipv6toStr(sb, src, 0, 8);
        return sb.toString();
    }

    private static final void ipv6toStr(StringBuilder sb, byte[] src,
                                        int fromHextet, int toHextet) {
        for (int i = fromHextet; i < toHextet; i++) {
            sb.append(Integer.toHexString(((src[i << 1] << 8) & 0xff00)
                    | (src[(i << 1) + 1] & 0xff)));
            if (i < toHextet - 1) {
                sb.append(ipv6hextetSeparator);
            }
        }
    }
}
