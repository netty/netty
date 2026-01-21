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
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicConnectionAddress;
import io.netty.handler.codec.quic.QuicConnectionPathStats;
import io.netty.handler.codec.quic.QuicConnectionStats;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamType;
import io.netty.handler.codec.quic.QuicTransportParameters;
import io.netty.util.AttributeKey;
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
    private ChannelConfig config;

    /**
     * TWO bits reserved for Variable-Length Integer Encoding
     * <a href="https://datatracker.ietf.org/doc/html/rfc9000?#name-variable-length-integer-enc">rfc9000</a>
     * TWO LSB used for distinguish Client/Server initiated stream & Bi/unidirection
     * these are not reserved but part of it
     * <a href="https://datatracker.ietf.org/doc/html/rfc9000?#name-stream-types-and-identifier">rfc9000</a>
     * so we can max stream per stream type (bi/uni) = (2^62-1)/2
     */
    private static final long MAX_PEER_STREAMS_PER_STREAM_TYPE = ((1L << 62) - 1) / 2;

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
        toReturn[0] = new ChannelInboundHandler() {
            @Override
            public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                channelConsumer.accept(ctx.channel());
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
    public ChannelConfig config() {
        if (config == null) {
            config = new EmbeddedQuicChannelConfig(super.config());
        }
        return config;
    }

    @Override
    public long peerAllowedStreams(QuicStreamType type) {
        return peerAllowedStreams.getOrDefault(type, MAX_PEER_STREAMS_PER_STREAM_TYPE);
    }

    public void peerAllowedStreams(QuicStreamType type, long peerAllowedStreams) {
        if (peerAllowedStreams > MAX_PEER_STREAMS_PER_STREAM_TYPE) {
            peerAllowedStreams = MAX_PEER_STREAMS_PER_STREAM_TYPE;
        }
        this.peerAllowedStreams.put(type, peerAllowedStreams);
    }

    @Override
    public void createStream(QuicStreamType type, ChannelHandler handler,
                             Promise<QuicStreamChannel> promise) {
        final AtomicLong streamIdGenerator = attr(streamIdGeneratorKey).get();
        promise.setSuccess(new EmbeddedQuicStreamChannel(this, true, type,
                streamIdGenerator.getAndAdd(2), handler));
    }

    @Override
    public void close(boolean applicationClose, int error, ByteBuf reason, Promise<Void> promise) {
        closeErrorCodes.add(error);
        if (closed.compareAndSet(false, true)) {
            promise.addListener(__ -> reason.release());
        } else {
            reason.release();
        }
        close(promise.toCompletionHandler());
    }

    @Override
    public void collectStats(Promise<QuicConnectionStats> promise) {
        promise.setFailure(
                new UnsupportedOperationException("Collect stats not supported for embedded channel."));
    }

    @Override
    public void collectPathStats(int i, Promise<QuicConnectionPathStats> promise) {
        promise.setFailure(
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

    private static final class EmbeddedQuicChannelConfig implements ChannelConfig {
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
        public EmbeddedQuicChannelConfig setConnectTimeoutMillis(int i) {
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
        public EmbeddedQuicChannelConfig setMaxMessagesPerRead(int i) {
            delegate.setMaxMessagesPerRead(i);
            return this;
        }

        @Override
        public int getWriteSpinCount() {
            return delegate.getWriteSpinCount();
        }

        @Override
        public EmbeddedQuicChannelConfig setWriteSpinCount(int i) {
            delegate.setWriteSpinCount(i);
            return this;
        }

        @Override
        public ByteBufAllocator getAllocator() {
            return delegate.getAllocator();
        }

        @Override
        public EmbeddedQuicChannelConfig setAllocator(ByteBufAllocator byteBufAllocator) {
            delegate.setAllocator(byteBufAllocator);
            return this;
        }

        @Override
        public <T extends RecvByteBufAllocator> T getRecvByteBufAllocator() {
            return delegate.getRecvByteBufAllocator();
        }

        @Override
        public EmbeddedQuicChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator recvByteBufAllocator) {
            delegate.setRecvByteBufAllocator(recvByteBufAllocator);
            return this;
        }

        @Override
        public boolean isAutoRead() {
            return delegate.isAutoRead();
        }

        @Override
        public EmbeddedQuicChannelConfig setAutoRead(boolean b) {
            delegate.setAutoRead(b);
            return this;
        }

        @Override
        public boolean isAutoClose() {
            return delegate.isAutoClose();
        }

        @Override
        public EmbeddedQuicChannelConfig setAutoClose(boolean b) {
            delegate.setAutoClose(b);
            return this;
        }

        @Override
        public MessageSizeEstimator getMessageSizeEstimator() {
            return delegate.getMessageSizeEstimator();
        }

        @Override
        public EmbeddedQuicChannelConfig setMessageSizeEstimator(MessageSizeEstimator messageSizeEstimator) {
            delegate.setMessageSizeEstimator(messageSizeEstimator);
            return this;
        }

        @Override
        public WriteBufferWaterMark getWriteBufferWaterMark() {
            return delegate.getWriteBufferWaterMark();
        }

        @Override
        public EmbeddedQuicChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
            delegate.setWriteBufferWaterMark(writeBufferWaterMark);
            return this;
        }
    }
}
