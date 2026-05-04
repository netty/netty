/*
 * Copyright 2019 The Netty Project
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

package io.netty.handler.codec.dns;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.TooLongFrameException;
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

        int totalLength = 0;
        final String[] labels = name.split("\\.");
        for (int i = 0; i < labels.length; i++) {
            String label = labels[i];
            final int labelLen = label.length();
            if (labelLen == 0) {
                if (i == labels.length - 1) {
                    // zero-length label at the end means the end of the name.
                    break;
                } else {
                    throw new IllegalArgumentException("DNS name contains empty label: " + name);
                }
            }
            if (labelLen > 63) {
                throw new IllegalArgumentException(
                        "DNS label length " + labelLen + " exceeds maximum of 63: " + name);
            }
            int idx = label.indexOf('\0');
            if (idx != -1) {
                throw new IllegalArgumentException(
                        "DNS label contains null byte at index " + idx);
            }
            totalLength += 1 + labelLen;
            if (totalLength > 255) {
                throw new IllegalArgumentException(
                        "DNS name exceeds maximum length of 255: " + name);
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
                // See https://datatracker.ietf.org/doc/html/rfc1035#section-2.3.4
                if (len > 63) {
                    throw new TooLongFrameException("label must be <= 63 but was " + len);
                }
                name.append(in.toString(in.readerIndex(), len, CharsetUtil.UTF_8)).append('.');
                in.skipBytes(len);
                // See https://datatracker.ietf.org/doc/html/rfc1035#section-2.3.4
                if (name.length() > 255) {
                    throw new TooLongFrameException("domain name must be <= 255 but was " + name.length());
                }
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
     * @param compression compressed data
     * @return decompressed data
     */
    static ByteBuf decompressDomainName(ByteBuf compression) {
        String domainName = decodeDomainName(compression);
        ByteBuf result = compression.alloc().buffer(domainName.length() << 1);
        encodeDomainName(domainName, result);
        return result;
    }
}
