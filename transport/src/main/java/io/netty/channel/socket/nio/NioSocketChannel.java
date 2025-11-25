/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel.socket.nio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import io.netty.channel.FileRegion;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.AbstractNioByteChannel;
import io.netty.channel.nio.NioIoOps;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannelConfig;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SocketUtils;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.NetworkChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Map;
import java.util.concurrent.Executor;

import static io.netty.channel.ChannelOption.ALLOW_HALF_CLOSURE;
import static io.netty.channel.ChannelOption.IP_TOS;
import static io.netty.channel.ChannelOption.SO_KEEPALIVE;
import static io.netty.channel.ChannelOption.SO_LINGER;
import static io.netty.channel.ChannelOption.SO_RCVBUF;
import static io.netty.channel.ChannelOption.SO_REUSEADDR;
import static io.netty.channel.ChannelOption.SO_SNDBUF;
import static io.netty.channel.ChannelOption.TCP_NODELAY;
import static io.netty.channel.internal.ChannelUtils.MAX_BYTES_PER_GATHERING_WRITE_ATTEMPTED_LOW_THRESHOLD;

/**
 * {@link io.netty.channel.socket.SocketChannel} which uses NIO selector based implementation.
 */
public class NioSocketChannel extends AbstractNioByteChannel implements io.netty.channel.socket.SocketChannel {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NioSocketChannel.class);
    private static final SelectorProvider DEFAULT_SELECTOR_PROVIDER = SelectorProvider.provider();

    private final SocketChannelConfig config;

    private static SocketChannel newChannel(SelectorProvider provider, SocketProtocolFamily family) {
        try {
            if (family == null) {
                return provider.openSocketChannel();
            }
            return provider.openSocketChannel(family.toJdkFamily());
        } catch (IOException e) {
            throw new ChannelException("Failed to open a socket.", e);
        }
    }

    /**
     * Create a new instance
     *
     * @param eventLoop the {@link EventLoop} to use for the {@link Channel}
     */
    public NioSocketChannel(EventLoop eventLoop) {
        this(eventLoop, DEFAULT_SELECTOR_PROVIDER);
    }

    /**
     * Create a new instance using the given {@link SelectorProvider}.
     */
    public NioSocketChannel(EventLoop eventLoop, SelectorProvider provider) {
        this(eventLoop, provider, (SocketProtocolFamily) null);
    }

    /**
     * Create a new instance using the given {@link SelectorProvider} and protocol family (supported only since JDK 15).
     */
    public NioSocketChannel(EventLoop eventLoop, SelectorProvider provider, SocketProtocolFamily family) {
        this(eventLoop, newChannel(provider, family));
    }

    /**
     * Create a new instance using the given {@link SocketChannel}.
     */
    public NioSocketChannel(EventLoop eventLoop, SocketChannel socket) {
        this(eventLoop, null, socket);
    }

    /**
     * Create a new instance using the given {@link SocketProtocolFamily}. If {@code null} is used it will depend
     * on the Operation Systems default which will be chosen.
     *
     * @param eventLoop the {@link EventLoop} to use for the {@link Channel}
     * @param family    the {@link SocketProtocolFamily} to use or {@code null} if the default should be used.
     */
    public NioSocketChannel(EventLoop eventLoop, SocketProtocolFamily family) {
        this(eventLoop, newChannel(DEFAULT_SELECTOR_PROVIDER, family));
    }
    /**
     * Create a new instance
     *
     * @param eventLoop the {@link EventLoop} to use for the {@link Channel}
     * @param parent    the {@link Channel} which created this instance or {@code null} if it was created by the user
     * @param channel   the {@link SocketChannel} which will be used
     */
    protected NioSocketChannel(EventLoop eventLoop, Channel parent, SocketChannel channel) {
        super(eventLoop, parent, channel);
        config = new NioSocketChannelConfig(this, channel);
    }

    @Override
    public ServerSocketChannel parent() {
        return (ServerSocketChannel) super.parent();
    }

    @Override
    public SocketChannelConfig config() {
        return config;
    }

    @Override
    protected SocketChannel javaChannel() {
        return (SocketChannel) super.javaChannel();
    }

    @Override
    public boolean isActive() {
        SocketChannel ch = javaChannel();
        return ch.isOpen() && ch.isConnected();
    }

    @Override
    public boolean isOutputShutdown() {
        return javaChannel().socket().isOutputShutdown() || !isActive();
    }

    @Override
    public boolean isInputShutdown() {
        return javaChannel().socket().isInputShutdown() || !isActive();
    }

    @Override
    public boolean isShutdown() {
        Socket socket = javaChannel().socket();
        return socket.isInputShutdown() && socket.isOutputShutdown() || !isActive();
    }

    @Override
    protected final void doShutdownOutput() throws Exception {
        javaChannel().shutdownOutput();
    }

    @Override
    public ChannelFuture shutdownOutput() {
        return shutdownOutput(newPromise());
    }

    @Override
    public ChannelFuture shutdownOutput(final ChannelPromise promise) {
        final EventLoop loop = executor();
        if (loop.inEventLoop()) {
            ((AbstractUnsafe) unsafe()).shutdownOutput(promise);
        } else {
            loop.execute(new Runnable() {
                @Override
                public void run() {
                    ((AbstractUnsafe) unsafe()).shutdownOutput(promise);
                }
            });
        }
        return promise;
    }

    @Override
    public ChannelFuture shutdownInput() {
        return shutdownInput(newPromise());
    }

    @Override
    protected boolean isInputShutdown0() {
        return isInputShutdown();
    }

    @Override
    public ChannelFuture shutdownInput(final ChannelPromise promise) {
        EventLoop loop = executor();
        if (loop.inEventLoop()) {
            shutdownInput0(promise);
        } else {
            loop.execute(new Runnable() {
                @Override
                public void run() {
                    shutdownInput0(promise);
                }
            });
        }
        return promise;
    }

    @Override
    public ChannelFuture shutdown() {
        return shutdown(newPromise());
    }

    @Override
    public ChannelFuture shutdown(final ChannelPromise promise) {
        ChannelFuture shutdownOutputFuture = shutdownOutput();
        if (shutdownOutputFuture.isDone()) {
            shutdownOutputDone(shutdownOutputFuture, promise);
        } else {
            shutdownOutputFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(final ChannelFuture shutdownOutputFuture) throws Exception {
                    shutdownOutputDone(shutdownOutputFuture, promise);
                }
            });
        }
        return promise;
    }

    private void shutdownOutputDone(final ChannelFuture shutdownOutputFuture, final ChannelPromise promise) {
        ChannelFuture shutdownInputFuture = shutdownInput();
        if (shutdownInputFuture.isDone()) {
            shutdownDone(shutdownOutputFuture, shutdownInputFuture, promise);
        } else {
            shutdownInputFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture shutdownInputFuture) throws Exception {
                    shutdownDone(shutdownOutputFuture, shutdownInputFuture, promise);
                }
            });
        }
    }

    private static void shutdownDone(ChannelFuture shutdownOutputFuture,
                                     ChannelFuture shutdownInputFuture,
                                     ChannelPromise promise) {
        Throwable shutdownOutputCause = shutdownOutputFuture.cause();
        Throwable shutdownInputCause = shutdownInputFuture.cause();
        if (shutdownOutputCause != null) {
            if (shutdownInputCause != null) {
                logger.debug("Exception suppressed because a previous exception occurred.",
                        shutdownInputCause);
            }
            promise.setFailure(shutdownOutputCause);
        } else if (shutdownInputCause != null) {
            promise.setFailure(shutdownInputCause);
        } else {
            promise.setSuccess();
        }
    }
    private void shutdownInput0(final ChannelPromise promise) {
        try {
            shutdownInput0();
            promise.setSuccess();
        } catch (Throwable t) {
            promise.setFailure(t);
        }
    }

    private void shutdownInput0() throws Exception {
        javaChannel().shutdownInput();
    }

    @Override
    protected SocketAddress localAddress0() {
        return javaChannel().socket().getLocalSocketAddress();
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return javaChannel().socket().getRemoteSocketAddress();
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        doBind0(localAddress);
    }

    private void doBind0(SocketAddress localAddress) throws Exception {
        SocketUtils.bind(javaChannel(), localAddress);
    }

    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        if (localAddress != null) {
            doBind0(localAddress);
        }

        boolean success = false;
        try {
            boolean connected = SocketUtils.connect(javaChannel(), remoteAddress);
            if (!connected) {
                addAndSubmit(NioIoOps.CONNECT);
            }
            success = true;
            return connected;
        } finally {
            if (!success) {
                doClose();
            }
        }
    }

    @Override
    protected void doFinishConnect() throws Exception {
        if (!javaChannel().finishConnect()) {
            throw new UnsupportedOperationException("finishConnect is not supported for " + getClass().getName());
        }
    }

    @Override
    protected void doDisconnect() throws Exception {
        doClose();
    }

    @Override
    protected void doClose() throws Exception {
        super.doClose();
        javaChannel().close();
    }

    @Override
    protected int doReadBytes(ByteBuf byteBuf) throws Exception {
        final RecvByteBufAllocator.Handle allocHandle = ((AbstractUnsafe) unsafe()).recvBufAllocHandle();
        allocHandle.attemptedBytesRead(byteBuf.writableBytes());
        return byteBuf.writeBytes(javaChannel(), allocHandle.attemptedBytesRead());
    }

    @Override
    protected int doWriteBytes(ByteBuf buf) throws Exception {
        final int expectedWrittenBytes = buf.readableBytes();
        return buf.readBytes(javaChannel(), expectedWrittenBytes);
    }

    @Override
    protected long doWriteFileRegion(FileRegion region) throws Exception {
        final long position = region.transferred();
        return region.transferTo(javaChannel(), position);
    }

    private void adjustMaxBytesPerGatheringWrite(int attempted, int written, int oldMaxBytesPerGatheringWrite) {
        // By default we track the SO_SNDBUF when ever it is explicitly set. However some OSes may dynamically change
        // SO_SNDBUF (and other characteristics that determine how much data can be written at once) so we should try
        // make a best effort to adjust as OS behavior changes.
        if (attempted == written) {
            if (attempted << 1 > oldMaxBytesPerGatheringWrite) {
                ((NioSocketChannelConfig) config).setMaxBytesPerGatheringWrite(attempted << 1);
            }
        } else if (attempted > MAX_BYTES_PER_GATHERING_WRITE_ATTEMPTED_LOW_THRESHOLD && written < attempted >>> 1) {
            ((NioSocketChannelConfig) config).setMaxBytesPerGatheringWrite(attempted >>> 1);
        }
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        SocketChannel ch = javaChannel();
        int writeSpinCount = config().getWriteSpinCount();
        do {
            if (in.isEmpty()) {
                // All written so clear OP_WRITE
                clearOpWrite();
                // Directly return here so incompleteWrite(...) is not called.
                return;
            }

            // Ensure the pending writes are made of ByteBufs only.
            int maxBytesPerGatheringWrite = ((NioSocketChannelConfig) config).getMaxBytesPerGatheringWrite();
            ByteBuffer[] nioBuffers = in.nioBuffers(1024, maxBytesPerGatheringWrite);
            int nioBufferCnt = in.nioBufferCount();

            // Always use nioBuffers() to workaround data-corruption.
            // See https://github.com/netty/netty/issues/2761
            switch (nioBufferCnt) {
                case 0:
                    // We have something else beside ByteBuffers to write so fallback to normal writes.
                    writeSpinCount -= doWrite0(in);
                    break;
                case 1: {
                    // Only one ByteBuf so use non-gathering write
                    // Zero length buffers are not added to nioBuffers by ChannelOutboundBuffer, so there is no need
                    // to check if the total size of all the buffers is non-zero.
                    ByteBuffer buffer = nioBuffers[0];
                    int attemptedBytes = buffer.remaining();
                    final int localWrittenBytes = ch.write(buffer);
                    if (localWrittenBytes <= 0) {
                        incompleteWrite(true);
                        return;
                    }
                    adjustMaxBytesPerGatheringWrite(attemptedBytes, localWrittenBytes, maxBytesPerGatheringWrite);
                    in.removeBytes(localWrittenBytes);
                    --writeSpinCount;
                    break;
                }
                default: {
                    // Zero length buffers are not added to nioBuffers by ChannelOutboundBuffer, so there is no need
                    // to check if the total size of all the buffers is non-zero.
                    // We limit the max amount to int above so cast is safe
                    long attemptedBytes = in.nioBufferSize();
                    final long localWrittenBytes = ch.write(nioBuffers, 0, nioBufferCnt);
                    if (localWrittenBytes <= 0) {
                        incompleteWrite(true);
                        return;
                    }
                    // Casting to int is safe because we limit the total amount of data in the nioBuffers to int above.
                    adjustMaxBytesPerGatheringWrite((int) attemptedBytes, (int) localWrittenBytes,
                            maxBytesPerGatheringWrite);
                    in.removeBytes(localWrittenBytes);
                    --writeSpinCount;
                    break;
                }
            }
        } while (writeSpinCount > 0);

        incompleteWrite(writeSpinCount < 0);
    }

    @Override
    protected AbstractNioUnsafe newUnsafe() {
        return new NioSocketChannelUnsafe();
    }

    private final class NioSocketChannelUnsafe extends NioByteUnsafe {
        @Override
        protected Executor prepareToClose() {
            try {
                if (javaChannel().isOpen() && config().getSoLinger() > 0) {
                    // We need to cancel this key of the channel so we may not end up in a eventloop spin
                    // because we try to read or write until the actual close happens which may be later due
                    // SO_LINGER handling.
                    // See https://github.com/netty/netty/issues/4449
                    doDeregister();
                    return GlobalEventExecutor.INSTANCE;
                }
            } catch (Throwable ignore) {
                // Ignore the error as the underlying channel may be closed in the meantime and so
                // getSoLinger() may produce an exception. In this case we just return null.
                // See https://github.com/netty/netty/issues/4449
            }
            return null;
        }
    }

    private static final class NioSocketChannelConfig extends DefaultChannelConfig
            implements SocketChannelConfig {

        final NetworkChannel jdkChannel;
        private volatile boolean allowHalfClosure;

        private volatile int maxBytesPerGatheringWrite = Integer.MAX_VALUE;
        private NioSocketChannelConfig(NioSocketChannel channel, NetworkChannel jdkChannel) {
            super(channel);
            this.jdkChannel = ObjectUtil.checkNotNull(jdkChannel, "jdkChannel");

            // Enable TCP_NODELAY by default if possible.
            if (PlatformDependent.canEnableTcpNoDelayByDefault()) {
                try {
                    setTcpNoDelay(true);
                } catch (Exception e) {
                    // Ignore.
                }
            }

            calculateMaxBytesPerGatheringWrite();
        }

        @Override
        protected void autoReadCleared() {
            ((NioSocketChannel) channel).clearReadPending();
        }

        @Override
        public Map<ChannelOption<?>, Object> getOptions() {
            return getOptions(getOptions(super.getOptions(),
                    SO_RCVBUF, SO_SNDBUF, TCP_NODELAY, SO_KEEPALIVE, SO_REUSEADDR, SO_LINGER, IP_TOS,
                    ALLOW_HALF_CLOSURE), NioChannelOption.getOptions(jdkChannel()));
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T getOption(ChannelOption<T> option) {
            if (option instanceof NioChannelOption) {
                return NioChannelOption.getOption(jdkChannel(), (NioChannelOption<T>) option);
            }
            if (option == SO_RCVBUF) {
                return (T) Integer.valueOf(getReceiveBufferSize());
            }
            if (option == SO_SNDBUF) {
                return (T) Integer.valueOf(getSendBufferSize());
            }
            if (option == TCP_NODELAY) {
                return (T) Boolean.valueOf(isTcpNoDelay());
            }
            if (option == SO_KEEPALIVE) {
                return (T) Boolean.valueOf(isKeepAlive());
            }
            if (option == SO_REUSEADDR) {
                return (T) Boolean.valueOf(isReuseAddress());
            }
            if (option == SO_LINGER) {
                return (T) Integer.valueOf(getSoLinger());
            }
            if (option == IP_TOS) {
                return (T) Integer.valueOf(getTrafficClass());
            }
            if (option == ALLOW_HALF_CLOSURE) {
                return (T) Boolean.valueOf(isAllowHalfClosure());
            }

            return super.getOption(option);
        }

        @Override
        public <T> boolean setOption(ChannelOption<T> option, T value) {
            validate(option, value);
            if (option instanceof NioChannelOption) {
                return NioChannelOption.setOption(jdkChannel(), (NioChannelOption<T>) option, value);
            }
            if (option == SO_RCVBUF) {
                setReceiveBufferSize((Integer) value);
            } else if (option == SO_SNDBUF) {
                setSendBufferSize((Integer) value);
            } else if (option == TCP_NODELAY) {
                setTcpNoDelay((Boolean) value);
            } else if (option == SO_KEEPALIVE) {
                setKeepAlive((Boolean) value);
            } else if (option == SO_REUSEADDR) {
                setReuseAddress((Boolean) value);
            } else if (option == SO_LINGER) {
                setSoLinger((Integer) value);
            } else if (option == IP_TOS) {
                setTrafficClass((Integer) value);
            } else if (option == ALLOW_HALF_CLOSURE) {
                setAllowHalfClosure((Boolean) value);
            } else {
                return super.setOption(option, value);
            }

            return true;
        }

        @Override
        public int getReceiveBufferSize() {
            return NioChannelOption.getOption0(jdkChannel, StandardSocketOptions.SO_RCVBUF);
        }

        @Override
        public int getSendBufferSize() {
            return NioChannelOption.getOption0(jdkChannel, StandardSocketOptions.SO_SNDBUF);
        }

        @Override
        public int getSoLinger() {
            return NioChannelOption.getOption0(jdkChannel, StandardSocketOptions.SO_LINGER);
        }

        @Override
        public int getTrafficClass() {
            return NioChannelOption.getOption0(jdkChannel, StandardSocketOptions.IP_TOS);
        }

        @Override
        public boolean isKeepAlive() {
            return NioChannelOption.getOption0(jdkChannel, StandardSocketOptions.SO_KEEPALIVE);
        }

        @Override
        public boolean isReuseAddress() {
            return NioChannelOption.getOption0(jdkChannel, StandardSocketOptions.SO_REUSEADDR);
        }

        @Override
        public boolean isTcpNoDelay() {
            return NioChannelOption.getOption0(jdkChannel, StandardSocketOptions.TCP_NODELAY);
        }

        @Override
        public SocketChannelConfig setKeepAlive(boolean keepAlive) {
            NioChannelOption.setOption0(jdkChannel, StandardSocketOptions.SO_KEEPALIVE, keepAlive);
            return this;
        }

        @Override
        public SocketChannelConfig setPerformancePreferences(
                int connectionTime, int latency, int bandwidth) {
            return this;
        }

        @Override
        public SocketChannelConfig setReceiveBufferSize(int receiveBufferSize) {
            NioChannelOption.setOption0(jdkChannel, StandardSocketOptions.SO_RCVBUF, receiveBufferSize);
            return this;
        }

        @Override
        public SocketChannelConfig setReuseAddress(boolean reuseAddress) {
            NioChannelOption.setOption0(jdkChannel, StandardSocketOptions.SO_REUSEADDR, reuseAddress);
            return this;
        }

        @Override
        public SocketChannelConfig setSendBufferSize(int sendBufferSize) {
            NioChannelOption.setOption0(jdkChannel, StandardSocketOptions.SO_SNDBUF, sendBufferSize);
            calculateMaxBytesPerGatheringWrite();
            return this;
        }

        @Override
        public SocketChannelConfig setSoLinger(int soLinger) {
            NioChannelOption.setOption0(jdkChannel, StandardSocketOptions.SO_LINGER, soLinger);
            return this;
        }

        @Override
        public SocketChannelConfig setTcpNoDelay(boolean tcpNoDelay) {
            NioChannelOption.setOption0(jdkChannel, StandardSocketOptions.TCP_NODELAY, tcpNoDelay);
            return this;
        }

        @Override
        public SocketChannelConfig setTrafficClass(int trafficClass) {
            NioChannelOption.setOption0(jdkChannel, StandardSocketOptions.IP_TOS, trafficClass);
            return this;
        }

        @Override
        public boolean isAllowHalfClosure() {
            return allowHalfClosure;
        }

        @Override
        public SocketChannelConfig setAllowHalfClosure(boolean allowHalfClosure) {
            this.allowHalfClosure = allowHalfClosure;
            return this;
        }

        @Override
        public SocketChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
            super.setConnectTimeoutMillis(connectTimeoutMillis);
            return this;
        }

        @Override
        @Deprecated
        public SocketChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead) {
            super.setMaxMessagesPerRead(maxMessagesPerRead);
            return this;
        }

        @Override
        public SocketChannelConfig setWriteSpinCount(int writeSpinCount) {
            super.setWriteSpinCount(writeSpinCount);
            return this;
        }

        @Override
        public SocketChannelConfig setAllocator(ByteBufAllocator allocator) {
            super.setAllocator(allocator);
            return this;
        }

        @Override
        public SocketChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator) {
            super.setRecvByteBufAllocator(allocator);
            return this;
        }

        @Override
        public SocketChannelConfig setAutoRead(boolean autoRead) {
            super.setAutoRead(autoRead);
            return this;
        }

        @Override
        public SocketChannelConfig setAutoClose(boolean autoClose) {
            super.setAutoClose(autoClose);
            return this;
        }

        @Override
        public SocketChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
            super.setWriteBufferHighWaterMark(writeBufferHighWaterMark);
            return this;
        }

        @Override
        public SocketChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
            super.setWriteBufferLowWaterMark(writeBufferLowWaterMark);
            return this;
        }

        @Override
        public SocketChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
            super.setWriteBufferWaterMark(writeBufferWaterMark);
            return this;
        }

        @Override
        public SocketChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator) {
            super.setMessageSizeEstimator(estimator);
            return this;
        }

        void setMaxBytesPerGatheringWrite(int maxBytesPerGatheringWrite) {
            this.maxBytesPerGatheringWrite = maxBytesPerGatheringWrite;
        }

        int getMaxBytesPerGatheringWrite() {
            return maxBytesPerGatheringWrite;
        }

        private void calculateMaxBytesPerGatheringWrite() {
            // Multiply by 2 to give some extra space in case the OS can process write data faster than we can provide.
            int newSendBufferSize = getSendBufferSize() << 1;
            if (newSendBufferSize > 0) {
                setMaxBytesPerGatheringWrite(newSendBufferSize);
            }
        }

        private SocketChannel jdkChannel() {
            return (SocketChannel) jdkChannel;
        }
    }
}
