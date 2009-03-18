/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.handler.codec.frame;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;

/**
 * A {@link FrameDecoder} which decodes the received {@link ChannelBuffer}s
 * into the {@link ChannelBuffer}s split by one or more delimiters.  It is
 * particularly useful for decoding the frames which ends with a delimiter
 * such as {@link Delimiters#nulDelimiter() NUL} or
 *         {@linkplain Delimiters#lineDelimiter() newline characters}.
 *
 * <h3>Predefined delimiters</h3>
 * <p>
 * {@link Delimiters} defines frequently used delimiters for convenience' sake.
 *
 * <h3>Specifying more than one delimiter</h3>
 * <p>
 * {@link DelimiterBasedFrameDecoder} allows you to specify more than one
 * delimiter.  If more than one delimiter is found in the buffer, it chooses
 * the delimiter which produces the shortest frame.  For example, if you have
 * the following data in the buffer:
 * <pre>
 * +--------------+
 * | ABC\nDEF\r\n |
 * +--------------+
 * </pre>
 * a {@link DelimiterBasedFrameDecoder}{@code (}{@link Delimiters#lineDelimiter() Delimiters.lineDelimiter()}{@code )}
 * will choose {@code '\n'} as the first delimiter and produce two frames:
 * <pre>
 * +-----+-----+
 * | ABC | DEF |
 * +-----+-----+
 * </pre>
 * rather than incorrectly choosing {@code '\r\n'} as the first delimiter:
 * <pre>
 * +----------+
 * | ABC\nDEF |
 * +----------+
 * </pre>
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev:231 $, $Date:2008-06-12 16:44:50 +0900 (목, 12 6월 2008) $
 *
 * @apiviz.landmark
 * @apiviz.uses org.jboss.netty.handler.codec.frame.Delimiters - - optional yet useful
 */
public class DelimiterBasedFrameDecoder extends FrameDecoder {

    private final ChannelBuffer[] delimiters;
    private final int maxFrameLength;
    private final boolean stripDelimiter;
    private volatile boolean discardingTooLongFrame;
    private volatile long tooLongFrameLength;

    /**
     * Creates a new instance.
     *
     * @param maxFrameLength  the maximum length of the decoded frame.
     *                        A {@link TooLongFrameException} is thrown if
     *                        the length of the frame exceeds this value.
     * @param delimiter  the delimiter
     */
    public DelimiterBasedFrameDecoder(int maxFrameLength, ChannelBuffer delimiter) {
        this(maxFrameLength, true, delimiter);
    }

    /**
     * Creates a new instance.
     *
     * @param maxFrameLength  the maximum length of the decoded frame.
     *                        A {@link TooLongFrameException} is thrown if
     *                        the length of the frame exceeds this value.
     * @param stripDelimiter  whether the decoded frame should strip out the
     *                        delimiter or not
     * @param delimiter  the delimiter
     */
    public DelimiterBasedFrameDecoder(
            int maxFrameLength, boolean stripDelimiter, ChannelBuffer delimiter) {
        validateMaxFrameLength(maxFrameLength);
        validateDelimiter(delimiter);
        delimiters = new ChannelBuffer[] {
                delimiter.slice(
                        delimiter.readerIndex(), delimiter.readableBytes())
        };
        this.maxFrameLength = maxFrameLength;
        this.stripDelimiter = stripDelimiter;
    }

    /**
     * Creates a new instance.
     *
     * @param maxFrameLength  the maximum length of the decoded frame.
     *                        A {@link TooLongFrameException} is thrown if
     *                        the length of the frame exceeds this value.
     * @param delimiters  the delimiters
     */
    public DelimiterBasedFrameDecoder(int maxFrameLength, ChannelBuffer... delimiters) {
        this(maxFrameLength, true, delimiters);
    }

    /**
     * Creates a new instance.
     *
     * @param maxFrameLength  the maximum length of the decoded frame.
     *                        A {@link TooLongFrameException} is thrown if
     *                        the length of the frame exceeds this value.
     * @param stripDelimiter  whether the decoded frame should strip out the
     *                        delimiter or not
     * @param delimiters  the delimiters
     */
    public DelimiterBasedFrameDecoder(
            int maxFrameLength, boolean stripDelimiter, ChannelBuffer... delimiters) {
        validateMaxFrameLength(maxFrameLength);
        if (delimiters == null) {
            throw new NullPointerException("delimiters");
        }
        if (delimiters.length == 0) {
            throw new IllegalArgumentException("empty delimiters");
        }
        this.delimiters = new ChannelBuffer[delimiters.length];
        for (int i = 0; i < delimiters.length; i ++) {
            ChannelBuffer d = delimiters[i];
            validateDelimiter(d);
            this.delimiters[i] = d.slice(d.readerIndex(), d.readableBytes());
        }
        this.maxFrameLength = maxFrameLength;
        this.stripDelimiter = stripDelimiter;
    }

    @Override
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer) throws Exception {
        // Try all delimiters and choose the delimiter which yields the shortest frame.
        int minFrameLength = Integer.MAX_VALUE;
        ChannelBuffer minDelim = null;
        for (ChannelBuffer delim: delimiters) {
            int frameLength = indexOf(buffer, delim);
            if (frameLength >= 0 && frameLength < minFrameLength) {
                minFrameLength = frameLength;
                minDelim = delim;
            }
        }

        if (minDelim != null) {
            int minDelimLength = minDelim.capacity();
            ChannelBuffer frame;

            if (discardingTooLongFrame) {
                // We've just finished discarding a very large frame.
                // Throw an exception and go back to the initial state.
                long tooLongFrameLength = this.tooLongFrameLength;
                this.tooLongFrameLength = 0L;
                discardingTooLongFrame = false;
                buffer.skipBytes(minFrameLength + minDelimLength);
                fail(tooLongFrameLength + minFrameLength + minDelimLength);
            }

            if (minFrameLength > maxFrameLength) {
                // Discard read frame.
                buffer.skipBytes(minFrameLength + minDelimLength);
                fail(minFrameLength);
            }

            if (stripDelimiter) {
                frame = buffer.readBytes(minFrameLength);
                buffer.skipBytes(minDelimLength);
            } else {
                frame = buffer.readBytes(minFrameLength + minDelimLength);
            }

            return frame;
        } else {
            if (buffer.readableBytes() > maxFrameLength) {
                // Discard the content of the buffer until a delimiter is found.
                tooLongFrameLength = buffer.readableBytes();
                buffer.skipBytes(buffer.readableBytes());
                discardingTooLongFrame = true;
            }

            return null;
        }
    }

    private void fail(long frameLength) throws TooLongFrameException {
        throw new TooLongFrameException(
                "The frame length exceeds " + maxFrameLength + ": " + frameLength);
    }

    /**
     * Returns the number of bytes between the readerIndex of the haystack and
     * the first needle found in the haystack.  -1 is returned if no needle is
     * found in the haystack.
     */
    private static int indexOf(ChannelBuffer haystack, ChannelBuffer needle) {
        for (int i = haystack.readerIndex(); i < haystack.writerIndex(); i ++) {
            int haystackIndex = i;
            int needleIndex;
            for (needleIndex = 0; needleIndex < needle.capacity(); needleIndex ++) {
                if (haystack.getByte(haystackIndex) != needle.getByte(needleIndex)) {
                    break;
                } else {
                    haystackIndex ++;
                    if (haystackIndex == haystack.writerIndex() &&
                        needleIndex != needle.capacity() - 1) {
                        return -1;
                    }
                }
            }

            if (needleIndex == needle.capacity()) {
                // Found the needle from the haystack!
                return i - haystack.readerIndex();
            }
        }
        return -1;
    }

    private static void validateDelimiter(ChannelBuffer delimiter) {
        if (delimiter == null) {
            throw new NullPointerException("delimiter");
        }
        if (!delimiter.readable()) {
            throw new IllegalArgumentException("empty delimiter");
        }
    }

    private static void validateMaxFrameLength(int maxFrameLength) {
        if (maxFrameLength <= 0) {
            throw new IllegalArgumentException(
                    "maxFrameLength must be a positive integer: " +
                    maxFrameLength);
        }
    }
}
