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
package io.netty.handler.codec.stomp;

import java.util.Iterator;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.util.CharsetUtil;

/**
 * Encodes a {@link StompFrame} or a {@link FullStompFrame} or a {@link StompContent} into a {@link ByteBuf}.
 */
public class StompEncoder extends MessageToMessageEncoder<StompObject> {

    @Override
    protected void encode(ChannelHandlerContext ctx, StompObject msg, List<Object> out) throws Exception {
        if (msg instanceof  FullStompFrame) {
            FullStompFrame frame = (FullStompFrame) msg;
            ByteBuf frameBuf = encodeFrame(frame, ctx);
            out.add(frameBuf);
            ByteBuf contentBuf = encodeContent(frame, ctx);
            out.add(contentBuf);
        } else if (msg instanceof StompFrame) {
            StompFrame frame = (StompFrame) msg;
            ByteBuf buf = encodeFrame(frame, ctx);
            out.add(buf);
        } else if (msg instanceof StompContent) {
            StompContent stompContent = (StompContent) msg;
            ByteBuf buf = encodeContent(stompContent, ctx);
            out.add(buf);
        }
    }

    private ByteBuf encodeContent(StompContent content, ChannelHandlerContext ctx) {
        if (content instanceof LastStompContent) {
            ByteBuf buf = ctx.alloc().buffer(content.content().readableBytes() + 1);
            buf.writeBytes(content.content());
            buf.writeByte(StompConstants.NULL);
            return buf;
        } else {
            ByteBuf buf = ctx.alloc().buffer(content.content().readableBytes());
            buf.writeBytes(content.content());
            return buf;
        }
    }

    private ByteBuf encodeFrame(StompFrame frame, ChannelHandlerContext ctx) {
        ByteBuf buf = ctx.alloc().buffer();

        buf.writeBytes(frame.command().toString().getBytes(CharsetUtil.US_ASCII));
        buf.writeByte(StompConstants.CR).writeByte(StompConstants.LF);

        StompHeaders headers = frame.headers();
        for (Iterator<String> iterator = headers.keySet().iterator(); iterator.hasNext();) {
            String key = iterator.next();
            List<String> values = headers.getAll(key);
            for (Iterator<String> stringIterator = values.iterator(); stringIterator.hasNext();) {
                String value = stringIterator.next();
                buf.writeBytes(key.getBytes(CharsetUtil.US_ASCII)).
                        writeByte(StompConstants.COLON).writeBytes(value.getBytes(CharsetUtil.US_ASCII));
                buf.writeByte(StompConstants.CR).writeByte(StompConstants.LF);
            }
        }
        buf.writeByte(StompConstants.CR).writeByte(StompConstants.LF);
        return buf;
    }

}
