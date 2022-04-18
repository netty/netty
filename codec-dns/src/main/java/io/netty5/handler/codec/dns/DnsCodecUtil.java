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

package io.netty5.handler.codec.dns;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.handler.codec.CorruptedFrameException;
import io.netty5.util.CharsetUtil;

import java.nio.charset.StandardCharsets;

import static io.netty5.buffer.api.DefaultBufferAllocators.offHeapAllocator;
import static io.netty5.buffer.api.DefaultBufferAllocators.onHeapAllocator;
import static io.netty5.handler.codec.dns.DefaultDnsRecordDecoder.ROOT;

final class DnsCodecUtil {
    private DnsCodecUtil() {
        // Util class
    }

    static void encodeDomainName(String name, Buffer buf) {
        if (ROOT.equals(name)) {
            // Root domain
            buf.ensureWritable(1);
            buf.writeByte((byte) 0);
            return;
        }

        // Buffer size: For every name part, a dot is replaced by a length, with +1 for terminal NUL byte.
        buf.ensureWritable(name.length() + 1);

        final String[] labels = name.split("\\.");
        for (String label : labels) {
            final int labelLen = label.length();
            if (labelLen == 0) {
                // zero-length label means the end of the name.
                break;
            }
            buf.writeByte((byte) labelLen);
            buf.writeBytes(label.getBytes(StandardCharsets.US_ASCII));
        }

        buf.writeByte((byte) 0); // marks end of name field
    }

    static String decodeDomainName(Buffer in) {
        int position = -1;
        int checked = 0;
        final int end = in.writerOffset();
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
        while (in.readableBytes() > 0) {
            final int len = in.readUnsignedByte();
            final boolean pointer = (len & 0xc0) == 0xc0;
            if (pointer) {
                if (position == -1) {
                    position = in.readerOffset() + 1;
                }

                if (in.readableBytes() == 0) {
                    throw new CorruptedFrameException("truncated pointer in a name");
                }

                final int next = (len & 0x3f) << 8 | in.readUnsignedByte();
                if (next >= end) {
                    throw new CorruptedFrameException("name has an out-of-range pointer");
                }
                in.readerOffset(next);

                // check for loops
                checked += 2;
                if (checked >= end) {
                    throw new CorruptedFrameException("name contains a loop.");
                }
            } else if (len != 0) {
                if (in.readableBytes() < len) {
                    throw new CorruptedFrameException("truncated label in a name");
                }
                byte[] nameBytes = new byte[len];
                in.readBytes(nameBytes, 0, len);
                name.append(new String(nameBytes, CharsetUtil.UTF_8)).append('.');
            } else { // len == 0
                break;
            }
        }

        if (position != -1) {
            in.readerOffset(position);
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
    static Buffer decompressDomainName(Buffer compression) {
        String domainName = decodeDomainName(compression);
        BufferAllocator allocator = compression.isDirect()? offHeapAllocator() : onHeapAllocator();
        Buffer result = allocator.allocate(domainName.length() << 1);
        encodeDomainName(domainName, result);
        return result;
    }
}
