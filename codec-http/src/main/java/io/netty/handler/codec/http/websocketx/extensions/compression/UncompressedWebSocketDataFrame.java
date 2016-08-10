/*
 * Copyright 2016 The Netty Project
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
package io.netty.handler.codec.http.websocketx.extensions.compression;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketDataFrameContainer;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtension;

/**
 * A frame wrapper for selective WebSocket compression.
 * Wrap your frame with this class if you want it uncompressed.
 */
public final class UncompressedWebSocketDataFrame extends WebSocketFrame implements WebSocketDataFrameContainer {

    private final boolean isBinaryFrame;

    /**
     * Wrap the WebSocket frame to skip compression.
     *
     * @param frame the frame to skip compression.
     */
    public UncompressedWebSocketDataFrame(WebSocketFrame frame) {
        this(validateFrame(frame).isFinalFragment(), frame.rsv(), frame.content(),
                frame instanceof BinaryWebSocketFrame);
    }

    private static WebSocketFrame validateFrame(WebSocketFrame frame) {
        if (!((frame instanceof TextWebSocketFrame) || (frame instanceof BinaryWebSocketFrame))) {
            throw new IllegalArgumentException("Text frame or binary frame expected.");
        }
        // if RSV1 is set, it means the packet is already compressed.
        if ((frame.rsv() & WebSocketExtension.RSV1) != 0) {
            throw new IllegalStateException("This frame is already compressed.");
        }
        return frame;
    }

    private UncompressedWebSocketDataFrame(boolean isFinalFragment, int rsv, ByteBuf content,
                                           boolean isBinaryFrame) {
        super(isFinalFragment, rsv, content);
        this.isBinaryFrame = isBinaryFrame;
    }

    @Override
    public UncompressedWebSocketDataFrame replace(ByteBuf content) {
        return new UncompressedWebSocketDataFrame(isFinalFragment(), rsv(), content, isBinaryFrame);
    }

    @Override
    public WebSocketFrame extractDataFrame() {
        return isBinaryFrame ? new BinaryWebSocketFrame(isFinalFragment(), rsv(), content())
                : new TextWebSocketFrame(isFinalFragment(), rsv(), content());
    }
}
