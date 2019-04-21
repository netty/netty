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

package io.netty.handler.codec.dns.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.CorruptedFrameException;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static io.netty.handler.codec.dns.util.DnsDecodeUtil.*;

public final class DnsEncodeUtil {
    private DnsEncodeUtil() {
        // Pirvate constructor for util class
    }

    /**
     * Encode domain name to byte buffer.
     *
     * @param domainName domain name
     * @param out output buffer
     */
    public static void encodeDomainName(String domainName, ByteBuf out) {
        if (ROOT.equals(domainName)) {
            // Root domain
            out.writeByte(0);
            return;
        }

        final String[] labels = domainName.split("\\.");
        for (String label : labels) {
            final int labelLen = label.length();
            if (labelLen == 0) {
                // zero-length label means the end of the name.
                break;
            }

            out.writeByte(labelLen);
            ByteBufUtil.writeAscii(out, label);
        }

        out.writeByte(0); // marks end of name field
    }

    /**
     * Get the cidr mask address by current inet address and mask length.
     *
     * @param inetAddress inet address, such as {@code 1.2.3.4}
     * @param maskLength cidr mask length, such as {@code 24}
     *
     * @return subnet inet address
     */
    public static InetAddress cidrMaskAddress(InetAddress inetAddress, int maskLength) {
        byte[] address = inetAddress.getAddress();
        int addressLength = address.length;
        for (int i = 0; i < addressLength; i++) {
            if (maskLength >= Byte.SIZE) {
                maskLength -= Byte.SIZE;
                continue;
            }
            address[i] &= ~(0xff >> maskLength);
            maskLength = 0;
        }
        try {
            return InetAddress.getByAddress(address);
        } catch (UnknownHostException e) {
            throw new CorruptedFrameException("unknown host");
        }
    }

}
