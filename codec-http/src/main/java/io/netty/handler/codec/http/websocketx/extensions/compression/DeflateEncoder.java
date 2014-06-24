/*
 * Copyright 2012 The Netty Project
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
// (BSD License: http://www.opensource.org/licenses/bsd-license)
//
// Copyright (c) 2011, Joe Walnes and contributors
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or
// without modification, are permitted provided that the
// following conditions are met:
//
// * Redistributions of source code must retain the above
// copyright notice, this list of conditions and the
// following disclaimer.
//
// * Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the
// following disclaimer in the documentation and/or other
// materials provided with the distribution.
//
// * Neither the name of the Webbit nor the names of
// its contributors may be used to endorse or promote products
// derived from this software without specific prior written
// permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
// CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
// INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
// MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
// CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
// INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
// GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
// BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
// LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
// OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.

package io.netty.handler.codec.http.websocketx.extensions.compression;

import static io.netty.handler.codec.http.websocketx.extensions.compression.PermessageDeflateDecoder.*;
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
