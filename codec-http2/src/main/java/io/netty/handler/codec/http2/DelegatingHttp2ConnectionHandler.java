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
 * {@link Http2FrameObserver}.
 * <p>
 * The {@link #channelActive} and {@link #handlerAdded} should called when appropriate to ensure
 * that the initial SETTINGS frame is sent to the remote endpoint.
 */
public class DelegatingHttp2ConnectionHandler extends AbstractHttp2ConnectionHandler {
    private final Http2FrameObserver observer;

    public DelegatingHttp2ConnectionHandler(boolean server, Http2FrameObserver observer) {
        super(server);
        this.observer = observer;
    }

    public DelegatingHttp2ConnectionHandler(boolean server, boolean allowCompression,
            Http2FrameObserver observer) {
        super(server, allowCompression);
        this.observer = observer;
    }

    public DelegatingHttp2ConnectionHandler(Http2Connection connection,
            Http2FrameReader frameReader, Http2FrameWriter frameWriter,
            Http2InboundFlowController inboundFlow, Http2OutboundFlowController outboundFlow,
            Http2FrameObserver observer) {
        super(connection, frameReader, frameWriter, inboundFlow, outboundFlow);
        this.observer = observer;
    }

    public DelegatingHttp2ConnectionHandler(Http2Connection connection, Http2FrameObserver observer) {
        super(connection);
        this.observer = observer;
    }

    @Override
    public ChannelFuture writeData(ChannelHandlerContext ctx, ChannelPromise promise, int streamId,
            ByteBuf data, int padding, boolean endStream, boolean endSegment, boolean compressed)
            throws Http2Exception {
        return super.writeData(ctx, promise, streamId, data, padding, endStream, endSegment,
                compressed);
    }

    @Override
    public ChannelFuture writeHeaders(ChannelHandlerContext ctx, ChannelPromise promise,
            int streamId, Http2Headers headers, int padding, boolean endStream, boolean endSegment)
            throws Http2Exception {
        return super.writeHeaders(ctx, promise, streamId, headers, padding, endStream, endSegment);
    }

    @Override
    public ChannelFuture writeHeaders(ChannelHandlerContext ctx, ChannelPromise promise,
            int streamId, Http2Headers headers, int streamDependency, short weight,
            boolean exclusive, int padding, boolean endStream, boolean endSegment)
            throws Http2Exception {
        return super.writeHeaders(ctx, promise, streamId, headers, streamDependency, weight,
                exclusive, padding, endStream, endSegment);
    }

    @Override
    public ChannelFuture writePriority(ChannelHandlerContext ctx, ChannelPromise promise,
            int streamId, int streamDependency, short weight, boolean exclusive)
            throws Http2Exception {
        return super.writePriority(ctx, promise, streamId, streamDependency, weight, exclusive);
    }

    @Override
    public ChannelFuture writeRstStream(ChannelHandlerContext ctx, ChannelPromise promise,
            int streamId, long errorCode) {
        return super.writeRstStream(ctx, promise, streamId, errorCode);
    }

    @Override
    public ChannelFuture writeSettings(ChannelHandlerContext ctx, ChannelPromise promise,
            Http2Settings settings) throws Http2Exception {
        return super.writeSettings(ctx, promise, settings);
    }

    @Override
    public ChannelFuture writePing(ChannelHandlerContext ctx, ChannelPromise promise, ByteBuf data)
            throws Http2Exception {
        return super.writePing(ctx, promise, data);
    }

    @Override
    public ChannelFuture writePushPromise(ChannelHandlerContext ctx, ChannelPromise promise,
            int streamId, int promisedStreamId, Http2Headers headers, int padding)
            throws Http2Exception {
        return super.writePushPromise(ctx, promise, streamId, promisedStreamId, headers, padding);
    }

    @Override
    public ChannelFuture writeAltSvc(ChannelHandlerContext ctx, ChannelPromise promise,
            int streamId, long maxAge, int port, ByteBuf protocolId, String host, String origin)
            throws Http2Exception {
        return super.writeAltSvc(ctx, promise, streamId, maxAge, port, protocolId, host, origin);
    }

    @Override
    public void onDataRead(int streamId, ByteBuf data, int padding, boolean endOfStream,
            boolean endOfSegment, boolean compressed) throws Http2Exception {
        observer.onDataRead(streamId, data, padding, endOfStream, endOfSegment, compressed);
    }

    @Override
    public void onHeadersRead(int streamId, Http2Headers headers, int padding, boolean endStream,
            boolean endSegment) throws Http2Exception {
        observer.onHeadersRead(streamId, headers, padding, endStream, endSegment);
    }

    @Override
    public void onHeadersRead(int streamId, Http2Headers headers, int streamDependency,
            short weight, boolean exclusive, int padding, boolean endStream, boolean endSegment)
            throws Http2Exception {
        observer.onHeadersRead(streamId, headers, streamDependency, weight, exclusive, padding,
                endStream, endSegment);
    }

    @Override
    public void onPriorityRead(int streamId, int streamDependency, short weight, boolean exclusive)
            throws Http2Exception {
        observer.onPriorityRead(streamId, streamDependency, weight, exclusive);
    }

    @Override
    public void onRstStreamRead(int streamId, long errorCode) throws Http2Exception {
        observer.onRstStreamRead(streamId, errorCode);
    }

    @Override
    public void onSettingsAckRead() throws Http2Exception {
        observer.onSettingsAckRead();
    }

    @Override
    public void onSettingsRead(Http2Settings settings) throws Http2Exception {
        observer.onSettingsRead(settings);
    }

    @Override
    public void onPingRead(ByteBuf data) throws Http2Exception {
        observer.onPingRead(data);
    }

    @Override
    public void onPingAckRead(ByteBuf data) throws Http2Exception {
        observer.onPingAckRead(data);
    }

    @Override
    public void onPushPromiseRead(int streamId, int promisedStreamId, Http2Headers headers,
            int padding) throws Http2Exception {
        observer.onPushPromiseRead(streamId, promisedStreamId, headers, padding);
    }

    @Override
    public void onGoAwayRead(int lastStreamId, long errorCode, ByteBuf debugData)
            throws Http2Exception {
        observer.onGoAwayRead(lastStreamId, errorCode, debugData);
    }

    @Override
    public void onWindowUpdateRead(int streamId, int windowSizeIncrement) throws Http2Exception {
        observer.onWindowUpdateRead(streamId, windowSizeIncrement);
    }

    @Override
    public void onAltSvcRead(int streamId, long maxAge, int port, ByteBuf protocolId, String host,
            String origin) throws Http2Exception {
        observer.onAltSvcRead(streamId, maxAge, port, protocolId, host, origin);
    }

    @Override
    public void onBlockedRead(int streamId) throws Http2Exception {
        observer.onBlockedRead(streamId);
    }
}
