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
import io.netty.handler.codec.TooLongFrameException;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.UnstableApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Decoder for SMTP responses.
 */
@UnstableApi
public final class SmtpResponseDecoder extends LineBasedFrameDecoder {

    private static final int DEFAULT_MAX_RESPONSE_SIZE = 64 * 1024;

    // Charged per accumulated line so the limit bounds retained heap rather than payload bytes alone.
    private static final int DETAIL_ENTRY_OVERHEAD = 48;

    private final int maxResponseSize;

    private List<CharSequence> details;
    private long responseSize;

    /**
     * Creates a new instance that enforces the given {@code maxLineLength} and a default limit of 64 KiB on the
     * accumulated size of a multi-line response.
     *
     * @param maxLineLength the maximum length of a single response line, in bytes.
     */
    public SmtpResponseDecoder(int maxLineLength) {
        this(maxLineLength, DEFAULT_MAX_RESPONSE_SIZE);
    }

    /**
     * Creates a new instance that enforces the given {@code maxLineLength} and {@code maxResponseSize}.
     * <p>
     * The lines of a multi-line response are buffered until its terminating line arrives.
     * {@code maxResponseSize} bounds that buffering. Once it is exceeded, decoding fails with a
     * {@link TooLongFrameException}. The limit is approximate and tracks retained memory rather than bytes
     * received, as it charges each buffered line a fixed object overhead in addition to its detail bytes, and
     * ignores the response code, the separator and the terminating line.
     *
     * @param maxLineLength   the maximum length of a single response line, in bytes.
     * @param maxResponseSize the maximum accumulated size of a multi-line response, in bytes.
     */
    public SmtpResponseDecoder(int maxLineLength, int maxResponseSize) {
        super(maxLineLength);
        this.maxResponseSize = ObjectUtil.checkPositive(maxResponseSize, "maxResponseSize");
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
                reset();
                throw newDecoderException(buffer, readerIndex, readable);
            }
            final int code = parseCode(frame);
            final int separator = frame.readByte();
            final CharSequence detail = frame.isReadable() ? frame.toString(CharsetUtil.US_ASCII) : null;

            List<CharSequence> details = this.details;

            switch (separator) {
            case ' ':
                // Marks the end of a response.
                reset();
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
                    responseSize += (long) detail.length() + DETAIL_ENTRY_OVERHEAD;
                    if (responseSize > maxResponseSize) {
                        reset();
                        throw new TooLongFrameException("SMTP response exceeds " + maxResponseSize + " bytes");
                    }
                    if (details == null) {
                        // Using initial capacity as it is very unlikely that we will receive a multi-line response
                        // with more then 3 lines.
                        this.details = details = new ArrayList<CharSequence>(4);
                    }
                    details.add(detail);
                }
                break;
            default:
                reset();
                throw newDecoderException(buffer, readerIndex, readable);
            }
        } finally {
            frame.release();
        }
        return null;
    }

    private void reset() {
        this.details = null;
        this.responseSize = 0;
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
