/*
 * Copyright 2018 The Netty Project
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
package io.netty5.handler.codec.http2;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.ChannelShutdownDirection;
import io.netty5.channel.ReadBufferAllocator;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.util.concurrent.EventExecutor;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;

import java.net.SocketAddress;

/**
 * Utility class which allows easy writing of HTTP2 frames via {@link EmbeddedChannel#writeInbound(Object...)}.
 */
final class Http2FrameInboundWriter {

    private final ChannelHandlerContext ctx;
    private final Http2FrameWriter writer;

    Http2FrameInboundWriter(EmbeddedChannel channel) {
        this(channel, new DefaultHttp2FrameWriter());
    }

    Http2FrameInboundWriter(EmbeddedChannel channel, Http2FrameWriter writer) {
        ctx = new WriteInboundChannelHandlerContext(channel);
        this.writer = writer;
    }

    void writeInboundData(int streamId, Buffer data, int padding, boolean endStream) throws Exception {
        writer.writeData(ctx, streamId, data, padding, endStream).asStage().sync();
    }

    void writeInboundHeaders(int streamId, Http2Headers headers,
                         int padding, boolean endStream) throws Exception {
        writer.writeHeaders(ctx, streamId, headers, padding, endStream).asStage().sync();
    }

    void writeInboundHeaders(
            int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive,
            int padding, boolean endStream) throws Exception {
        writer.writeHeaders(ctx, streamId, headers, streamDependency,
                weight, exclusive, padding, endStream).asStage().sync();
    }

    void writeInboundPriority(int streamId, int streamDependency,
                                short weight, boolean exclusive) throws Exception {
        writer.writePriority(ctx, streamId, streamDependency, weight, exclusive).asStage().sync();
    }

    void writeInboundRstStream(int streamId, long errorCode) throws Exception {
        writer.writeRstStream(ctx, streamId, errorCode).asStage().sync();
    }

    void writeInboundSettings(Http2Settings settings) throws Exception {
        writer.writeSettings(ctx, settings).asStage().sync();
    }

    void writeInboundSettingsAck() throws Exception {
        writer.writeSettingsAck(ctx).asStage().sync();
    }

    void writeInboundPing(boolean ack, long data) throws Exception {
        writer.writePing(ctx, ack, data).asStage().sync();
    }

    void writePushPromise(int streamId, int promisedStreamId,
                                   Http2Headers headers, int padding) throws Exception {
        writer.writePushPromise(ctx, streamId, promisedStreamId,
                   headers, padding).asStage().sync();
    }

    void writeInboundGoAway(int lastStreamId, long errorCode, Buffer debugData) throws Exception {
        writer.writeGoAway(ctx, lastStreamId, errorCode, debugData).asStage().sync();
    }

    void writeInboundWindowUpdate(int streamId, int windowSizeIncrement) throws Exception {
        writer.writeWindowUpdate(ctx, streamId, windowSizeIncrement).asStage().sync();
    }

    void writeInboundFrame(
            byte frameType, int streamId, Http2Flags flags, Buffer payload) throws Exception {
        writer.writeFrame(ctx, frameType, streamId, flags, payload).asStage().sync();
    }

    private static final class WriteInboundChannelHandlerContext
            implements ChannelHandlerContext, ChannelHandler {
        private final EmbeddedChannel channel;

        WriteInboundChannelHandlerContext(EmbeddedChannel channel) {
            this.channel = channel;
        }

        @Override
        public Channel channel() {
            return channel;
        }

        @Override
        public EventExecutor executor() {
            return channel.executor();
        }

        @Override
        public String name() {
            return "WriteInbound";
        }

        @Override
        public ChannelHandler handler() {
            return this;
        }

        @Override
        public boolean isRemoved() {
            return false;
        }

        @Override
        public ChannelHandlerContext fireChannelRegistered() {
            channel.pipeline().fireChannelRegistered();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelUnregistered() {
            channel.pipeline().fireChannelUnregistered();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelActive() {
            channel.pipeline().fireChannelActive();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelInactive() {
            channel.pipeline().fireChannelInactive();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelShutdown(ChannelShutdownDirection direction) {
            channel.pipeline().fireChannelShutdown(direction);
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelExceptionCaught(Throwable cause) {
            channel.pipeline().fireChannelExceptionCaught(cause);
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelInboundEvent(Object evt) {
            channel.pipeline().fireChannelInboundEvent(evt);
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelRead(Object msg) {
            channel.pipeline().fireChannelRead(msg);
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelReadComplete() {
            channel.pipeline().fireChannelReadComplete();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelWritabilityChanged() {
            channel.pipeline().fireChannelWritabilityChanged();
            return this;
        }

        @Override
        public ChannelHandlerContext read(ReadBufferAllocator readBufferAllocator) {
            channel.read(readBufferAllocator);
            return this;
        }

        @Override
        public ChannelHandlerContext read() {
            channel.read();
            return this;
        }

        @Override
        public ChannelHandlerContext flush() {
            channel.pipeline().fireChannelReadComplete();
            return this;
        }

        @Override
        public Future<Void> sendOutboundEvent(Object event) {
            return channel.pipeline().sendOutboundEvent(event);
        }

        @Override
        public ChannelPipeline pipeline() {
            return channel.pipeline();
        }

        @Override
        public BufferAllocator bufferAllocator() {
            return channel.bufferAllocator();
        }

        @Override
        public Future<Void> bind(SocketAddress localAddress) {
            return channel.bind(localAddress);
        }

        @Override
        public Future<Void> connect(SocketAddress remoteAddress) {
            return channel.connect(remoteAddress);
        }

        @Override
        public Future<Void> connect(SocketAddress remoteAddress, SocketAddress localAddress) {
            return channel.connect(remoteAddress, localAddress);
        }

        @Override
        public Future<Void> disconnect() {
            return channel.disconnect();
        }

        @Override
        public Future<Void> close() {
            return channel.close();
        }

        @Override
        public Future<Void> shutdown(ChannelShutdownDirection direction) {
            return channel.shutdown(direction);
        }

        @Override
        public Future<Void> register() {
            return channel.register();
        }

        @Override
        public Future<Void> deregister() {
            return channel.deregister();
        }

        @Override
        public Future<Void> write(Object msg) {
            return writeAndFlush(msg);
        }

        @Override
        public Future<Void> writeAndFlush(Object msg) {
            try {
                channel.writeInbound(msg);
                channel.runPendingTasks();
            } catch (Throwable cause) {
                return newFailedFuture(cause);
            }
            return newSucceededFuture();
        }

        @Override
        public Promise<Void> newPromise() {
            return channel.newPromise();
        }

        @Override
        public Future<Void> newSucceededFuture() {
            return channel.newSucceededFuture();
        }

        @Override
        public Future<Void> newFailedFuture(Throwable cause) {
            return channel.newFailedFuture(cause);
        }
    }
}
