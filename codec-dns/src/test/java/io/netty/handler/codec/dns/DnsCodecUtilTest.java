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
import io.netty.handler.codec.TooLongFrameException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class DnsCodecUtilTest {

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
