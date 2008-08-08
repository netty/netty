/*
 * Copyright (C) 2008  Trustin Heuiseung Lee
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, 5th Floor, Boston, MA 02110-1301 USA
 */
package net.gleamynode.netty.handler.codec.frame;

import net.gleamynode.netty.buffer.ChannelBuffer;
import net.gleamynode.netty.channel.Channel;
import net.gleamynode.netty.channel.ChannelHandlerContext;

/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev:231 $, $Date:2008-06-12 16:44:50 +0900 (목, 12 6월 2008) $
 *
 * @apiviz.uses net.gleamynode.netty.handler.codec.frame.Delimiters - - optional yet useful
 */
public class DelimiterBasedFrameDecoder extends FrameDecoder {

    private final ChannelBuffer[] delimiters;
    private final int maxFrameLength;

    public DelimiterBasedFrameDecoder(int maxFrameLength, ChannelBuffer delimiter) {
        validateMaxFrameLength(maxFrameLength);
        validateDelimiter(delimiter);
        delimiters = new ChannelBuffer[] {
                delimiter.slice(
                        delimiter.readerIndex(), delimiter.readableBytes())
        };
        this.maxFrameLength = maxFrameLength;
    }

    public DelimiterBasedFrameDecoder(int maxFrameLength, ChannelBuffer... delimiters) {
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
    }

    @Override
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer) throws Exception {
        // Try all delimiters.
        for (ChannelBuffer delim: delimiters) {
            int delimIndex = indexOf(buffer, delim);
            if (delimIndex > 0) {
                ChannelBuffer frame = buffer.readBytes(delimIndex);
                if (frame.capacity() > maxFrameLength) {
                    fail();
                }
                buffer.skipBytes(delim.capacity());
                return frame;
            } else if (delimIndex == 0) {
                buffer.skipBytes(delim.capacity());
                return ChannelBuffer.EMPTY_BUFFER;
            }
        }

        if (buffer.readableBytes() > maxFrameLength) {
            fail();
        }
        return null;
    }

    private void fail() throws TooLongFrameException {
        throw new TooLongFrameException(
                "The frame length exceeds " + maxFrameLength);
    }

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
                return i;
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
