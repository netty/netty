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

    // See https://datatracker.ietf.org/doc/html/rfc1035#section-2.3.4
    static final int MAX_DOMAIN_NAME_LENGTH = 255;

    // A decoded name is at most 255 characters and every label contributes at least 2 ("x."), so a valid name has
    // at most 127 labels and therefore never needs to follow more compression pointers than that. Bounding the hops
    // per name keeps the decoding cost of each name constant instead of proportional to the message size.
    static final int MAX_COMPRESSION_POINTERS = 127;

    // The RFC does not define a maximum number of records per section (the count is a 16-bit value). The cost of
    // decoding a single record is bounded (see MAX_DOMAIN_NAME_LENGTH and MAX_COMPRESSION_POINTERS), so this only
    // limits the total work per message. It is high enough that a full 64 KiB TCP message of minimal records with
    // compressed names (e.g. A records, 16 bytes each) still decodes.
    static final int MAX_RECORDS_PER_SECTION = 4096;

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
            if (totalLength > MAX_DOMAIN_NAME_LENGTH) {
                throw new IllegalArgumentException(
                        "DNS name exceeds maximum length of " + MAX_DOMAIN_NAME_LENGTH + ": " + name);
            }
            buf.writeByte(labelLen);
            ByteBufUtil.writeAscii(buf, label);
        }

        buf.writeByte(0); // marks end of name field
    }

    /**
     * Returns the initial capacity to use for the {@link StringBuilder} that accumulates a decoded domain name.
     * A decoded name can never exceed {@link #MAX_DOMAIN_NAME_LENGTH} characters (see the check in
     * {@link #decodeDomainName(ByteBuf)}), so the capacity must not scale with {@code readable}, the number of
     * bytes left in the whole message: doing so would let a message with a huge amount of trailing data (e.g.
     * many more records after this name) force an oversized allocation for every single name decoded from it.
     */
    static int calculateMaxNameLength(int readable) {
        return Math.min(readable, MAX_DOMAIN_NAME_LENGTH);
    }

    /**
     * Ensures the record count of a section, as announced in the header of a DNS message, does not exceed
     * {@link #MAX_RECORDS_PER_SECTION}.
     *
     * @throws TooLongFrameException if the count is too large.
     */
    static int checkRecordCount(DnsSection section, int count) {
        if (count > MAX_RECORDS_PER_SECTION) {
            throw new TooLongFrameException(
                    section + " record count must be <= " + MAX_RECORDS_PER_SECTION + " but was " + count);
        }
        return count;
    }

    static String decodeDomainName(ByteBuf in) {
        int position = -1;
        int pointers = 0;
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

        final StringBuilder name = new StringBuilder(calculateMaxNameLength(readable));
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

                // Bounds the work per name, which also terminates loops.
                if (++pointers > MAX_COMPRESSION_POINTERS) {
                    throw new CorruptedFrameException(
                            "name contains more than " + MAX_COMPRESSION_POINTERS + " compression pointers");
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
                if (name.length() > MAX_DOMAIN_NAME_LENGTH) {
                    throw new TooLongFrameException(
                            "domain name must be <= " + MAX_DOMAIN_NAME_LENGTH + " but was " + name.length());
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
        try {
            encodeDomainName(domainName, result);
        } catch (Throwable cause) {
            result.release();
            throw cause;
        }
        return result;
    }
}
