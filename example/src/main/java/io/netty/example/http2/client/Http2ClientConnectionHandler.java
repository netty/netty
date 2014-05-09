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
package io.netty.example.http2.client;

import static io.netty.util.internal.logging.InternalLogLevel.INFO;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.AbstractHttp2ConnectionHandler;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DefaultHttp2FrameReader;
import io.netty.handler.codec.http2.DefaultHttp2FrameWriter;
import io.netty.handler.codec.http2.DefaultHttp2InboundFlowController;
import io.netty.handler.codec.http2.DefaultHttp2OutboundFlowController;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2FrameReader;
import io.netty.handler.codec.http2.Http2FrameWriter;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2InboundFrameLogger;
import io.netty.handler.codec.http2.Http2OutboundFrameLogger;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A subclass of the connection handler that interprets response messages as text and prints it out
 * to the console.
 */
public class Http2ClientConnectionHandler extends AbstractHttp2ConnectionHandler {
    private static final Http2FrameLogger logger = new Http2FrameLogger(INFO,
            InternalLoggerFactory.getInstance(Http2ClientConnectionHandler.class));
    private final BlockingQueue<ChannelFuture> queue = new LinkedBlockingQueue<ChannelFuture>();

    private ByteBuf collectedData;
    private ChannelPromise initialized;

    public Http2ClientConnectionHandler() {
        super(new DefaultHttp2Connection(false, false), frameReader(), frameWriter(),
                new DefaultHttp2InboundFlowController(), new DefaultHttp2OutboundFlowController());
    }

    public void awaitInitialization() throws InterruptedException {
        initialized.await(5, TimeUnit.SECONDS);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        initialized = ctx.newPromise();
    }

    public ChannelFuture writeData(ChannelPromise promise, int streamId, ByteBuf data, int padding,
            boolean endStream, boolean endSegment, boolean compressed) throws Http2Exception {
        return super.writeData(ctx(), promise, streamId, data, padding, endStream, endSegment,
                compressed);
    }

    public ChannelFuture writeHeaders(ChannelPromise promise, int streamId, Http2Headers headers,
            int padding, boolean endStream, boolean endSegment) throws Http2Exception {
        return super
                .writeHeaders(ctx(), promise, streamId, headers, padding, endStream, endSegment);
    }

    @Override
    public void onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
            boolean endOfStream, boolean endOfSegment, boolean compressed) throws Http2Exception {

        // Copy the data into the buffer.
        int available = data.readableBytes();
        if (collectedData == null) {
            collectedData = ctx().alloc().buffer(available);
            collectedData.writeBytes(data);
        } else {
            // Expand the buffer
            ByteBuf newBuffer = ctx().alloc().buffer(data.readableBytes() + available);
            newBuffer.writeBytes(data);
            newBuffer.writeBytes(data);
            collectedData.release();
            collectedData = newBuffer;
        }

        // If it's the last frame, print the complete message.
        if (endOfStream) {
            byte[] bytes = new byte[data.readableBytes()];
            data.readBytes(bytes);
            System.out.println("Received message: " + new String(bytes, CharsetUtil.UTF_8));

            // Free the data buffer.
            collectedData.release();
            collectedData = null;

            queue.add(ctx().channel().newSucceededFuture());
        }
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
            int padding, boolean endStream, boolean endSegment) throws Http2Exception {
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
            int streamDependency, short weight, boolean exclusive, int padding, boolean endStream,
            boolean endSegment) throws Http2Exception {
    }

    @Override
    public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency,
            short weight, boolean exclusive) throws Http2Exception {
    }

    @Override
    public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode)
            throws Http2Exception {
    }

    @Override
    public void onSettingsAckRead(ChannelHandlerContext ctx) throws Http2Exception {
    }

    @Override
    public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings)
            throws Http2Exception {
        if (!initialized.isDone()) {
            initialized.setSuccess();
        }
    }

    @Override
    public void onPingRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
    }

    @Override
    public void onPingAckRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
    }

    @Override
    public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
            Http2Headers headers, int padding) throws Http2Exception {
    }

    @Override
    public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode,
            ByteBuf debugData) throws Http2Exception {
    }

    @Override
    public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement)
            throws Http2Exception {
    }

    @Override
    public void onAltSvcRead(ChannelHandlerContext ctx, int streamId, long maxAge, int port,
            ByteBuf protocolId, String host, String origin) throws Http2Exception {
    }

    @Override
    public void onBlockedRead(ChannelHandlerContext ctx, int streamId) throws Http2Exception {
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        queue.add(ctx.channel().newFailedFuture(cause));
        cause.printStackTrace();

        super.exceptionCaught(ctx, cause);
        if (!initialized.isDone()) {
            initialized.setFailure(cause);
        }
    }

    public BlockingQueue<ChannelFuture> queue() {
        return queue;
    }

    private static Http2FrameReader frameReader() {
        return new Http2InboundFrameLogger(new DefaultHttp2FrameReader(), logger);
    }

    private static Http2FrameWriter frameWriter() {
        return new Http2OutboundFrameLogger(new DefaultHttp2FrameWriter(), logger);
    }
}
