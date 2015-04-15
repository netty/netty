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

package io.netty.handler.codec;


import java.util.Map.Entry;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.AsciiString;

public final class AsciiHeadersEncoder {

    /**
     * The separator characters to insert between a header name and a header value.
     */
    public enum SeparatorType {
        /**
         * {@code ':'}
         */
        COLON,
        /**
         * {@code ': '}
         */
        COLON_SPACE,
    }

    /**
     * The newline characters to insert between header entries.
     */
    public enum NewlineType {
        /**
         * {@code '\n'}
         */
        LF,
        /**
         * {@code '\r\n'}
         */
        CRLF
    }

    private final ByteBuf buf;
    private final SeparatorType separatorType;
    private final NewlineType newlineType;

    public AsciiHeadersEncoder(ByteBuf buf) {
        this(buf, SeparatorType.COLON_SPACE, NewlineType.CRLF);
    }

    public AsciiHeadersEncoder(ByteBuf buf, SeparatorType separatorType, NewlineType newlineType) {
        if (buf == null) {
            throw new NullPointerException("buf");
        }
        if (separatorType == null) {
            throw new NullPointerException("separatorType");
        }
        if (newlineType == null) {
            throw new NullPointerException("newlineType");
        }

        this.buf = buf;
        this.separatorType = separatorType;
        this.newlineType = newlineType;
    }

    public void encode(Entry<CharSequence, CharSequence> entry) {
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

        switch (separatorType) {
            case COLON:
                buf.setByte(offset ++, ':');
                break;
            case COLON_SPACE:
                buf.setByte(offset ++, ':');
                buf.setByte(offset ++, ' ');
                break;
            default:
                throw new Error();
        }

        writeAscii(buf, offset, value, valueLen);
        offset += valueLen;

        switch (newlineType) {
            case LF:
                buf.setByte(offset ++, '\n');
                break;
            case CRLF:
                buf.setByte(offset ++, '\r');
                buf.setByte(offset ++, '\n');
                break;
            default:
                throw new Error();
        }

        buf.writerIndex(offset);
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
