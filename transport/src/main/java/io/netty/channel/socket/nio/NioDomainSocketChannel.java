/*
 * Copyright 2024 The Netty Project
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
import io.netty.channel.ChannelConfig;
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
import io.netty.channel.ServerChannel;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.AbstractNioByteChannel;
import io.netty.channel.socket.DuplexChannel;
import io.netty.channel.socket.DuplexChannelConfig;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SocketUtils;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.netty.channel.ChannelOption.SO_RCVBUF;
import static io.netty.channel.ChannelOption.SO_SNDBUF;
import static io.netty.channel.internal.ChannelUtils.MAX_BYTES_PER_GATHERING_WRITE_ATTEMPTED_LOW_THRESHOLD;

/**
 * {@link DuplexChannel} which uses NIO selector based implementation to support
 * UNIX Domain Sockets. This is only supported when using Java 16+.
 */
public final class NioDomainSocketChannel extends AbstractNioByteChannel
        implements DuplexChannel {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NioDomainSocketChannel.class);
    private static final SelectorProvider DEFAULT_SELECTOR_PROVIDER = SelectorProvider.provider();

    private static final Method OPEN_SOCKET_CHANNEL_WITH_FAMILY =
            SelectorProviderUtil.findOpenMethod("openSocketChannel");

    private final ChannelConfig config;
    private volatile boolean isInputShutdown;
    private volatile boolean isOutputShutdown;

    static SocketChannel newChannel(SelectorProvider provider) {
        if (PlatformDependent.javaVersion() < 16) {
            throw new UnsupportedOperationException("Only supported on java 16+");
        }
        try {
            SocketChannel channel = SelectorProviderUtil.newDomainSocketChannel(
                    OPEN_SOCKET_CHANNEL_WITH_FAMILY, provider);
            if (channel == null) {
                throw new ChannelException("Failed to open a socket.");
            }
            return channel;
        } catch (IOException e) {
            throw new ChannelException("Failed to open a socket.", e);
        }
    }

    /**
     * Create a new instance
     */
    public NioDomainSocketChannel() {
        this(DEFAULT_SELECTOR_PROVIDER);
    }

    /**
     * Create a new instance using the given {@link SelectorProvider}.
     */
    public NioDomainSocketChannel(SelectorProvider provider) {
        this(newChannel(provider));
    }

    /**
     * Create a new instance using the given {@link SocketChannel}.
     */
    public NioDomainSocketChannel(SocketChannel socket) {
        this(null, socket);
    }

    /**
     * Create a new instance
     *
     * @param parent    the {@link Channel} which created this instance or {@code null} if it was created by the user
     * @param socket    the {@link SocketChannel} which will be used
     */
    public NioDomainSocketChannel(Channel parent, SocketChannel socket) {
        super(parent, socket);
        if (PlatformDependent.javaVersion() < 16) {
            throw new UnsupportedOperationException("Only supported on java 16+");
        }
        config = new NioDomainSocketChannelConfig(this, socket);
    }

    @Override
    public ServerChannel parent() {
        return (ServerChannel) super.parent();
    }

    @Override
    public ChannelConfig config() {
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
        return isOutputShutdown || !isActive();
    }

    @Override
    public boolean isInputShutdown() {
        return isInputShutdown || !isActive();
    }

    @Override
    public boolean isShutdown() {
        return isInputShutdown() && isOutputShutdown() || !isActive();
    }

    @Override
    protected void doShutdownOutput() throws Exception {
        javaChannel().shutdownOutput();
        isOutputShutdown = true;
    }

    @Override
    public ChannelFuture shutdownOutput() {
        return shutdownOutput(newPromise());
    }

    @Override
    public ChannelFuture shutdownOutput(final ChannelPromise promise) {
        final EventLoop loop = eventLoop();
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
        EventLoop loop = eventLoop();
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
        isInputShutdown = true;
    }

    @Override
    protected SocketAddress localAddress0() {
        try {
            return javaChannel().getLocalAddress();
        } catch (Exception ignore) {
            // ignore
        }
        return null;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        try {
            return javaChannel().getRemoteAddress();
        } catch (Exception ignore) {
            // ignore
        }
        return null;
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        SocketUtils.bind(javaChannel(), localAddress);
    }

    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        if (localAddress != null) {
            doBind(localAddress);
        }

        boolean success = false;
        try {
            boolean connected = SocketUtils.connect(javaChannel(), remoteAddress);
            if (!connected) {
                selectionKey().interestOps(SelectionKey.OP_CONNECT);
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
            throw new Error();
        }
    }

    @Override
    protected void doDisconnect() throws Exception {
        doClose();
    }

    @Override
    protected void doClose() throws Exception {
        try {
            super.doClose();
        } finally {
            javaChannel().close();
        }
    }

    @Override
    protected int doReadBytes(ByteBuf byteBuf) throws Exception {
        final RecvByteBufAllocator.Handle allocHandle = unsafe().recvBufAllocHandle();
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
                ((NioDomainSocketChannelConfig) config).setMaxBytesPerGatheringWrite(attempted << 1);
            }
        } else if (attempted > MAX_BYTES_PER_GATHERING_WRITE_ATTEMPTED_LOW_THRESHOLD && written < attempted >>> 1) {
            ((NioDomainSocketChannelConfig) config).setMaxBytesPerGatheringWrite(attempted >>> 1);
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
            int maxBytesPerGatheringWrite = ((NioDomainSocketChannelConfig) config).getMaxBytesPerGatheringWrite();
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
        // Only extending it so we create a new instance in newUnsafe() and return it.
    }

    private final class NioDomainSocketChannelConfig extends DefaultChannelConfig
            implements DuplexChannelConfig {
        private volatile boolean allowHalfClosure;
        private volatile int maxBytesPerGatheringWrite = Integer.MAX_VALUE;
        private final SocketChannel javaChannel;
        private NioDomainSocketChannelConfig(NioDomainSocketChannel channel, SocketChannel javaChannel) {
            super(channel);
            this.javaChannel = javaChannel;
            calculateMaxBytesPerGatheringWrite();
        }

        @Override
        public boolean isAllowHalfClosure() {
            return allowHalfClosure;
        }

        @Override
        public NioDomainSocketChannelConfig setAllowHalfClosure(boolean allowHalfClosure) {
            this.allowHalfClosure = allowHalfClosure;
            return this;
        }
        @Override
        public Map<ChannelOption<?>, Object> getOptions() {
            List<ChannelOption<?>> options = new ArrayList<ChannelOption<?>>();
            options.add(SO_RCVBUF);
            options.add(SO_SNDBUF);
            for (ChannelOption<?> opt : NioChannelOption.getOptions(jdkChannel())) {
                options.add(opt);
            }
            return getOptions(super.getOptions(), options.toArray(new ChannelOption[0]));
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T getOption(ChannelOption<T> option) {
            if (option == SO_RCVBUF) {
                return (T) Integer.valueOf(getReceiveBufferSize());
            }
            if (option == SO_SNDBUF) {
                return (T) Integer.valueOf(getSendBufferSize());
            }
            if (option instanceof NioChannelOption) {
                return NioChannelOption.getOption(jdkChannel(), (NioChannelOption<T>) option);
            }

            return super.getOption(option);
        }

        @Override
        public <T> boolean setOption(ChannelOption<T> option, T value) {
            if (option == SO_RCVBUF) {
                validate(option, value);
                setReceiveBufferSize((Integer) value);
            } else if (option == SO_SNDBUF) {
                validate(option, value);
                setSendBufferSize((Integer) value);
            } else if (option instanceof NioChannelOption) {
                return NioChannelOption.setOption(jdkChannel(), (NioChannelOption<T>) option, value);
            } else {
                return super.setOption(option, value);
            }

            return true;
        }

        private int getReceiveBufferSize() {
            try {
                return javaChannel.getOption(StandardSocketOptions.SO_RCVBUF);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
        }

        private NioDomainSocketChannelConfig setReceiveBufferSize(int receiveBufferSize) {
            try {
                javaChannel.setOption(StandardSocketOptions.SO_RCVBUF, receiveBufferSize);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
            return this;
        }

        private int getSendBufferSize() {
            try {
                return javaChannel.getOption(StandardSocketOptions.SO_SNDBUF);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
        }
        private NioDomainSocketChannelConfig setSendBufferSize(int sendBufferSize) {
            try {
                javaChannel.setOption(StandardSocketOptions.SO_SNDBUF, sendBufferSize);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
            return this;
        }

        @Override
        public NioDomainSocketChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
            super.setConnectTimeoutMillis(connectTimeoutMillis);
            return this;
        }

        @Override
        @Deprecated
        public NioDomainSocketChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead) {
            super.setMaxMessagesPerRead(maxMessagesPerRead);
            return this;
        }

        @Override
        public NioDomainSocketChannelConfig setWriteSpinCount(int writeSpinCount) {
            super.setWriteSpinCount(writeSpinCount);
            return this;
        }

        @Override
        public NioDomainSocketChannelConfig setAllocator(ByteBufAllocator allocator) {
            super.setAllocator(allocator);
            return this;
        }

        @Override
        public NioDomainSocketChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator) {
            super.setRecvByteBufAllocator(allocator);
            return this;
        }

        @Override
        public NioDomainSocketChannelConfig setAutoRead(boolean autoRead) {
            super.setAutoRead(autoRead);
            return this;
        }

        @Override
        public NioDomainSocketChannelConfig setAutoClose(boolean autoClose) {
            super.setAutoClose(autoClose);
            return this;
        }

        @Override
        public NioDomainSocketChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
            super.setWriteBufferHighWaterMark(writeBufferHighWaterMark);
            return this;
        }

        @Override
        public NioDomainSocketChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
            super.setWriteBufferLowWaterMark(writeBufferLowWaterMark);
            return this;
        }

        @Override
        public NioDomainSocketChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
            super.setWriteBufferWaterMark(writeBufferWaterMark);
            return this;
        }

        @Override
        public NioDomainSocketChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator) {
            super.setMessageSizeEstimator(estimator);
            return this;
        }

        @Override
        protected void autoReadCleared() {
            clearReadPending();
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
            return javaChannel;
        }
    }
}
