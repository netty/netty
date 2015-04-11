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

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DefaultHttp2FrameReader;
import io.netty.handler.codec.http2.DefaultHttp2FrameWriter;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2OrHttpChooser;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandler;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapter;

import javax.net.ssl.SSLEngine;

/**
 * Used during protocol negotiation, the main function of this handler is to
 * return the HTTP/1.1 or HTTP/2 handler once the protocol has been negotiated.
 */
public class Http2OrHttpHandler extends Http2OrHttpChooser {

    public Http2OrHttpHandler(int maxHttpContentLength) {
        super(maxHttpContentLength);
    }

    @Override
    protected SelectedProtocol getProtocol(SSLEngine engine) {
        String[] protocol = engine.getSession().getProtocol().split(":");
        if (protocol != null && protocol.length > 1) {
            SelectedProtocol selectedProtocol = SelectedProtocol.protocol(protocol[1]);
            System.err.println("Selected Protocol is " + selectedProtocol);
            return selectedProtocol;
        }
        return SelectedProtocol.UNKNOWN;
    }

    @Override
    protected void addHttp2Handlers(ChannelHandlerContext ctx) {
        DefaultHttp2Connection connection = new DefaultHttp2Connection(true);
        DefaultHttp2FrameWriter writer = new DefaultHttp2FrameWriter();
        DefaultHttp2FrameReader reader = new DefaultHttp2FrameReader();
        InboundHttp2ToHttpAdapter listener = new InboundHttp2ToHttpAdapter.Builder(connection).propagateSettings(true)
                .validateHttpHeaders(false).maxContentLength(1024 * 100).build();

        ctx.pipeline().addLast("httpToHttp2", new HttpToHttp2ConnectionHandler(connection,
                // Loggers can be activated for debugging purposes
                // new Http2InboundFrameLogger(reader, TilesHttp2ToHttpHandler.logger),
                // new Http2OutboundFrameLogger(writer, TilesHttp2ToHttpHandler.logger)
                reader, writer, listener));
        ctx.pipeline().addLast("fullHttpRequestHandler", new Http2RequestHandler());
    }

    @Override
    protected ChannelHandler createHttp1RequestHandler() {
        return new FallbackRequestHandler();
    }

    @Override
    protected Http2ConnectionHandler createHttp2RequestHandler() {
        return null; // NOOP
    }
}
