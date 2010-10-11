/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.frame;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;

/**
 * A decoder that splits the received {@link ChannelBuffer}s by the fixed number
 * of bytes. For example, if you received the following four fragmented packets:
 * <pre>
 * +---+----+------+----+
 * | A | BC | DEFG | HI |
 * +---+----+------+----+
 * </pre>
 * A {@link FixedLengthFrameDecoder}{@code (3)} will decode them into the
 * following three packets with the fixed length:
 * <pre>
 * +-----+-----+-----+
 * | ABC | DEF | GHI |
 * +-----+-----+-----+
 * </pre>
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev:231 $, $Date:2008-06-12 16:44:50 +0900 (목, 12 6월 2008) $
 */
public class FixedLengthFrameDecoder extends FrameDecoder {

    private final int frameLength;

    /**
     * Creates a new instance.
     *
     * @param frameLength  the length of the frame
     */
    public FixedLengthFrameDecoder(int frameLength) {
        if (frameLength <= 0) {
            throw new IllegalArgumentException(
                    "frameLength must be a positive integer: " + frameLength);
        }
        this.frameLength = frameLength;
    }

    @Override
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer) throws Exception {
        if (buffer.readableBytes() < frameLength) {
            return null;
        } else {
            return buffer.readBytes(frameLength);
        }
    }
}
