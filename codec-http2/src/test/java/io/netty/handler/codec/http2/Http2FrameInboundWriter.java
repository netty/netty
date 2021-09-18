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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;

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
        writer.writeData(ctx, streamId, data, padding, endStream, ctx.newPromise()).syncUninterruptibly();
    }

    void writeInboundHeaders(int streamId, Http2Headers headers,
                         int padding, boolean endStream) {
        writer.writeHeaders(ctx, streamId, headers, padding, endStream, ctx.newPromise()).syncUninterruptibly();
    }

    void writeInboundHeaders(int streamId, Http2Headers headers,
                               int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) {
        writer.writeHeaders(ctx, streamId, headers, streamDependency,
                weight, exclusive, padding, endStream, ctx.newPromise()).syncUninterruptibly();
    }

    void writeInboundPriority(int streamId, int streamDependency,
                                short weight, boolean exclusive) {
        writer.writePriority(ctx, streamId, streamDependency, weight,
                exclusive, ctx.newPromise()).syncUninterruptibly();
    }

    void writeInboundRstStream(int streamId, long errorCode) {
        writer.writeRstStream(ctx, streamId, errorCode, ctx.newPromise()).syncUninterruptibly();
    }

    void writeInboundSettings(Http2Settings settings) {
        writer.writeSettings(ctx, settings, ctx.newPromise()).syncUninterruptibly();
    }

    void writeInboundSettingsAck() {
        writer.writeSettingsAck(ctx, ctx.newPromise()).syncUninterruptibly();
    }

    void writeInboundPing(boolean ack, long data) {
        writer.writePing(ctx, ack, data, ctx.newPromise()).syncUninterruptibly();
    }

    void writePushPromise(int streamId, int promisedStreamId,
                                   Http2Headers headers, int padding) {
           writer.writePushPromise(ctx, streamId, promisedStreamId,
                   headers, padding, ctx.newPromise()).syncUninterruptibly();
    }

    void writeInboundGoAway(int lastStreamId, long errorCode, ByteBuf debugData) {
        writer.writeGoAway(ctx, lastStreamId, errorCode, debugData, ctx.newPromise()).syncUninterruptibly();
    }

    void writeInboundWindowUpdate(int streamId, int windowSizeIncrement) {
        writer.writeWindowUpdate(ctx, streamId, windowSizeIncrement, ctx.newPromise()).syncUninterruptibly();
    }

    void writeInboundFrame(byte frameType, int streamId,
                             Http2Flags flags, ByteBuf payload) {
        writer.writeFrame(ctx, frameType, streamId, flags, payload, ctx.newPromise()).syncUninterruptibly();
    }

    private static final class WriteInboundChannelHandlerContext extends ChannelOutboundHandlerAdapter
            implements ChannelHandlerContext {
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
            return channel.eventLoop();
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
        public ChannelHandlerContext fireExceptionCaught(Throwable cause) {
            channel.pipeline().fireExceptionCaught(cause);
            return this;
        }

        @Override
        public ChannelHandlerContext fireUserEventTriggered(Object evt) {
            channel.pipeline().fireUserEventTriggered(evt);
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
        public ChannelPipeline pipeline() {
            return channel.pipeline();
        }

        @Override
        public ByteBufAllocator alloc() {
            return channel.alloc();
        }

        @Override
        public <T> Attribute<T> attr(AttributeKey<T> key) {
            return channel.attr(key);
        }

        @Override
        public <T> boolean hasAttr(AttributeKey<T> key) {
            return channel.hasAttr(key);
        }

        @Override
        public ChannelFuture bind(SocketAddress localAddress) {
            return channel.bind(localAddress);
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress) {
            return channel.connect(remoteAddress);
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
            return channel.connect(remoteAddress, localAddress);
        }

        @Override
        public ChannelFuture disconnect() {
            return channel.disconnect();
        }

        @Override
        public ChannelFuture close() {
            return channel.close();
        }

        @Override
        public ChannelFuture deregister() {
            return channel.deregister();
        }

        @Override
        public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
            return channel.bind(localAddress, promise);
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
            return channel.connect(remoteAddress, promise);
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            return channel.connect(remoteAddress, localAddress, promise);
        }

        @Override
        public ChannelFuture disconnect(ChannelPromise promise) {
            return channel.disconnect(promise);
        }

        @Override
        public ChannelFuture close(ChannelPromise promise) {
            return channel.close(promise);
        }

        @Override
        public ChannelFuture deregister(ChannelPromise promise) {
            return channel.deregister(promise);
        }

        @Override
        public ChannelFuture write(Object msg) {
            return write(msg, newPromise());
        }

        @Override
        public ChannelFuture write(Object msg, ChannelPromise promise) {
            return writeAndFlush(msg, promise);
        }

        @Override
        public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
            try {
                channel.writeInbound(msg);
                channel.runPendingTasks();
                promise.setSuccess();
            } catch (Throwable cause) {
                promise.setFailure(cause);
            }
            return promise;
        }

        @Override
        public ChannelFuture writeAndFlush(Object msg) {
            return writeAndFlush(msg, newPromise());
        }

        @Override
        public ChannelPromise newPromise() {
            return channel.newPromise();
        }

        @Override
        public ChannelProgressivePromise newProgressivePromise() {
            return channel.newProgressivePromise();
        }

        @Override
        public ChannelFuture newSucceededFuture() {
            return channel.newSucceededFuture();
        }

        @Override
        public ChannelFuture newFailedFuture(Throwable cause) {
            return channel.newFailedFuture(cause);
        }

        @Override
        public ChannelPromise voidPromise() {
            return channel.voidPromise();
        }
    }
}
