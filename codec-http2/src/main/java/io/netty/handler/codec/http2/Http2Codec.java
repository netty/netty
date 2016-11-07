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
package io.netty.handler.codec.http2;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.internal.UnstableApi;

import static io.netty.handler.logging.LogLevel.INFO;

/**
 * An HTTP/2 channel handler that adds a {@link Http2FrameCodec} and {@link Http2MultiplexCodec} to the pipeline before
 * removing itself.
 */
@UnstableApi
public final class Http2Codec extends ChannelDuplexHandler {
    private static final Http2FrameLogger HTTP2_FRAME_LOGGER = new Http2FrameLogger(INFO, Http2Codec.class);

    private final Http2FrameCodec frameCodec;
    private final Http2MultiplexCodec multiplexCodec;

    /**
     * Construct a new handler whose child channels run in the same event loop as this handler.
     *
     * @param server {@code true} this is a server
     * @param streamHandler the handler added to channels for remotely-created streams. It must be
     *     {@link ChannelHandler.Sharable}. {@code null} if the event loop from the parent channel should be used.
     */
    public Http2Codec(boolean server, ChannelHandler streamHandler) {
        this(server, new Http2StreamChannelBootstrap().handler(streamHandler), HTTP2_FRAME_LOGGER);
    }

    /**
     * Construct a new handler whose child channels run in the same event loop as this handler.
     *
     * @param server {@code true} this is a server
     * @param streamHandler the handler added to channels for remotely-created streams. It must be
     *     {@link ChannelHandler.Sharable}. {@code null} if the event loop from the parent channel should be used.
     * @param initialSettings non default initial settings to send to peer
     */
    public Http2Codec(boolean server, ChannelHandler streamHandler, Http2Settings initialSettings) {
        this(server, new Http2StreamChannelBootstrap().handler(streamHandler), HTTP2_FRAME_LOGGER,
            initialSettings);
    }

    /**
     * Construct a new handler whose child channels run in a different event loop.
     *
     * @param server {@code true} this is a server
     * @param bootstrap bootstrap used to instantiate child channels for remotely-created streams.
     */
    public Http2Codec(boolean server, Http2StreamChannelBootstrap bootstrap, Http2FrameLogger frameLogger) {
        this(server, bootstrap, new DefaultHttp2FrameWriter(), frameLogger, new Http2Settings());
    }

    /**
     * Construct a new handler whose child channels run in a different event loop.
     *
     * @param server {@code true} this is a server
     * @param bootstrap bootstrap used to instantiate child channels for remotely-created streams.
     * @param initialSettings non default initial settings to send to peer
     */
    public Http2Codec(boolean server, Http2StreamChannelBootstrap bootstrap, Http2FrameLogger frameLogger,
                      Http2Settings initialSettings) {
        this(server, bootstrap, new DefaultHttp2FrameWriter(), frameLogger, initialSettings);
    }

    // Visible for testing
    Http2Codec(boolean server, Http2StreamChannelBootstrap bootstrap, Http2FrameWriter frameWriter,
               Http2FrameLogger frameLogger, Http2Settings initialSettings) {
        frameCodec = new Http2FrameCodec(server, frameWriter, frameLogger, initialSettings);
        multiplexCodec = new Http2MultiplexCodec(server, bootstrap);
    }

    Http2FrameCodec frameCodec() {
        return frameCodec;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        ctx.pipeline().addBefore(ctx.executor(), ctx.name(), null, frameCodec);
        ctx.pipeline().addBefore(ctx.executor(), ctx.name(), null, multiplexCodec);

        ctx.pipeline().remove(this);
    }
}
