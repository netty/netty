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

import net.gleamynode.netty.array.ByteArray;
import net.gleamynode.netty.array.ByteArrayBuffer;
import net.gleamynode.netty.channel.Channel;
import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.pipeline.PipeContext;

/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev:231 $, $Date:2008-06-12 16:44:50 +0900 (목, 12 6월 2008) $
 *
 */
public class DelimiterBasedFrameDecoder extends FrameDecoder {

    private final ByteArray[] delimiters;
    private final int maxFrameLength;

    public DelimiterBasedFrameDecoder(int maxFrameLength, ByteArray delimiter) {
        validateMaxFrameLength(maxFrameLength);
        validateDelimiter(delimiter);
        delimiters = new ByteArray[] { delimiter };
        this.maxFrameLength = maxFrameLength;
    }

    public DelimiterBasedFrameDecoder(int maxFrameLength, ByteArray... delimiters) {
        validateMaxFrameLength(maxFrameLength);
        if (delimiters == null) {
            throw new NullPointerException("delimiters");
        }
        if (delimiters.length == 0) {
            throw new IllegalArgumentException("empty delimiters");
        }
        this.delimiters = new ByteArray[delimiters.length];
        for (int i = 0; i < delimiters.length; i ++) {
            ByteArray d = delimiters[i];
            validateDelimiter(d);
            this.delimiters[i] = d;
        }
        this.maxFrameLength = maxFrameLength;
    }

    @Override
    protected Object readFrame(
            PipeContext<ChannelEvent> ctx, Channel channel, ByteArrayBuffer buffer) throws Exception {
        // Try all delimiters.
        for (ByteArray delim: delimiters) {
            int delimIndex = indexOf(buffer, delim);
            if (delimIndex > buffer.firstIndex()) {
                ByteArray frame = buffer.read(delimIndex - buffer.firstIndex());
                if (frame.length() > maxFrameLength) {
                    fail();
                }
                buffer.skip(delim.length());
                return frame;
            } else if (delimIndex == buffer.firstIndex()) {
                buffer.skip(delim.length());
                return ByteArray.EMPTY_BUFFER;
            }
        }

        if (buffer.length() > maxFrameLength) {
            fail();
        }
        return null;
    }

    private void fail() throws TooLongFrameException {
        throw new TooLongFrameException(
                "The frame length exceeds " + maxFrameLength);
    }

    private static int indexOf(ByteArray haystack, ByteArray needle) {
        for (int i = haystack.firstIndex(); i < haystack.endIndex(); i ++) {
            int haystackIndex = i;
            int needleIndex = needle.firstIndex();
            for (; needleIndex < needle.endIndex(); needleIndex ++) {
                if (haystack.get8(haystackIndex) != needle.get8(needleIndex)) {
                    break;
                } else {
                    haystackIndex ++;
                    if (haystackIndex == haystack.endIndex() &&
                        needleIndex != needle.endIndex() - 1) {
                        return -1;
                    }
                }
            }

            if (needleIndex == needle.endIndex()) {
                // Found the needle from the haystack!
                return i;
            }
        }
        return -1;
    }

    private static void validateDelimiter(ByteArray delimiter) {
        if (delimiter == null) {
            throw new NullPointerException("delimiter");
        }
        if (delimiter.empty()) {
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
