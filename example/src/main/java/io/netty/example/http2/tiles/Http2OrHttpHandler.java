/*
 * Copyright 2015 The Netty Project
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

package io.netty.example.http2.tiles;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DefaultHttp2FrameReader;
import io.netty.handler.codec.http2.DefaultHttp2FrameWriter;
import io.netty.handler.codec.http2.Http2OrHttpChooser;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandler;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapter;

/**
 * Used during protocol negotiation, the main function of this handler is to
 * return the HTTP/1.1 or HTTP/2 handler once the protocol has been negotiated.
 */
public class Http2OrHttpHandler extends Http2OrHttpChooser {

    private static final int MAX_CONTENT_LENGTH = 1024 * 100;

    @Override
    protected void configureHttp2(ChannelHandlerContext ctx) {
        DefaultHttp2Connection connection = new DefaultHttp2Connection(true);
        DefaultHttp2FrameWriter writer = new DefaultHttp2FrameWriter();
        DefaultHttp2FrameReader reader = new DefaultHttp2FrameReader();
        InboundHttp2ToHttpAdapter listener = new InboundHttp2ToHttpAdapter.Builder(connection).propagateSettings(true)
                .validateHttpHeaders(false).maxContentLength(MAX_CONTENT_LENGTH).build();

        ctx.pipeline().addLast("httpToHttp2", new HttpToHttp2ConnectionHandler(connection,
                // Loggers can be activated for debugging purposes
                // new Http2InboundFrameLogger(reader, TilesHttp2ToHttpHandler.logger),
                // new Http2OutboundFrameLogger(writer, TilesHttp2ToHttpHandler.logger)
                reader, writer, listener));
        ctx.pipeline().addLast("fullHttpRequestHandler", new Http2RequestHandler());
    }

    @Override
    protected void configureHttp1(ChannelHandlerContext ctx) throws Exception {
        ctx.pipeline().addLast(new HttpServerCodec(),
                               new HttpObjectAggregator(MAX_CONTENT_LENGTH),
                               new FallbackRequestHandler());
    }
}
