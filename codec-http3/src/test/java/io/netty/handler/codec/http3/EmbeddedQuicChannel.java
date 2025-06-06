/*
 * Copyright 2021 The Netty Project
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
package io.netty.handler.codec.http3;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPromise;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicChannelConfig;
import io.netty.handler.codec.quic.QuicConnectionAddress;
import io.netty.handler.codec.quic.QuicConnectionPathStats;
import io.netty.handler.codec.quic.QuicConnectionStats;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamType;
import io.netty.handler.codec.quic.QuicTransportParameters;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.SSLEngine;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static io.netty.util.AttributeKey.valueOf;
import static java.lang.System.arraycopy;
import static java.util.Collections.unmodifiableCollection;

final class EmbeddedQuicChannel extends EmbeddedChannel implements QuicChannel {
    private static final AttributeKey<AtomicLong> streamIdGeneratorKey =
            valueOf("embedded_channel_stream_id_generator");
    private final Map<QuicStreamType, Long> peerAllowedStreams = new EnumMap<>(QuicStreamType.class);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ConcurrentLinkedQueue<Integer> closeErrorCodes = new ConcurrentLinkedQueue<>();
    private QuicChannelConfig config;

    EmbeddedQuicChannel(boolean server) {
        this(server, new ChannelHandler[0]);
    }

    EmbeddedQuicChannel(boolean server, ChannelHandler... handlers) {
        super(prependChannelConsumer(channel -> channel.attr(streamIdGeneratorKey).set(new AtomicLong(server ? 1 : 0)),
                handlers));
    }

    static ChannelHandler[] prependChannelConsumer(Consumer<Channel> channelConsumer,
                                                   ChannelHandler... handlers) {
        ChannelHandler[] toReturn = new ChannelHandler[handlers.length + 1];
        toReturn[0] = new ChannelInboundHandlerAdapter() {
            @Override
            public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                channelConsumer.accept(ctx.channel());
                super.handlerAdded(ctx);
            }
        };
        arraycopy(handlers, 0, toReturn, 1, handlers.length);
        return toReturn;
    }

    @Override
    public QuicConnectionAddress localAddress() {
        return null;
    }

    @Override
    public QuicConnectionAddress remoteAddress() {
        return null;
    }

    @Override
    public SocketAddress localSocketAddress() {
        return null;
    }

    @Override
    public SocketAddress remoteSocketAddress() {
        return null;
    }

    @Override
    public boolean isTimedOut() {
        return false;
    }

    @Override
    @Nullable
    public SSLEngine sslEngine() {
        return null;
    }

    @Override
    public QuicChannelConfig config() {
        if (config == null) {
            config = new EmbeddedQuicChannelConfig(super.config());
        }
        return config;
    }

    @Override
    public QuicChannel flush() {
        super.flush();
        return this;
    }

    @Override
    public QuicChannel read() {
        super.read();
        return this;
    }

    @Override
    public long peerAllowedStreams(QuicStreamType type) {
        return peerAllowedStreams.getOrDefault(type, Long.MAX_VALUE);
    }

    public void peerAllowedStreams(QuicStreamType type, long peerAllowedStreams) {
        this.peerAllowedStreams.put(type, peerAllowedStreams);
    }

    @Override
    public Future<QuicStreamChannel> createStream(QuicStreamType type, ChannelHandler handler,
                                                  Promise<QuicStreamChannel> promise) {
        final AtomicLong streamIdGenerator = attr(streamIdGeneratorKey).get();
        return promise.setSuccess(new EmbeddedQuicStreamChannel(this, true, type,
                streamIdGenerator.getAndAdd(2), handler));
    }

    @Override
    public ChannelFuture close(boolean applicationClose, int error, ByteBuf reason, ChannelPromise promise) {
        closeErrorCodes.add(error);
        if (closed.compareAndSet(false, true)) {
            promise.addListener(__ -> reason.release());
        } else {
            reason.release();
        }
        return close(promise);
    }

    @Override
    public Future<QuicConnectionStats> collectStats(Promise<QuicConnectionStats> promise) {
        return promise.setFailure(
                new UnsupportedOperationException("Collect stats not supported for embedded channel."));
    }

    @Override
    public Future<QuicConnectionPathStats> collectPathStats(int i, Promise<QuicConnectionPathStats> promise) {
        return promise.setFailure(
                new UnsupportedOperationException("Collect path stats not supported for embedded channel."));
    }

    @Nullable
    public EmbeddedQuicStreamChannel localControlStream() {
        return (EmbeddedQuicStreamChannel) Http3.getLocalControlStream(this);
    }

    @Override
    @Nullable
    public QuicTransportParameters peerTransportParameters() {
        return null;
    }

    Collection<Integer> closeErrorCodes() {
        return unmodifiableCollection(closeErrorCodes);
    }

    private static final class EmbeddedQuicChannelConfig implements QuicChannelConfig {
        private final ChannelConfig delegate;

        EmbeddedQuicChannelConfig(ChannelConfig delegate) {
            this.delegate = delegate;
        }

        @Override
        public Map<ChannelOption<?>, Object> getOptions() {
            return delegate.getOptions();
        }

        @Override
        public boolean setOptions(Map<ChannelOption<?>, ?> map) {
            return delegate.setOptions(map);
        }

        @Override
        public <T> T getOption(ChannelOption<T> channelOption) {
            return delegate.getOption(channelOption);
        }

        @Override
        public <T> boolean setOption(ChannelOption<T> channelOption, T t) {
            return delegate.setOption(channelOption, t);
        }

        @Override
        public int getConnectTimeoutMillis() {
            return delegate.getConnectTimeoutMillis();
        }

        @Override
        public QuicChannelConfig setConnectTimeoutMillis(int i) {
            delegate.setConnectTimeoutMillis(i);
            return this;
        }

        @Override
        @Deprecated
        public int getMaxMessagesPerRead() {
            return delegate.getMaxMessagesPerRead();
        }

        @Override
        @Deprecated
        public QuicChannelConfig setMaxMessagesPerRead(int i) {
            delegate.setMaxMessagesPerRead(i);
            return this;
        }

        @Override
        public int getWriteSpinCount() {
            return delegate.getWriteSpinCount();
        }

        @Override
        public QuicChannelConfig setWriteSpinCount(int i) {
            delegate.setWriteSpinCount(i);
            return this;
        }

        @Override
        public ByteBufAllocator getAllocator() {
            return delegate.getAllocator();
        }

        @Override
        public QuicChannelConfig setAllocator(ByteBufAllocator byteBufAllocator) {
            delegate.setAllocator(byteBufAllocator);
            return this;
        }

        @Override
        public <T extends RecvByteBufAllocator> T getRecvByteBufAllocator() {
            return delegate.getRecvByteBufAllocator();
        }

        @Override
        public QuicChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator recvByteBufAllocator) {
            delegate.setRecvByteBufAllocator(recvByteBufAllocator);
            return this;
        }

        @Override
        public boolean isAutoRead() {
            return delegate.isAutoRead();
        }

        @Override
        public QuicChannelConfig setAutoRead(boolean b) {
            delegate.setAutoRead(b);
            return this;
        }

        @Override
        public boolean isAutoClose() {
            return delegate.isAutoClose();
        }

        @Override
        public QuicChannelConfig setAutoClose(boolean b) {
            delegate.setAutoClose(b);
            return this;
        }

        @Override
        public int getWriteBufferHighWaterMark() {
            return delegate.getWriteBufferHighWaterMark();
        }

        @Override
        public QuicChannelConfig setWriteBufferHighWaterMark(int i) {
            delegate.setWriteBufferHighWaterMark(i);
            return this;
        }

        @Override
        public int getWriteBufferLowWaterMark() {
            return delegate.getWriteBufferLowWaterMark();
        }

        @Override
        public QuicChannelConfig setWriteBufferLowWaterMark(int i) {
            delegate.setWriteBufferLowWaterMark(i);
            return this;
        }

        @Override
        public MessageSizeEstimator getMessageSizeEstimator() {
            return delegate.getMessageSizeEstimator();
        }

        @Override
        public QuicChannelConfig setMessageSizeEstimator(MessageSizeEstimator messageSizeEstimator) {
            delegate.setMessageSizeEstimator(messageSizeEstimator);
            return this;
        }

        @Override
        public WriteBufferWaterMark getWriteBufferWaterMark() {
            return delegate.getWriteBufferWaterMark();
        }

        @Override
        public QuicChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
            delegate.setWriteBufferWaterMark(writeBufferWaterMark);
            return this;
        }
    }
}
