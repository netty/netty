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
package io.netty.handler.codec.http.websocketx.extensions.compression;

import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtension;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionFilter;

/**
 * Per-frame implementation of deflate decompressor.
 */
class PerFrameDeflateDecoder extends DeflateDecoder {

    /**
     * Constructor
     *
     * @param noContext true to disable context takeover.
     */
    PerFrameDeflateDecoder(boolean noContext) {
        super(noContext, WebSocketExtensionFilter.NEVER_SKIP);
    }

    /**
     * Constructor
     *
     * @param noContext true to disable context takeover.
     * @param extensionDecoderFilter extension decoder filter for per frame deflate decoder.
     */
    PerFrameDeflateDecoder(boolean noContext, WebSocketExtensionFilter extensionDecoderFilter) {
        super(noContext, extensionDecoderFilter);
    }

    @Override
    public boolean acceptInboundMessage(Object msg) throws Exception {
        if (!super.acceptInboundMessage(msg)) {
            return false;
        }

        WebSocketFrame wsFrame = (WebSocketFrame) msg;
        if (extensionDecoderFilter().mustSkip(wsFrame)) {
            return false;
        }

        return (msg instanceof TextWebSocketFrame || msg instanceof BinaryWebSocketFrame ||
                msg instanceof ContinuationWebSocketFrame) &&
               (wsFrame.rsv() & WebSocketExtension.RSV1) > 0;
    }

    @Override
    protected int newRsv(WebSocketFrame msg) {
        return msg.rsv() ^ WebSocketExtension.RSV1;
    }

    @Override
    protected boolean appendFrameTail(WebSocketFrame msg) {
        return true;
    }

}
