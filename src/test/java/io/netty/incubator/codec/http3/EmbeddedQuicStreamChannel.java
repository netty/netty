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
package io.netty.incubator.codec.http3;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.EventLoop;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamAddress;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamChannelConfig;
import io.netty.incubator.codec.quic.QuicStreamFrame;
import io.netty.incubator.codec.quic.QuicStreamPriority;
import io.netty.incubator.codec.quic.QuicStreamType;

import java.net.SocketAddress;
import java.util.Map;

final class EmbeddedQuicStreamChannel extends EmbeddedChannel implements QuicStreamChannel {
    private final boolean localCreated;
    private final QuicStreamType type;
    private final long id;
    private QuicStreamChannelConfig config;
    private Integer inputShutdown;
    private Integer outputShutdown;

    EmbeddedQuicStreamChannel(ChannelHandler... handlers) {
        this(null, false, QuicStreamType.BIDIRECTIONAL, 0, handlers);
    }

    EmbeddedQuicStreamChannel(boolean localCreated, QuicStreamType type, long id, ChannelHandler... handlers) {
        this(null, localCreated, type, id, handlers);
    }

    EmbeddedQuicStreamChannel(QuicChannel parent, boolean localCreated, QuicStreamType type,
                              long id, ChannelHandler... handlers) {
        super(parent, DefaultChannelId.newInstance(), true, false, handlers);
        this.localCreated = localCreated;
        this.type = type;
        this.id = id;
    }

    boolean writeInboundWithFin(Object... msgs) {
        shutdownInput();
        boolean written = writeInbound(msgs);
        pipeline().fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE);
        pipeline().fireUserEventTriggered(ChannelInputShutdownReadComplete.INSTANCE);
        return written;
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
    public QuicStreamPriority priority() {
        return null;
    }

    @Override
    public ChannelFuture updatePriority(QuicStreamPriority priority, ChannelPromise promise) {
        return promise.setFailure(new UnsupportedOperationException());
    }

    @Override
    public QuicStreamAddress localAddress() {
        return null;
    }

    @Override
    public QuicStreamAddress remoteAddress() {
        return null;
    }

    @Override
    public QuicChannel parent() {
        return (QuicChannel) super.parent();
    }

    @Override
    public QuicStreamChannelConfig config() {
        if (config == null) {
            config = new EmbeddedQuicStreamChannelConfig(super.config());
        }
        return config;
    }

    @Override
    public boolean isLocalCreated() {
        return localCreated;
    }

    @Override
    public QuicStreamType type() {
        return type;
    }

    @Override
    public long streamId() {
        return id;
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

    Integer outputShutdownError() {
        return outputShutdown;
    }

    Integer inputShutdownError() {
        return inputShutdown;
    }

    private Unsafe unsafe;

    @Override
    public Unsafe unsafe() {
        if (unsafe == null) {
            Unsafe superUnsafe = super.unsafe();
            unsafe = new Unsafe() {
                @Override
                public RecvByteBufAllocator.Handle recvBufAllocHandle() {
                    return superUnsafe.recvBufAllocHandle();
                }

                @Override
                public SocketAddress localAddress() {
                    return superUnsafe.localAddress();
                }

                @Override
                public SocketAddress remoteAddress() {
                    return superUnsafe.remoteAddress();
                }

                @Override
                public void register(EventLoop eventLoop, ChannelPromise promise) {
                    superUnsafe.register(eventLoop, promise);
                }

                @Override
                public void bind(SocketAddress localAddress, ChannelPromise promise) {
                    superUnsafe.bind(localAddress, promise);
                }

                @Override
                public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
                    superUnsafe.connect(remoteAddress, localAddress, promise);
                }

                @Override
                public void disconnect(ChannelPromise promise) {
                    superUnsafe.disconnect(promise);
                }

                @Override
                public void close(ChannelPromise promise) {
                    superUnsafe.close(promise);
                }

                @Override
                public void closeForcibly() {
                    superUnsafe.closeForcibly();
                }

                @Override
                public void deregister(ChannelPromise promise) {
                    superUnsafe.deregister(promise);
                }

                @Override
                public void beginRead() {
                    superUnsafe.beginRead();
                }

                @Override
                public void write(Object msg, ChannelPromise promise) {
                    if (msg instanceof QuicStreamFrame && ((QuicStreamFrame) msg).hasFin()) {
                        // Mimic the API.
                        promise = promise.unvoid().addListener(f -> outputShutdown = 0);
                    }
                    superUnsafe.write(msg, promise);
                }

                @Override
                public void flush() {
                    superUnsafe.flush();
                }

                @Override
                public ChannelPromise voidPromise() {
                    return superUnsafe.voidPromise();
                }

                @Override
                public ChannelOutboundBuffer outboundBuffer() {
                    return superUnsafe.outboundBuffer();
                }
            };
        }
        return unsafe;
    }

    private static final class EmbeddedQuicStreamChannelConfig implements QuicStreamChannelConfig {
        private final ChannelConfig config;
        private boolean allowHalfClosure;

        EmbeddedQuicStreamChannelConfig(ChannelConfig config) {
            this.config = config;
        }

        @Override
        public QuicStreamChannelConfig setReadFrames(boolean readFrames) {
            return this;
        }

        @Override
        public boolean isReadFrames() {
            return false;
        }

        @Override
        public QuicStreamChannelConfig setAllowHalfClosure(boolean allowHalfClosure) {
            this.allowHalfClosure = allowHalfClosure;
            return this;
        }

        @Override
        public QuicStreamChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead) {
            config.setMaxMessagesPerRead(maxMessagesPerRead);
            return this;
        }

        @Override
        public QuicStreamChannelConfig setWriteSpinCount(int writeSpinCount) {
            config.setWriteSpinCount(writeSpinCount);
            return this;
        }

        @Override
        public QuicStreamChannelConfig setAllocator(ByteBufAllocator allocator) {
            config.setAllocator(allocator);
            return this;
        }

        @Override
        public QuicStreamChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator) {
            config.setRecvByteBufAllocator(allocator);
            return this;
        }

        @Override
        public QuicStreamChannelConfig setAutoRead(boolean autoRead) {
            config.setAutoRead(autoRead);
            return this;
        }

        @Override
        public QuicStreamChannelConfig setAutoClose(boolean autoClose) {
            config.setAutoClose(autoClose);
            return this;
        }

        @Override
        public QuicStreamChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator) {
            config.setMessageSizeEstimator(estimator);
            return this;
        }

        @Override
        public QuicStreamChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
            config.setWriteBufferWaterMark(writeBufferWaterMark);
            return this;
        }

        @Override
        public QuicStreamChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
            config.setConnectTimeoutMillis(connectTimeoutMillis);
            return this;
        }

        @Override
        public QuicStreamChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
            config.setWriteBufferHighWaterMark(writeBufferHighWaterMark);
            return this;
        }

        @Override
        public QuicStreamChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
            config.setWriteBufferLowWaterMark(writeBufferLowWaterMark);
            return this;
        }

        @Override
        public boolean isAllowHalfClosure() {
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
