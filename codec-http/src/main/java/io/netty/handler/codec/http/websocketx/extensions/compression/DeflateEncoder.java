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

import static io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateDecoder.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.CodecException;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtension;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionEncoder;

import java.util.List;

abstract class DeflateEncoder extends WebSocketExtensionEncoder {

    private final int compressionLevel;
    private final int windowSize;
    private final boolean noContext;

    private EmbeddedChannel encoder;

    public DeflateEncoder(int compressionLevel, int windowSize, boolean noContext) {
        this.compressionLevel = compressionLevel;
        this.windowSize = windowSize;
        this.noContext = noContext;
    }

    protected abstract int rsv(WebSocketFrame msg);

    protected abstract boolean removeFrameTail(WebSocketFrame msg);

    @Override
    protected void encode(ChannelHandlerContext ctx, WebSocketFrame msg,
            List<Object> out) throws Exception {

        if (encoder == null) {
            encoder = new EmbeddedChannel(ZlibCodecFactory.newZlibEncoder(
                    ZlibWrapper.NONE, compressionLevel, windowSize, 8));
        }

        encoder.writeOutbound(msg.content().retain());

        ByteBuf encodedContent = (ByteBuf) encoder.readOutbound();
        if (encodedContent == null) {
            throw new CodecException();
        }

        if (removeFrameTail(msg)) {
            int realLength = encodedContent.readableBytes() - FRAME_TAIL.length;
            encodedContent = encodedContent.slice(0, realLength);
        }

        WebSocketFrame outMsg;
        if (msg instanceof TextWebSocketFrame) {
            outMsg = new TextWebSocketFrame(msg.isFinalFragment(), rsv(msg), encodedContent);
        } else if (msg instanceof BinaryWebSocketFrame) {
            outMsg = new BinaryWebSocketFrame(msg.isFinalFragment(), rsv(msg), encodedContent);
        } else if (msg instanceof ContinuationWebSocketFrame) {
            outMsg = new ContinuationWebSocketFrame(msg.isFinalFragment(), rsv(msg), encodedContent);
        } else {
            throw new CodecException("unexpected frame type: " + msg.getClass().getName());
        }
        out.add(outMsg);

        if (msg.isFinalFragment() && noContext) {
            encoder = null;
        }
    }

}
