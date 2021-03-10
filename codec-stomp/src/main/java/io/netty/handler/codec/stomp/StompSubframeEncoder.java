/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.stomp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;
import java.util.Map.Entry;

import static io.netty.handler.codec.stomp.StompConstants.*;

/**
 * Encodes a {@link StompFrame} or a {@link StompSubframe} into a {@link ByteBuf}.
 */
public class StompSubframeEncoder extends MessageToMessageEncoder<StompSubframe> {

    @Override
    protected void encode(ChannelHandlerContext ctx, StompSubframe msg, List<Object> out) throws Exception {
        if (msg instanceof StompFrame) {
            StompFrame stompFrame = (StompFrame) msg;
            ByteBuf buf = encodeFullFrame(stompFrame, ctx);

            out.add(convertFullFrame(stompFrame, buf));
        } else if (msg instanceof StompHeadersSubframe) {
            StompHeadersSubframe stompHeadersSubframe = (StompHeadersSubframe) msg;
            ByteBuf buf = ctx.alloc().buffer(headersSubFrameSize(stompHeadersSubframe));
            encodeHeaders(stompHeadersSubframe, buf);

            out.add(convertHeadersSubFrame(stompHeadersSubframe, buf));
        } else if (msg instanceof StompContentSubframe) {
            StompContentSubframe stompContentSubframe = (StompContentSubframe) msg;
            ByteBuf buf = encodeContent(stompContentSubframe, ctx);

            out.add(convertContentSubFrame(stompContentSubframe, buf));
        }
    }

    /**
     * An extension method to convert a STOMP encoded buffer to a different message type
     * based on an original {@link StompFrame} full frame.
     *
     * <p>By default an encoded buffer is returned as is.
     */
    protected Object convertFullFrame(StompFrame original, ByteBuf encoded) {
        return encoded;
    }

    /**
     * An extension method to convert a STOMP encoded buffer to a different message type
     * based on an original {@link StompHeadersSubframe} headers sub frame.
     *
     * <p>By default an encoded buffer is returned as is.
     */
    protected Object convertHeadersSubFrame(StompHeadersSubframe original, ByteBuf encoded) {
        return encoded;
    }

    /**
     * An extension method to convert a STOMP encoded buffer to a different message type
     * based on an original {@link StompHeadersSubframe} content sub frame.
     *
     * <p>By default an encoded buffer is returned as is.
     */
    protected Object convertContentSubFrame(StompContentSubframe original, ByteBuf encoded) {
        return encoded;
    }

    /**
     * Returns a heuristic size for headers (32 bytes per header line) + (2 bytes for colon and eol) + (additional
     * command buffer).
     */
    protected int headersSubFrameSize(StompHeadersSubframe headersSubframe) {
        int estimatedSize = headersSubframe.headers().size() * 34 + 48;
        if (estimatedSize < 128) {
            return 128;
        } else if (estimatedSize < 256) {
            return 256;
        }

        return estimatedSize;
    }

    private ByteBuf encodeFullFrame(StompFrame frame, ChannelHandlerContext ctx) {
        int contentReadableBytes = frame.content().readableBytes();
        ByteBuf buf = ctx.alloc().buffer(headersSubFrameSize(frame) + contentReadableBytes);
        encodeHeaders(frame, buf);

        if (contentReadableBytes > 0) {
            buf.writeBytes(frame.content());
        }

        return buf.writeByte(NUL);
    }

    private static void encodeHeaders(StompHeadersSubframe frame, ByteBuf buf) {
        ByteBufUtil.writeUtf8(buf, frame.command().toString());
        buf.writeByte(StompConstants.LF);

        for (Entry<CharSequence, CharSequence> entry : frame.headers()) {
            ByteBufUtil.writeUtf8(buf, entry.getKey());
            buf.writeByte(StompConstants.COLON);
            ByteBufUtil.writeUtf8(buf, entry.getValue());
            buf.writeByte(StompConstants.LF);
        }

        buf.writeByte(StompConstants.LF);
    }

    private static ByteBuf encodeContent(StompContentSubframe content, ChannelHandlerContext ctx) {
        if (content instanceof LastStompContentSubframe) {
            ByteBuf buf = ctx.alloc().buffer(content.content().readableBytes() + 1);
            buf.writeBytes(content.content());
            buf.writeByte(StompConstants.NUL);
            return buf;
        }

        return content.content().retain();
    }
}
