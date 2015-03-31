/*
 * Copyright 2014 The Netty Project
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

package io.netty.handler.codec.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.TextHeaders.EntryVisitor;
import io.netty.util.AsciiString;

import java.util.Map.Entry;

final class HttpHeadersEncoder implements EntryVisitor {

    private final ByteBuf buf;

    HttpHeadersEncoder(ByteBuf buf) {
        this.buf = buf;
    }

    @Override
    public boolean visit(Entry<CharSequence, CharSequence> entry) throws Exception {
        final CharSequence name = entry.getKey();
        final CharSequence value = entry.getValue();
        final ByteBuf buf = this.buf;
        final int nameLen = name.length();
        final int valueLen = value.length();
        final int entryLen = nameLen + valueLen + 4;
        int offset = buf.writerIndex();
        buf.ensureWritable(entryLen);
        writeAscii(buf, offset, name, nameLen);
        offset += nameLen;
        buf.setByte(offset ++, ':');
        buf.setByte(offset ++, ' ');
        writeAscii(buf, offset, value, valueLen);
        offset += valueLen;
        buf.setByte(offset ++, '\r');
        buf.setByte(offset ++, '\n');
        buf.writerIndex(offset);
        return true;
    }

    private static void writeAscii(ByteBuf buf, int offset, CharSequence value, int valueLen) {
        if (value instanceof AsciiString) {
            writeAsciiString(buf, offset, (AsciiString) value, valueLen);
        } else {
            writeCharSequence(buf, offset, value, valueLen);
        }
    }

    private static void writeAsciiString(ByteBuf buf, int offset, AsciiString value, int valueLen) {
        ByteBufUtil.copy(value, 0, buf, offset, valueLen);
    }

    private static void writeCharSequence(ByteBuf buf, int offset, CharSequence value, int valueLen) {
        for (int i = 0; i < valueLen; i ++) {
            buf.setByte(offset ++, c2b(value.charAt(i)));
        }
    }

    private static int c2b(char ch) {
        return ch < 256? (byte) ch : '?';
    }
}
