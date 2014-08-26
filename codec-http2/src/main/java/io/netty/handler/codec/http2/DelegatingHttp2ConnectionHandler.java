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

package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

/**
 * Helper class that facilitates use of {@link AbstractHttp2ConnectionHandler} in compositional
 * models, rather than having to subclass it directly.
 * <p>
 * Exposes all {@code writeXXX} methods as public and delegates all frame read events to a provided
 * {@link Http2FrameListener}.
 * <p>
 * The {@link #channelActive} and {@link #handlerAdded} should called when appropriate to ensure
 * that the initial SETTINGS frame is sent to the remote endpoint.
 */
public class DelegatingHttp2ConnectionHandler extends AbstractHttp2ConnectionHandler {
    private final Http2FrameListener listener;

    public DelegatingHttp2ConnectionHandler(boolean server, Http2FrameListener listener) {
        super(server);
        this.listener = listener;
    }

    public DelegatingHttp2ConnectionHandler(Http2Connection connection,
            Http2FrameReader frameReader, Http2FrameWriter frameWriter,
            Http2InboundFlowController inboundFlow, Http2OutboundFlowController outboundFlow,
            Http2FrameListener listener) {
        super(connection, frameReader, frameWriter, inboundFlow, outboundFlow);
        this.listener = listener;
    }

    public DelegatingHttp2ConnectionHandler(Http2Connection connection, Http2FrameListener listener) {
        super(connection);
        this.listener = listener;
    }

    @Override
    public ChannelFuture writeData(ChannelHandlerContext ctx, int streamId, ByteBuf data,
            int padding, boolean endStream, ChannelPromise promise) {
        return super.writeData(ctx, streamId, data, padding, endStream, promise);
    }

    @Override
    public ChannelFuture writeHeaders(ChannelHandlerContext ctx, int streamId,
            Http2Headers headers, int padding, boolean endStream, ChannelPromise promise) {
        return super.writeHeaders(ctx, streamId, headers, padding, endStream, promise);
    }

    @Override
    public ChannelFuture writeHeaders(ChannelHandlerContext ctx, int streamId,
            Http2Headers headers, int streamDependency, short weight, boolean exclusive,
            int padding, boolean endStream, ChannelPromise promise) {
        return super.writeHeaders(ctx, streamId, headers, streamDependency, weight,
                exclusive, padding, endStream, promise);
    }

    @Override
    public ChannelFuture writePriority(ChannelHandlerContext ctx, int streamId,
            int streamDependency, short weight, boolean exclusive, ChannelPromise promise) {
        return super.writePriority(ctx, streamId, streamDependency, weight, exclusive, promise);
    }

    @Override
    public ChannelFuture writeRstStream(ChannelHandlerContext ctx, int streamId, long errorCode,
            ChannelPromise promise) {
        return super.writeRstStream(ctx, streamId, errorCode, promise);
    }

    @Override
    public ChannelFuture writeSettings(ChannelHandlerContext ctx,
            Http2Settings settings, ChannelPromise promise) {
        return super.writeSettings(ctx, settings, promise);
    }

    @Override
    public ChannelFuture writePing(ChannelHandlerContext ctx, ByteBuf data, ChannelPromise promise) {
        return super.writePing(ctx, data, promise);
    }

    @Override
    public ChannelFuture writePushPromise(ChannelHandlerContext ctx, int streamId,
            int promisedStreamId, Http2Headers headers, int padding, ChannelPromise promise) {
        return super.writePushPromise(ctx, streamId, promisedStreamId, headers, padding, promise);
    }

    @Override
    public void onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
            boolean endOfStream) throws Http2Exception {
        listener.onDataRead(ctx, streamId, data, padding, endOfStream);
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
            int streamDependency, short weight, boolean exclusive, int padding, boolean endStream)
            throws Http2Exception {
        listener.onHeadersRead(ctx, streamId, headers, streamDependency, weight, exclusive,
                padding, endStream);
    }

    @Override
    public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency,
            short weight, boolean exclusive) throws Http2Exception {
        listener.onPriorityRead(ctx, streamId, streamDependency, weight, exclusive);
    }

    @Override
    public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode)
            throws Http2Exception {
        listener.onRstStreamRead(ctx, streamId, errorCode);
    }

    @Override
    public void onSettingsAckRead(ChannelHandlerContext ctx) throws Http2Exception {
        listener.onSettingsAckRead(ctx);
    }

    @Override
    public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) throws Http2Exception {
        listener.onSettingsRead(ctx, settings);
    }

    @Override
    public void onPingRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
        listener.onPingRead(ctx, data);
    }

    @Override
    public void onPingAckRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
        listener.onPingAckRead(ctx, data);
    }

    @Override
    public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
            Http2Headers headers, int padding) throws Http2Exception {
        listener.onPushPromiseRead(ctx, streamId, promisedStreamId, headers, padding);
    }

    @Override
    public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData)
            throws Http2Exception {
        listener.onGoAwayRead(ctx, lastStreamId, errorCode, debugData);
    }

    @Override
    public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement)
            throws Http2Exception {
        listener.onWindowUpdateRead(ctx, streamId, windowSizeIncrement);
    }

    @Override
    public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags,
            ByteBuf payload) {
        listener.onUnknownFrame(ctx, frameType, streamId, flags, payload);
    }
}
