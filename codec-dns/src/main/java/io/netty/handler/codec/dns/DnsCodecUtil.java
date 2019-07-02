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

package io.netty.handler.codec.dns;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.util.CharsetUtil;

import static io.netty.handler.codec.dns.DefaultDnsRecordDecoder.*;

final class DnsCodecUtil {
    private DnsCodecUtil() {
        // Util class
    }

    static void encodeDomainName(String name, ByteBuf buf) {
        if (ROOT.equals(name)) {
            // Root domain
            buf.writeByte(0);
            return;
        }

        final String[] labels = name.split("\\.");
        for (String label : labels) {
            final int labelLen = label.length();
            if (labelLen == 0) {
                // zero-length label means the end of the name.
                break;
            }

            buf.writeByte(labelLen);
            ByteBufUtil.writeAscii(buf, label);
        }

        buf.writeByte(0); // marks end of name field
    }

    static String decodeDomainName(ByteBuf in) {
        int position = -1;
        int checked = 0;
        final int end = in.writerIndex();
        final int readable = in.readableBytes();

        // Looking at the spec we should always have at least enough readable bytes to read a byte here but it seems
        // some servers do not respect this for empty names. So just workaround this and return an empty name in this
        // case.
        //
        // See:
        // - https://github.com/netty/netty/issues/5014
        // - https://www.ietf.org/rfc/rfc1035.txt , Section 3.1
        if (readable == 0) {
            return ROOT;
        }

        final StringBuilder name = new StringBuilder(readable << 1);
        while (in.isReadable()) {
            final int len = in.readUnsignedByte();
            final boolean pointer = (len & 0xc0) == 0xc0;
            if (pointer) {
                if (position == -1) {
                    position = in.readerIndex() + 1;
                }

                if (!in.isReadable()) {
                    throw new CorruptedFrameException("truncated pointer in a name");
                }

                final int next = (len & 0x3f) << 8 | in.readUnsignedByte();
                if (next >= end) {
                    throw new CorruptedFrameException("name has an out-of-range pointer");
                }
                in.readerIndex(next);

                // check for loops
                checked += 2;
                if (checked >= end) {
                    throw new CorruptedFrameException("name contains a loop.");
                }
            } else if (len != 0) {
                if (!in.isReadable(len)) {
                    throw new CorruptedFrameException("truncated label in a name");
                }
                name.append(in.toString(in.readerIndex(), len, CharsetUtil.UTF_8)).append('.');
                in.skipBytes(len);
            } else { // len == 0
                break;
            }
        }

        if (position != -1) {
            in.readerIndex(position);
        }

        if (name.length() == 0) {
            return ROOT;
        }

        if (name.charAt(name.length() - 1) != '.') {
            name.append('.');
        }

        return name.toString();
    }

    /**
     * Decompress pointer data.
     * @param compression comporession data
     * @return decompressed data
     */
    static ByteBuf decompressDomainName(ByteBuf compression) {
        String domainName = decodeDomainName(compression);
        ByteBuf result = compression.alloc().buffer(domainName.length() << 1);
        encodeDomainName(domainName, result);
        return result;
    }
}
