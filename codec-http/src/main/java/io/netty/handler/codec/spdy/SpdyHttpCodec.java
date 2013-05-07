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
package io.netty.handler.codec.spdy;

import io.netty.buffer.MessageBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandler;
import io.netty.channel.ChannelOutboundMessageHandler;
import io.netty.channel.CombinedChannelDuplexHandler;
import io.netty.handler.codec.http.HttpObject;


/**
 * A combination of {@link SpdyHttpDecoder} and {@link SpdyHttpEncoder}
 */
public final class SpdyHttpCodec
        extends CombinedChannelDuplexHandler
        implements ChannelInboundMessageHandler<SpdyDataOrControlFrame>, ChannelOutboundMessageHandler<HttpObject> {

    /**
     * Creates a new instance with the specified decoder options.
     */
    public SpdyHttpCodec(int version, int maxContentLength) {
        super(new SpdyHttpDecoder(version, maxContentLength), new SpdyHttpEncoder(version));
    }

    private SpdyHttpDecoder decoder() {
        return (SpdyHttpDecoder) stateHandler();
    }

    private SpdyHttpEncoder encoder() {
        return (SpdyHttpEncoder) operationHandler();
    }

    @Override
    public MessageBuf<SpdyDataOrControlFrame> newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
        return decoder().newInboundBuffer(ctx);
    }

    @Override
    public MessageBuf<HttpObject> newOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
        return encoder().newOutboundBuffer(ctx);
    }
}
