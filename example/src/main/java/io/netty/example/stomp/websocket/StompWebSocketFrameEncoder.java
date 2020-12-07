/*
 * Copyright 2020 The Netty Project
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
package io.netty.example.stomp.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.stomp.LastStompContentSubframe;
import io.netty.handler.codec.stomp.StompContentSubframe;
import io.netty.handler.codec.stomp.StompFrame;
import io.netty.handler.codec.stomp.StompHeaders;
import io.netty.handler.codec.stomp.StompHeadersSubframe;
import io.netty.handler.codec.stomp.StompSubframe;
import io.netty.handler.codec.stomp.StompSubframeEncoder;

import java.util.List;

public class StompWebSocketFrameEncoder extends StompSubframeEncoder {

    @Override
    public void encode(ChannelHandlerContext ctx, StompSubframe msg, List<Object> out) throws Exception {
        super.encode(ctx, msg, out);
    }

    @Override
    protected WebSocketFrame convertFullFrame(StompFrame original, ByteBuf encoded) {
        if (isTextFrame(original)) {
            return new TextWebSocketFrame(encoded);
        }

        return new BinaryWebSocketFrame(encoded);
    }

    @Override
    protected WebSocketFrame convertHeadersSubFrame(StompHeadersSubframe original, ByteBuf encoded) {
        if (isTextFrame(original)) {
            return new TextWebSocketFrame(false, 0, encoded);
        }

        return new BinaryWebSocketFrame(false, 0, encoded);
    }

    @Override
    protected WebSocketFrame convertContentSubFrame(StompContentSubframe original, ByteBuf encoded) {
        if (original instanceof LastStompContentSubframe) {
            return new ContinuationWebSocketFrame(true, 0, encoded);
        }

        return new ContinuationWebSocketFrame(false, 0, encoded);
    }

    private static boolean isTextFrame(StompHeadersSubframe headersSubframe) {
        String contentType = headersSubframe.headers().getAsString(StompHeaders.CONTENT_TYPE);
        return contentType != null && (contentType.startsWith("text") || contentType.startsWith("application/json"));
    }
}
