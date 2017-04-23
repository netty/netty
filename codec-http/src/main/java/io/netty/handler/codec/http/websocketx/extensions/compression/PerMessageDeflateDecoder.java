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

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtension;

import java.util.List;

/**
 * Per-message implementation of deflate decompressor.
 */
class PerMessageDeflateDecoder extends DeflateDecoder {

    private boolean compressing;

    /**
     * Constructor
     * @param noContext true to disable context takeover.
     */
    public PerMessageDeflateDecoder(boolean noContext) {
        super(noContext);
    }

    @Override
    public boolean acceptInboundMessage(Object msg) throws Exception {
        return ((msg instanceof TextWebSocketFrame ||
                 msg instanceof BinaryWebSocketFrame) &&
                    (((WebSocketFrame) msg).rsv() & WebSocketExtension.RSV1) > 0) ||
                (msg instanceof ContinuationWebSocketFrame && compressing);
    }

    @Override
    protected int newRsv(WebSocketFrame msg) {
        return (msg.rsv() & WebSocketExtension.RSV1) > 0 ?
                msg.rsv() ^ WebSocketExtension.RSV1 : msg.rsv();
    }

    @Override
    protected boolean appendFrameTail(WebSocketFrame msg) {
        return msg.isFinalFragment();
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, WebSocketFrame msg,
        List<Object> out) throws Exception {
        super.decode(ctx, msg, out);

        if (msg.isFinalFragment()) {
            compressing = false;
        } else if (msg instanceof TextWebSocketFrame || msg instanceof BinaryWebSocketFrame) {
            compressing = true;
        }
    }
}
