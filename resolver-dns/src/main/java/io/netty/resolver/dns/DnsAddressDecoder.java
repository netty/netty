/*
 * Copyright 2018 The Netty Project
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
package io.netty.resolver.dns;

import java.net.IDN;
import java.net.InetAddress;
import java.net.UnknownHostException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;

/**
 * Decodes an {@link InetAddress} from an A or AAAA {@link DnsRawRecord}.
 */
final class DnsAddressDecoder {

    private static final int INADDRSZ4 = 4;
    private static final int INADDRSZ6 = 16;

    /**
     * Decodes an {@link InetAddress} from an A or AAAA {@link DnsRawRecord}.
     *
     * @param record the {@link DnsRecord}, most likely a {@link DnsRawRecord}
     * @param name the host name of the decoded address
     * @param decodeIdn whether to convert {@code name} to a unicode host name
     *
     * @return the {@link InetAddress}, or {@code null} if {@code record} is not a {@link DnsRawRecord} or
     *         its content is malformed
     */
    static InetAddress decodeAddress(DnsRecord record, String name, boolean decodeIdn) {
        if (!(record instanceof DnsRawRecord)) {
            return null;
        }
        final ByteBuf content = ((ByteBufHolder) record).content();
        final int contentLen = content.readableBytes();
        if (contentLen != INADDRSZ4 && contentLen != INADDRSZ6) {
            return null;
        }

        final byte[] addrBytes = new byte[contentLen];
        content.getBytes(content.readerIndex(), addrBytes);

        try {
            return InetAddress.getByAddress(decodeIdn ? IDN.toUnicode(name) : name, addrBytes);
        } catch (UnknownHostException e) {
            // Should never reach here.
            throw new Error(e);
        }
    }

    private DnsAddressDecoder() { }
}
