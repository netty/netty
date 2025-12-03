/*
 * Copyright 2020 The Netty Project
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

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicStreamAddress;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamFrame;
import io.netty.handler.codec.quic.QuicStreamPriority;
import io.netty.handler.codec.quic.QuicStreamType;
import io.netty.util.AttributeKey;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static io.netty.handler.codec.http3.EmbeddedQuicChannel.prependChannelConsumer;
import static io.netty.util.AttributeKey.valueOf;

final class EmbeddedQuicStreamChannel extends EmbeddedChannel implements QuicStreamChannel {
    private static final AttributeKey<Long> streamIdKey = valueOf("embedded_channel_stream_id");
    private static final AttributeKey<QuicStreamType> streamTypeKey = valueOf("embedded_channel_stream_type");
    private static final AttributeKey<Boolean> localCreatedKey = valueOf("embedded_channel_stream_local_created");
    private ChannelConfig config;
    private Integer inputShutdown;
    private Integer outputShutdown;

    EmbeddedQuicStreamChannel(ChannelHandler... handlers) {
        this(null, false, QuicStreamType.BIDIRECTIONAL, 0, handlers);
    }

    EmbeddedQuicStreamChannel(@Nullable QuicChannel parent, boolean localCreated, QuicStreamType type,
                              long id, ChannelHandler... handlers) {
        super(parent, DefaultChannelId.newInstance(), true, false,
                prependChannelConsumer(channel -> {
                    channel.attr(streamIdKey).set(id);
                    channel.attr(streamTypeKey).set(type);
                    channel.attr(localCreatedKey).set(localCreated);
                }, handlers));
        pipeline().addFirst(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                if (msg instanceof QuicStreamFrame && ((QuicStreamFrame) msg).hasFin()) {
                    // Mimic the API.
                    promise.addListener(f -> outputShutdown = 0);
                }
                super.write(ctx, msg, promise);
            }
        });
    }

    boolean writeInboundWithFin(Object... msgs) {
        shutdownInput();
        boolean written = writeInbound(msgs);
        fireInputShutdownEvents();
        return written;
    }

    void writeInboundFin() {
        shutdownInput();
        fireInputShutdownEvents();
    }

    private void fireInputShutdownEvents() {
        pipeline().fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE);
        pipeline().fireUserEventTriggered(ChannelInputShutdownReadComplete.INSTANCE);
    }

    @Override
    public QuicStreamChannel flush() {
        super.flush();
        return this;
    }

    @Override
    public QuicStreamChannel read() {
        super.read();
        return this;
    }

    @Override
    @Nullable
    public QuicStreamPriority priority() {
        return null;
    }

    @Override
    public ChannelFuture updatePriority(QuicStreamPriority priority, ChannelPromise promise) {
        return promise.setFailure(new UnsupportedOperationException());
    }

    @Override
    @Nullable
    public QuicStreamAddress localAddress() {
        return null;
    }

    @Override
    @Nullable
    public QuicStreamAddress remoteAddress() {
        return null;
    }

    @Override
    public QuicChannel parent() {
        return (QuicChannel) super.parent();
    }

    @Override
    public ChannelConfig config() {
        if (config == null) {
            config = new EmbeddedQuicStreamChannelConfig(super.config());
        }
        return config;
    }

    @Override
    public boolean isLocalCreated() {
        return attr(localCreatedKey).get();
    }

    @Override
    public QuicStreamType type() {
        return attr(streamTypeKey).get();
    }

    @Override
    public long streamId() {
        return attr(streamIdKey).get();
    }

    @Override
    public boolean isInputShutdown() {
        return inputShutdown != null;
    }

    @Override
    public boolean isOutputShutdown() {
        return outputShutdown != null;
    }

    @Override
    public ChannelFuture shutdown(int i, ChannelPromise channelPromise) {
        if (inputShutdown == null) {
            inputShutdown = i;
        }
        if (outputShutdown == null) {
            outputShutdown = i;
        }
        return channelPromise.setSuccess();
    }

    @Override
    public ChannelFuture shutdownInput(int i, ChannelPromise channelPromise) {
        if (inputShutdown == null) {
            inputShutdown = i;
        }
        return channelPromise.setSuccess();
    }

    @Override
    public ChannelFuture shutdownOutput(int i, ChannelPromise channelPromise) {
        if (outputShutdown == null) {
            outputShutdown = i;
        }
        return channelPromise.setSuccess();
    }

    @Override
    public boolean isShutdown() {
        return isInputShutdown() && isOutputShutdown();
    }

    @Override
    public ChannelFuture shutdown(ChannelPromise promise) {
        return shutdown(0, promise);
    }

    @Override
    public ChannelFuture shutdownOutput(ChannelPromise promise) {
        return shutdownOutput(0, promise);
    }

    Integer outputShutdownError() {
        return outputShutdown;
    }

    Integer inputShutdownError() {
        return inputShutdown;
    }

    private static final class EmbeddedQuicStreamChannelConfig implements ChannelConfig {
        private final ChannelConfig config;
        private boolean allowHalfClosure;
        private boolean readFrames;

        EmbeddedQuicStreamChannelConfig(ChannelConfig config) {
            this.config = config;
        }

        EmbeddedQuicStreamChannelConfig setReadFrames(boolean readFrames) {
            this.readFrames = readFrames;
            return this;
        }

        boolean isReadFrames() {
            return readFrames;
        }

        EmbeddedQuicStreamChannelConfig setAllowHalfClosure(boolean allowHalfClosure) {
            this.allowHalfClosure = allowHalfClosure;
            return this;
        }

        @Override
        public EmbeddedQuicStreamChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead) {
            config.setMaxMessagesPerRead(maxMessagesPerRead);
            return this;
        }

        @Override
        public EmbeddedQuicStreamChannelConfig setWriteSpinCount(int writeSpinCount) {
            config.setWriteSpinCount(writeSpinCount);
            return this;
        }

        @Override
        public EmbeddedQuicStreamChannelConfig setAllocator(ByteBufAllocator allocator) {
            config.setAllocator(allocator);
            return this;
        }

        @Override
        public EmbeddedQuicStreamChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator) {
            config.setRecvByteBufAllocator(allocator);
            return this;
        }

        @Override
        public EmbeddedQuicStreamChannelConfig setAutoRead(boolean autoRead) {
            config.setAutoRead(autoRead);
            return this;
        }

        @Override
        public EmbeddedQuicStreamChannelConfig setAutoClose(boolean autoClose) {
            config.setAutoClose(autoClose);
            return this;
        }

        @Override
        public EmbeddedQuicStreamChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator) {
            config.setMessageSizeEstimator(estimator);
            return this;
        }

        @Override
        public EmbeddedQuicStreamChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
            config.setWriteBufferWaterMark(writeBufferWaterMark);
            return this;
        }

        @Override
        public EmbeddedQuicStreamChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
            config.setConnectTimeoutMillis(connectTimeoutMillis);
            return this;
        }

        @Override
        public EmbeddedQuicStreamChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
            config.setWriteBufferHighWaterMark(writeBufferHighWaterMark);
            return this;
        }

        @Override
        public EmbeddedQuicStreamChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
            config.setWriteBufferLowWaterMark(writeBufferLowWaterMark);
            return this;
        }

        boolean isAllowHalfClosure() {
            return allowHalfClosure;
        }

        @Override
        public Map<ChannelOption<?>, Object> getOptions() {
            return config.getOptions();
        }

        @Override
        public boolean setOptions(Map<ChannelOption<?>, ?> options) {
            return config.setOptions(options);
        }

        @Override
        public <T> T getOption(ChannelOption<T> option) {
            return config.getOption(option);
        }

        @Override
        public <T> boolean setOption(ChannelOption<T> option, T value) {
            return config.setOption(option, value);
        }

        @Override
        public int getConnectTimeoutMillis() {
            return config.getConnectTimeoutMillis();
        }

        @Override
        public int getMaxMessagesPerRead() {
            return config.getMaxMessagesPerRead();
        }

        @Override
        public int getWriteSpinCount() {
            return config.getWriteSpinCount();
        }

        @Override
        public ByteBufAllocator getAllocator() {
            return config.getAllocator();
        }

        @Override
        public <T extends RecvByteBufAllocator> T getRecvByteBufAllocator() {
            return config.getRecvByteBufAllocator();
        }

        @Override
        public boolean isAutoRead() {
            return config.isAutoRead();
        }

        @Override
        public boolean isAutoClose() {
            return config.isAutoClose();
        }

        @Override
        public int getWriteBufferHighWaterMark() {
            return config.getWriteBufferHighWaterMark();
        }

        @Override
        public int getWriteBufferLowWaterMark() {
            return config.getWriteBufferLowWaterMark();
        }

        @Override
        public MessageSizeEstimator getMessageSizeEstimator() {
            return config.getMessageSizeEstimator();
        }

        @Override
        public WriteBufferWaterMark getWriteBufferWaterMark() {
            return config.getWriteBufferWaterMark();
        }
    }
}
