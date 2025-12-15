/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Promise;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Decorator around another {@link Http2FrameWriter} instance.
 */
public class DecoratingHttp2FrameWriter implements Http2FrameWriter {
    private final Http2FrameWriter delegate;

    public DecoratingHttp2FrameWriter(Http2FrameWriter delegate) {
        this.delegate = checkNotNull(delegate, "delegate");
    }

    @Override
    public void writeData(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
                          boolean endStream, Promise<Void> promise) {
        delegate.writeData(ctx, streamId, data, padding, endStream, promise);
    }

    @Override
    public void writeHeaders(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
                             boolean endStream, Promise<Void> promise) {
        delegate.writeHeaders(ctx, streamId, headers, padding, endStream, promise);
    }

    @Override
    public void writeHeaders(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                             int streamDependency, short weight, boolean exclusive, int padding,
                             boolean endStream, Promise<Void> promise) {
        delegate.writeHeaders(ctx, streamId, headers, streamDependency, weight, exclusive, padding, endStream, promise);
    }

    @Override
    public void writePriority(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight,
                              boolean exclusive, Promise<Void> promise) {
        delegate.writePriority(ctx, streamId, streamDependency, weight, exclusive, promise);
    }

    @Override
    public void writeRstStream(ChannelHandlerContext ctx, int streamId, long errorCode,
                               Promise<Void> promise) {
        delegate.writeRstStream(ctx, streamId, errorCode, promise);
    }

    @Override
    public void writeSettings(ChannelHandlerContext ctx, Http2Settings settings, Promise<Void> promise) {
        delegate.writeSettings(ctx, settings, promise);
    }

    @Override
    public void writeSettingsAck(ChannelHandlerContext ctx, Promise<Void> promise) {
        delegate.writeSettingsAck(ctx, promise);
    }

    @Override
    public void writePing(ChannelHandlerContext ctx, boolean ack, long data, Promise<Void> promise) {
        delegate.writePing(ctx, ack, data, promise);
    }

    @Override
    public void writePushPromise(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
                                 Http2Headers headers, int padding, Promise<Void> promise) {
        delegate.writePushPromise(ctx, streamId, promisedStreamId, headers, padding, promise);
    }

    @Override
    public void writeGoAway(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData,
                            Promise<Void> promise) {
        delegate.writeGoAway(ctx, lastStreamId, errorCode, debugData, promise);
    }

    @Override
    public void writeWindowUpdate(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement,
                                  Promise<Void> promise) {
        delegate.writeWindowUpdate(ctx, streamId, windowSizeIncrement, promise);
    }

    @Override
    public void writeFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags,
                           ByteBuf payload, Promise<Void> promise) {
        delegate.writeFrame(ctx, frameType, streamId, flags, payload, promise);
    }

    @Override
    public Configuration configuration() {
        return delegate.configuration();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
