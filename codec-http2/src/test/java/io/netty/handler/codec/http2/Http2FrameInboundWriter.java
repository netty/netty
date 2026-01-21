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

package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelShutdownType;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.CompletionHandler;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

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

    void writeInboundData(int streamId, ByteBuf data, int padding, boolean endStream) {
        Promise<Void> promise = ctx.newPromise();
        writer.writeData(ctx, streamId, data, padding, endStream, promise);
        promise.syncUninterruptibly();
    }

    void writeInboundHeaders(int streamId, Http2Headers headers,
                         int padding, boolean endStream) {
        Promise<Void> promise = ctx.newPromise();
        writer.writeHeaders(ctx, streamId, headers, padding, endStream, promise);
        promise.syncUninterruptibly();
    }

    void writeInboundHeaders(int streamId, Http2Headers headers,
                               int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) {
        Promise<Void> promise = ctx.newPromise();
        writer.writeHeaders(ctx, streamId, headers, streamDependency,
                weight, exclusive, padding, endStream, promise);
        promise.syncUninterruptibly();
    }

    void writeInboundPriority(int streamId, int streamDependency,
                                short weight, boolean exclusive) {
        Promise<Void> promise = ctx.newPromise();
        writer.writePriority(ctx, streamId, streamDependency, weight,
                exclusive, promise);
        promise.syncUninterruptibly();
    }

    void writeInboundRstStream(int streamId, long errorCode) {
        Promise<Void> promise = ctx.newPromise();
        writer.writeRstStream(ctx, streamId, errorCode, promise);
        promise.syncUninterruptibly();
    }

    void writeInboundSettings(Http2Settings settings) {
        Promise<Void> promise = ctx.newPromise();
        writer.writeSettings(ctx, settings, promise);
        promise.syncUninterruptibly();
    }

    void writeInboundSettingsAck() {
        Promise<Void> promise = ctx.newPromise();
        writer.writeSettingsAck(ctx, promise);
        promise.syncUninterruptibly();
    }

    void writeInboundPing(boolean ack, long data) {
        Promise<Void> promise = ctx.newPromise();
        writer.writePing(ctx, ack, data, promise);
        promise.syncUninterruptibly();
    }

    void writePushPromise(int streamId, int promisedStreamId,
                                   Http2Headers headers, int padding) {
        Promise<Void> promise = ctx.newPromise();
        writer.writePushPromise(ctx, streamId, promisedStreamId,
                   headers, padding, promise);
        promise.syncUninterruptibly();
    }

    void writeInboundGoAway(int lastStreamId, long errorCode, ByteBuf debugData) {
        Promise<Void> promise = ctx.newPromise();
        writer.writeGoAway(ctx, lastStreamId, errorCode, debugData, promise);
        promise.syncUninterruptibly();
    }

    void writeInboundWindowUpdate(int streamId, int windowSizeIncrement) {
        Promise<Void> promise = ctx.newPromise();
        writer.writeWindowUpdate(ctx, streamId, windowSizeIncrement, promise);
        promise.syncUninterruptibly();
    }

    void writeInboundFrame(byte frameType, int streamId,
                             Http2Flags flags, ByteBuf payload) {
        Promise<Void> promise = ctx.newPromise();
        writer.writeFrame(ctx, frameType, streamId, flags, payload, promise);
        promise.syncUninterruptibly();
    }

    private static final class WriteInboundChannelHandlerContext
            implements ChannelHandlerContext, ChannelOutboundHandler {
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
        public void fireChannelRegistered() {
            channel.pipeline().fireChannelRegistered();
        }

        @Override
        public void fireChannelUnregistered() {
            channel.pipeline().fireChannelUnregistered();
        }

        @Override
        public void fireChannelActive() {
            channel.pipeline().fireChannelActive();
        }

        @Override
        public void fireChannelInactive() {
            channel.pipeline().fireChannelInactive();
        }

        @Override
        public void fireExceptionCaught(Throwable cause) {
            channel.pipeline().fireExceptionCaught(cause);
        }

        @Override
        public void fireUserEventTriggered(Object evt) {
            channel.pipeline().fireUserEventTriggered(evt);
        }

        @Override
        public void fireChannelRead(Object msg) {
            channel.pipeline().fireChannelRead(msg);
        }

        @Override
        public void fireChannelReadComplete() {
            channel.pipeline().fireChannelReadComplete();
        }

        @Override
        public void fireChannelWritabilityChanged() {
            channel.pipeline().fireChannelWritabilityChanged();
        }

        @Override
        public void fireChannelShutdown(ChannelShutdownType type) {
            channel.pipeline().fireChannelShutdown(type);
        }

        @Override
        public void shutdown(ChannelShutdownType type, CompletionHandler<Void> handler) {
            channel.shutdown(type, handler);
        }

        @Override
        public void read() {
            channel.read();
        }

        @Override
        public void flush() {
            channel.pipeline().fireChannelReadComplete();
        }

        @Override
        public ChannelPipeline pipeline() {
            return channel.pipeline();
        }

        @Override
        public ByteBufAllocator alloc() {
            return channel.alloc();
        }

        @Override
        public void register(CompletionHandler<Void> handler) {
            channel.register(handler);
        }

        @Override
        public Future<Void> register() {
            return channel.register();
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
        public Future<Void> deregister() {
            return channel.deregister();
        }

        @Override
        public void bind(SocketAddress localAddress, CompletionHandler<Void> handler) {
            channel.bind(localAddress, handler);
        }

        @Override
        public void connect(SocketAddress remoteAddress, CompletionHandler<Void> handler) {
            channel.connect(remoteAddress, handler);
        }

        @Override
        public void connect(SocketAddress remoteAddress, SocketAddress localAddress, CompletionHandler<Void> handler) {
            channel.connect(remoteAddress, localAddress, handler);
        }

        @Override
        public void disconnect(CompletionHandler<Void> handler) {
            channel.disconnect(handler);
        }

        @Override
        public void close(CompletionHandler<Void> handler) {
            channel.close(handler);
        }

        @Override
        public void deregister(CompletionHandler<Void> promise) {
            channel.deregister(promise);
        }

        @Override
        public Future<Void> write(Object msg) {
            Promise<Void> promise = newPromise();
            write(msg, promise.toCompletionHandler());
            return promise;
        }

        @Override
        public void write(Object msg, CompletionHandler<Void> handler) {
            writeAndFlush(msg, handler);
        }

        @Override
        public void writeAndFlush(Object msg, CompletionHandler<Void> promise) {
            try {
                channel.writeInbound(msg);
                channel.runPendingTasks();
                promise.onSuccess(null);
            } catch (Throwable cause) {
                promise.onFailure(cause);
            }
        }

        @Override
        public Future<Void> writeAndFlush(Object msg) {
            Promise<Void> promise = newPromise();
            writeAndFlush(msg, promise.toCompletionHandler());
            return promise;
        }

        @Override
        public <T> Promise<T> newPromise() {
            return channel.newPromise();
        }

        @Override
        public <T> Future<T> newSucceededFuture(T result) {
            return channel.newSucceededFuture(result);
        }

        @Override
        public <T> Future<T> newFailedFuture(Throwable cause) {
            return channel.newFailedFuture(cause);
        }
    }
}
