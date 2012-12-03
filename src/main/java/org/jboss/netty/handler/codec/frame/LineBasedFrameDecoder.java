/*
 * Copyright 2012 The Netty Project
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
package org.jboss.netty.handler.codec.frame;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ChannelHandlerContext;

/**
 * A decoder that splits the received {@link ChannelBuffer}s on line endings.
 * <p>
 * Both {@code "\n"} and {@code "\r\n"} are handled.
 * For a more general delimiter-based decoder, see {@link DelimiterBasedFrameDecoder}.
 */
public class LineBasedFrameDecoder extends FrameDecoder {

    /** Maximum length of a frame we're willing to decode.  */
    private final int maxLength;
    /** Whether or not to throw an exception as soon as we exceed maxLength. */
    private final boolean failFast;
    private final boolean stripDelimiter;

    /** True if we're discarding input because we're already over maxLength.  */
    private boolean discarding;

    /**
     * Creates a new decoder.
     * @param maxLength  the maximum length of the decoded frame.
     *                   A {@link TooLongFrameException} is thrown if
     *                   the length of the frame exceeds this value.
     */
    public LineBasedFrameDecoder(final int maxLength) {
        this(maxLength, true, false);
    }

    /**
     * Creates a new decoder.
     * @param maxLength  the maximum length of the decoded frame.
     *                   A {@link TooLongFrameException} is thrown if
     *                   the length of the frame exceeds this value.
     * @param stripDelimiter  whether the decoded frame should strip out the
     *                        delimiter or not
     * @param failFast  If <tt>true</tt>, a {@link TooLongFrameException} is
     *                  thrown as soon as the decoder notices the length of the
     *                  frame will exceed <tt>maxFrameLength</tt> regardless of
     *                  whether the entire frame has been read.
     *                  If <tt>false</tt>, a {@link TooLongFrameException} is
     *                  thrown after the entire frame that exceeds
     *                  <tt>maxFrameLength</tt> has been read.
     */
    public LineBasedFrameDecoder(final int maxLength, final boolean stripDelimiter, final boolean failFast) {
        this.maxLength = maxLength;
        this.failFast = failFast;
        this.stripDelimiter = stripDelimiter;
    }

    @Override
    protected Object decode(final ChannelHandlerContext ctx,
                            final Channel channel,
                            final ChannelBuffer buffer) throws Exception {
        final int eol = findEndOfLine(buffer);
        if (eol != -1) {
            final ChannelBuffer frame;
            final int length = eol - buffer.readerIndex();
            assert length >= 0: "Invalid length=" + length;
            if (discarding) {
                frame = null;
                buffer.skipBytes(length);
                if (!failFast) {
                    fail(ctx, "over " + (maxLength + length) + " bytes");
                }
            } else {
                int delimLength;
                final byte delim = buffer.getByte(buffer.readerIndex() + length);
                if (delim == '\r') {
                    delimLength = 2;  // Skip the \r\n.
                } else {
                    delimLength = 1;
                }
                if (stripDelimiter) {
                    frame = extractFrame(buffer, buffer.readerIndex(), length);
                } else {
                    frame = extractFrame(buffer, buffer.readerIndex(), length + delimLength);
                }
                buffer.skipBytes(length + delimLength);
            }
            return frame;
        }

        final int buffered = buffer.readableBytes();
        if (!discarding && buffered > maxLength) {
            discarding = true;
            if (failFast) {
                fail(ctx, buffered + " bytes buffered already");
            }
        }
        if (discarding) {
            buffer.skipBytes(buffer.readableBytes());
        }
        return null;
    }

    private void fail(final ChannelHandlerContext ctx, final String msg) {
        Channels.fireExceptionCaught(ctx.getChannel(),
            new TooLongFrameException("Frame length exceeds " + maxLength + " ("
                                      + msg + ')'));
    }

    /**
     * Returns the index in the buffer of the end of line found.
     * Returns -1 if no end of line was found in the buffer.
     */
    private static int findEndOfLine(final ChannelBuffer buffer) {
        final int n = buffer.writerIndex();
        for (int i = buffer.readerIndex(); i < n; i ++) {
            final byte b = buffer.getByte(i);
            if (b == '\n') {
                return i;
            } else if (b == '\r' && i < n - 1 && buffer.getByte(i + 1) == '\n') {
                return i;  // \r\n
            }
        }
        return -1;  // Not found.
    }

}
