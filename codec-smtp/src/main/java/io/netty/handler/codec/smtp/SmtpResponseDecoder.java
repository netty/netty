/*
 * Copyright 2016 The Netty Project
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
package io.netty.handler.codec.smtp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.UnstableApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Decoder for SMTP responses.
 */
@UnstableApi
public final class SmtpResponseDecoder extends LineBasedFrameDecoder {

    private List<CharSequence> details;

    /**
     * Creates a new instance that enforces the given {@code maxLineLength}.
     */
    public SmtpResponseDecoder(int maxLineLength) {
        super(maxLineLength);
    }

    @Override
    protected SmtpResponse decode(ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
        ByteBuf frame = (ByteBuf) super.decode(ctx, buffer);
        if (frame == null) {
            // No full line received yet.
            return null;
        }
        try {
            final int readable = frame.readableBytes();
            final int readerIndex = frame.readerIndex();
            if (readable < 3) {
                throw newDecoderException(buffer, readerIndex, readable);
            }
            final int code = parseCode(frame);
            final int separator = frame.readByte();
            final CharSequence detail = frame.isReadable() ? frame.toString(CharsetUtil.US_ASCII) : null;

            List<CharSequence> details = this.details;

            switch (separator) {
            case ' ':
                // Marks the end of a response.
                this.details = null;
                if (details != null) {
                    if (detail != null) {
                        details.add(detail);
                    }
                } else {
                    if (detail == null) {
                        details = Collections.emptyList();
                    } else {
                        details = Collections.singletonList(detail);
                    }
                }
                return new DefaultSmtpResponse(code, details);
            case '-':
                // Multi-line response.
                if (detail != null) {
                    if (details == null) {
                        // Using initial capacity as it is very unlikely that we will receive a multi-line response
                        // with more then 3 lines.
                        this.details = details = new ArrayList<CharSequence>(4);
                    }
                    details.add(detail);
                }
                break;
            default:
                throw newDecoderException(buffer, readerIndex, readable);
            }
        } finally {
            frame.release();
        }
        return null;
    }

    private static DecoderException newDecoderException(ByteBuf buffer, int readerIndex, int readable) {
        return new DecoderException(
                "Received invalid line: '" + buffer.toString(readerIndex, readable, CharsetUtil.US_ASCII) + '\'');
    }

    /**
     * Parses the io.netty.handler.codec.smtp code without any allocation, which is three digits.
     */
    private static int parseCode(ByteBuf buffer) {
        final int first = parseNumber(buffer.readByte()) * 100;
        final int second = parseNumber(buffer.readByte()) * 10;
        final int third = parseNumber(buffer.readByte());
        return first + second + third;
    }

    private static int parseNumber(byte b) {
        return Character.digit((char) b, 10);
    }
}
