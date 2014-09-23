/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.example.http2.server;

import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.buffer.Unpooled.unreleasableBuffer;
import static io.netty.example.http2.Http2ExampleUtil.UPGRADE_RESPONSE_HEADER;
import static io.netty.util.internal.logging.InternalLogLevel.INFO;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.AsciiString;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DefaultHttp2FrameReader;
import io.netty.handler.codec.http2.DefaultHttp2FrameWriter;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2InboundFlowController;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2FrameAdapter;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2FrameWriter;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2InboundFrameLogger;
import io.netty.handler.codec.http2.Http2OutboundConnectionAdapter;
import io.netty.handler.codec.http2.Http2OutboundFrameLogger;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * A simple handler that responds with the message "Hello World!".
 */
public class HelloWorldHttp2Handler extends Http2ConnectionHandler {

    private static final Http2FrameLogger logger = new Http2FrameLogger(INFO,
            InternalLoggerFactory.getInstance(HelloWorldHttp2Handler.class));
    static final ByteBuf RESPONSE_BYTES = unreleasableBuffer(copiedBuffer("Hello World", CharsetUtil.UTF_8));

    public HelloWorldHttp2Handler() {
        this(new DefaultHttp2Connection(true), new Http2OutboundFrameLogger(new DefaultHttp2FrameWriter(), logger));
    }

    private HelloWorldHttp2Handler(Http2Connection connection, Http2FrameWriter frameWriter) {
        this(connection, frameWriter, new Http2OutboundConnectionAdapter(connection, frameWriter));
    }

    private HelloWorldHttp2Handler(Http2Connection connection, Http2FrameWriter frameWriter,
            Http2OutboundConnectionAdapter outbound) {
        super(connection, new SimpleHttp2FrameListener(outbound),
                new Http2InboundFrameLogger(new DefaultHttp2FrameReader(), logger),
                new DefaultHttp2InboundFlowController(connection, frameWriter), outbound);
    }

    /**
     * Handles the cleartext HTTP upgrade event. If an upgrade occurred, sends a simple response via HTTP/2
     * on stream 1 (the stream specifically reserved for cleartext HTTP upgrade).
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof HttpServerUpgradeHandler.UpgradeEvent) {
            // Write an HTTP/2 response to the upgrade request
            Http2Headers headers =
                    new DefaultHttp2Headers().status(new AsciiString("200"))
                    .set(new AsciiString(UPGRADE_RESPONSE_HEADER), new AsciiString("true"));
            writeHeaders(ctx, 1, headers, 0, true, ctx.newPromise());
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    private static class SimpleHttp2FrameListener extends Http2FrameAdapter {
        private Http2OutboundConnectionAdapter outbound;

        public SimpleHttp2FrameListener(Http2OutboundConnectionAdapter outbound) {
            this.outbound = outbound;
        }

        /**
         * If receive a frame with end-of-stream set, send a pre-canned response.
         */
        @Override
        public void onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
                boolean endOfStream) throws Http2Exception {
            if (endOfStream) {
                sendResponse(ctx, streamId, data.retain());
            }
        }

        /**
         * If receive a frame with end-of-stream set, send a pre-canned response.
         */
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId,
                Http2Headers headers, int streamDependency, short weight,
                boolean exclusive, int padding, boolean endStream) throws Http2Exception {
            if (endStream) {
                sendResponse(ctx, streamId, RESPONSE_BYTES.duplicate());
            }
        }

        /**
         * Sends a "Hello World" DATA frame to the client.
         */
        private void sendResponse(ChannelHandlerContext ctx, int streamId, ByteBuf payload) {
            // Send a frame for the response status
            Http2Headers headers = new DefaultHttp2Headers().status(new AsciiString("200"));
            outbound.writeHeaders(ctx, streamId, headers, 0, false, ctx.newPromise());
            outbound.writeData(ctx, streamId, payload, 0, true, ctx.newPromise());
        }
    };
}
