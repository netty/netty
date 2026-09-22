/*
 * Copyright 2026 The Netty Project
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
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.TooLongFrameException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DnsCodecUtilTest {

    @Test
    void calculateMaxNameLengthIsBoundedRegardlessOfRemainingBytes() {
        // A decoded name can never exceed MAX_DOMAIN_NAME_LENGTH characters, so the working buffer must stay
        // bounded even when the message has a huge number of bytes still readable after the name (e.g. many
        // more records in a large DNS message). Without this bound, a single message could force an oversized
        // allocation for every name decoded from it.
        assertEquals(DnsCodecUtil.MAX_DOMAIN_NAME_LENGTH,
                DnsCodecUtil.calculateMaxNameLength(Integer.MAX_VALUE));
        assertEquals(DnsCodecUtil.MAX_DOMAIN_NAME_LENGTH,
                DnsCodecUtil.calculateMaxNameLength(8 * 1024 * 1024));
        assertEquals(DnsCodecUtil.MAX_DOMAIN_NAME_LENGTH,
                DnsCodecUtil.calculateMaxNameLength(DnsCodecUtil.MAX_DOMAIN_NAME_LENGTH + 1));

        // For buffers that cannot possibly contain more than a valid name, behavior is unchanged.
        assertEquals(5, DnsCodecUtil.calculateMaxNameLength(5));
        assertEquals(0, DnsCodecUtil.calculateMaxNameLength(0));
    }

    @Test
    void rejectTooLongLabelWhileDecoding() {
        final ByteBuf buf = Unpooled.buffer(256);
        // 63 is the maximum label length
        writeLabel(buf, 64);
        writeLabel(buf, 3);
        buf.writeByte(0);

        assertThrows(TooLongFrameException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                DnsCodecUtil.decodeDomainName(buf);
            }
        });
        buf.release();
    }

    @Test
    void rejectTooLongDomainNameWhileDecoding() {
        // 255 is the maximum domain name
        final ByteBuf buf = Unpooled.buffer(512);
        writeLabel(buf, 50);
        writeLabel(buf, 50);
        writeLabel(buf, 50);
        writeLabel(buf, 50);
        writeLabel(buf, 56);
        buf.writeByte(0);

        assertThrows(TooLongFrameException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                DnsCodecUtil.decodeDomainName(buf);
            }
        });
        buf.release();
    }

    @Test
    void acceptMaxCompressionPointers() {
        ByteBuf buf = newPointerChain(DnsCodecUtil.MAX_COMPRESSION_POINTERS);
        try {
            assertEquals("abc.", DnsCodecUtil.decodeDomainName(buf));
            // The reader index must be just after the first pointer.
            assertEquals(buf.writerIndex(), buf.readerIndex());
        } finally {
            buf.release();
        }
    }

    @Test
    void rejectTooManyCompressionPointers() {
        final ByteBuf buf = newPointerChain(DnsCodecUtil.MAX_COMPRESSION_POINTERS + 1);
        try {
            assertThrows(CorruptedFrameException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    DnsCodecUtil.decodeDomainName(buf);
                }
            });
        } finally {
            buf.release();
        }
    }

    @Test
    void rejectCompressionPointerLoop() {
        final ByteBuf buf = Unpooled.buffer();
        // A pointer to itself.
        buf.writeShort(0xc000);
        try {
            assertThrows(CorruptedFrameException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    DnsCodecUtil.decodeDomainName(buf);
                }
            });
        } finally {
            buf.release();
        }
    }

    /**
     * Writes the name {@code abc.} followed by a chain of {@code pointers} compression pointers, each pointing to the
     * previous one, and positions the reader index at the last pointer so that decoding it follows all of them.
     */
    private static ByteBuf newPointerChain(int pointers) {
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(3).writeBytes(new byte[] { 'a', 'b', 'c' }).writeByte(0);
        int previous = 0;
        for (int i = 0; i < pointers; i++) {
            int current = buf.writerIndex();
            buf.writeShort(0xc000 | previous);
            previous = current;
        }
        buf.readerIndex(previous);
        return buf;
    }

    @Test
    void rejectTooLongLabelWhileEncoding() {
        final ByteBuf buf = Unpooled.buffer(256);
        // 63 is the maximum label length
        final StringBuilder sb = new StringBuilder();
        appendLabel(sb, 64);
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                DnsCodecUtil.encodeDomainName(sb.toString(), buf);
            }
        });
        buf.release();
    }

    @Test
    void rejectEmptyLabelWhileEncoding() {
        final ByteBuf buf = Unpooled.buffer(256);
        // 63 is the maximum label length
        final StringBuilder sb = new StringBuilder();
        appendLabel(sb, 5);
        appendLabel(sb, 0);
        appendLabel(sb, 5);
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                DnsCodecUtil.encodeDomainName(sb.toString(), buf);
            }
        });
        buf.release();
    }

    @Test
    void rejectTooLongDomainNameWhileEncoding() {
        final ByteBuf buf = Unpooled.buffer(256);
        // 255 is the maximum domain name
        final StringBuilder sb = new StringBuilder();
        appendLabel(sb, 50);
        appendLabel(sb, 50);
        appendLabel(sb, 50);
        appendLabel(sb, 50);
        appendLabel(sb, 56);

        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                DnsCodecUtil.encodeDomainName(sb.toString(), buf);
            }
        });
        buf.release();
    }

    private static void writeLabel(ByteBuf buf, int length) {
        buf.writeByte(length);
        for (int i = 1; i <= length; i++) {
            buf.writeByte(i);
        }
    }

    private static void appendLabel(StringBuilder sb, int length) {
        for (int i = 0; i < length; i++) {
            sb.append('a');
        }
        sb.append('.');
    }
}
