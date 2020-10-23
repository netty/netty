/*
 * Copyright 2017 The Netty Project
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
package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.util.internal.UnstableApi;

import java.util.List;

import static io.netty.buffer.Unpooled.unreleasableBuffer;
import static io.netty.handler.codec.http2.Http2CodecUtil.connectionPrefaceBuf;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Performing cleartext upgrade, by h2c HTTP upgrade or Prior Knowledge.
 * This handler config pipeline for h2c upgrade when handler added.
 * And will update pipeline once it detect the connection is starting HTTP/2 by
 * prior knowledge or not.
 */
@UnstableApi
public final class CleartextHttp2ServerUpgradeHandler extends ByteToMessageDecoder {
    private static final ByteBuf CONNECTION_PREFACE = unreleasableBuffer(connectionPrefaceBuf());

    private final HttpServerCodec httpServerCodec;
    private final HttpServerUpgradeHandler httpServerUpgradeHandler;
    private final ChannelHandler http2ServerHandler;

    /**
     * Creates the channel handler provide cleartext HTTP/2 upgrade from HTTP
     * upgrade or prior knowledge
     *
     * @param httpServerCodec the http server codec
     * @param httpServerUpgradeHandler the http server upgrade handler for HTTP/2
     * @param http2ServerHandler the http2 server handler, will be added into pipeline
     *                           when starting HTTP/2 by prior knowledge
     */
    public CleartextHttp2ServerUpgradeHandler(HttpServerCodec httpServerCodec,
                                              HttpServerUpgradeHandler httpServerUpgradeHandler,
                                              ChannelHandler http2ServerHandler) {
        this.httpServerCodec = checkNotNull(httpServerCodec, "httpServerCodec");
        this.httpServerUpgradeHandler = checkNotNull(httpServerUpgradeHandler, "httpServerUpgradeHandler");
        this.http2ServerHandler = checkNotNull(http2ServerHandler, "http2ServerHandler");
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        ctx.pipeline()
                .addAfter(ctx.name(), null, httpServerUpgradeHandler)
                .addAfter(ctx.name(), null, httpServerCodec);
    }

    /**
     * Peek inbound message to determine current connection wants to start HTTP/2
     * by HTTP upgrade or prior knowledge
     */
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        int prefaceLength = CONNECTION_PREFACE.readableBytes();
        int bytesRead = Math.min(in.readableBytes(), prefaceLength);

        if (!ByteBufUtil.equals(CONNECTION_PREFACE, CONNECTION_PREFACE.readerIndex(),
                in, in.readerIndex(), bytesRead)) {
            ctx.pipeline().remove(this);
        } else if (bytesRead == prefaceLength) {
            // Full h2 preface match, removed source codec, using http2 codec to handle
            // following network traffic
            ctx.pipeline()
                    .remove(httpServerCodec)
                    .remove(httpServerUpgradeHandler);

            ctx.pipeline().addAfter(ctx.name(), null, http2ServerHandler);
            ctx.pipeline().remove(this);

            ctx.fireUserEventTriggered(PriorKnowledgeUpgradeEvent.INSTANCE);
        }
    }

    /**
     * User event that is fired to notify about HTTP/2 protocol is started.
     */
    public static final class PriorKnowledgeUpgradeEvent {
        private static final PriorKnowledgeUpgradeEvent INSTANCE = new PriorKnowledgeUpgradeEvent();

        private PriorKnowledgeUpgradeEvent() {
        }
    }
}
