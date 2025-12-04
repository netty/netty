/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.sctp.nio;

import com.sun.nio.sctp.Association;
import com.sun.nio.sctp.MessageInfo;
import com.sun.nio.sctp.NotificationHandler;
import com.sun.nio.sctp.SctpChannel;
import com.sun.nio.sctp.SctpStandardSocketOptions;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.nio.AbstractNioMessageChannel;
import io.netty.channel.nio.NioIoOps;
import io.netty.channel.sctp.SctpMessage;
import io.netty.channel.sctp.SctpNotificationHandler;
import io.netty.channel.sctp.SctpServerChannel;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.netty.channel.ChannelOption.SO_RCVBUF;
import static io.netty.channel.ChannelOption.SO_SNDBUF;
import static io.netty.channel.sctp.SctpChannelOption.SCTP_INIT_MAXSTREAMS;
import static io.netty.channel.sctp.SctpChannelOption.SCTP_NODELAY;

/**
 * {@link io.netty.channel.sctp.SctpChannel} implementation which use non-blocking mode and allows to read /
 * write {@link SctpMessage}s to the underlying {@link SctpChannel}.
 *
 * Be aware that not all operations systems support SCTP. Please refer to the documentation of your operation system,
 * to understand what you need to do to use it. Also this feature is only supported on Java 7+.
 */
public class NioSctpChannel extends AbstractNioMessageChannel implements io.netty.channel.sctp.SctpChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(false);

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NioSctpChannel.class);

    private final NioSctpChannelConfig config;

    private final NotificationHandler<?> notificationHandler;
    private ByteBuffer inputCopy;
    private ByteBuffer outputCopy;

    private static SctpChannel newSctpChannel() {
        try {
            return SctpChannel.open();
        } catch (IOException e) {
            throw new ChannelException("Failed to open a sctp channel.", e);
        }
    }

    /**
     * Create a new instance
     */
    public NioSctpChannel(EventLoop eventLoop) {
        this(eventLoop, newSctpChannel());
    }

    /**
     * Create a new instance using {@link SctpChannel}
     */
    public NioSctpChannel(EventLoop eventLoop, SctpChannel sctpChannel) {
        this(eventLoop, null, sctpChannel);
    }

    /**
     * Create a new instance
     *
     * @param parent        the {@link Channel} which is the parent of this {@link NioSctpChannel}
     *                      or {@code null}.
     * @param sctpChannel   the underlying {@link SctpChannel}
     */
    public NioSctpChannel(EventLoop eventLoop, Channel parent, SctpChannel sctpChannel) {
        super(eventLoop, parent, sctpChannel, SelectionKey.OP_READ);
        try {
            sctpChannel.configureBlocking(false);
            config = new NioSctpChannelConfig(this, sctpChannel);
            notificationHandler = new SctpNotificationHandler(this);
        } catch (IOException e) {
            try {
                sctpChannel.close();
            } catch (IOException e2) {
                if (logger.isWarnEnabled()) {
                    logger.warn(
                            "Failed to close a partially initialized sctp channel.", e2);
                }
            }

            throw new ChannelException("Failed to enter non-blocking mode.", e);
        }
    }

    @Override
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) super.localAddress();
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return (InetSocketAddress) super.remoteAddress();
    }

    @Override
    public SctpServerChannel parent() {
        return (SctpServerChannel) super.parent();
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    public Association association() {
        try {
            return javaChannel().association();
        } catch (IOException ignored) {
            return null;
        }
    }

    @Override
    public Set<InetSocketAddress> allLocalAddresses() {
        try {
            final Set<SocketAddress> allLocalAddresses = javaChannel().getAllLocalAddresses();
            final Set<InetSocketAddress> addresses = new LinkedHashSet<InetSocketAddress>(allLocalAddresses.size());
            for (SocketAddress socketAddress : allLocalAddresses) {
                addresses.add((InetSocketAddress) socketAddress);
            }
            return addresses;
        } catch (Throwable ignored) {
            return Collections.emptySet();
        }
    }

    @Override
    public ChannelConfig config() {
        return config;
    }

    @Override
    public Set<InetSocketAddress> allRemoteAddresses() {
        try {
            final Set<SocketAddress> allLocalAddresses = javaChannel().getRemoteAddresses();
            final Set<InetSocketAddress> addresses = new HashSet<InetSocketAddress>(allLocalAddresses.size());
            for (SocketAddress socketAddress : allLocalAddresses) {
                addresses.add((InetSocketAddress) socketAddress);
            }
            return addresses;
        } catch (Throwable ignored) {
            return Collections.emptySet();
        }
    }

    @Override
    protected SctpChannel javaChannel() {
        return (SctpChannel) super.javaChannel();
    }

    @Override
    public boolean isActive() {
        SctpChannel ch = javaChannel();
        return ch.isOpen() && association() != null;
    }

    @Override
    protected SocketAddress localAddress0() {
        try {
            Iterator<SocketAddress> i = javaChannel().getAllLocalAddresses().iterator();
            if (i.hasNext()) {
                return i.next();
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        try {
            Iterator<SocketAddress> i = javaChannel().getRemoteAddresses().iterator();
            if (i.hasNext()) {
                return i.next();
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    @Override
    protected void doBind(SocketAddress localAddress, ChannelPromise promise) {
        try {
            javaChannel().bind(localAddress);
        } catch (Throwable cause) {
            promise.setFailure(cause);
            return;
        }
        promise.setSuccess();
    }

    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        if (localAddress != null) {
            javaChannel().bind(localAddress);
        }

        boolean success = false;
        try {
            boolean connected = javaChannel().connect(remoteAddress);
            if (!connected) {
                addAndSubmit(NioIoOps.CONNECT);
            }
            success = true;
            return connected;
        } finally {
            if (!success) {
                doClose(newPromise());
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
    protected void doDisconnect(ChannelPromise promise)  {
        doClose(promise);
    }

    @Override
    protected void doClose(ChannelPromise promise) {
        try {
            javaChannel().close();
        } catch (Throwable cause) {
            promise.setFailure(cause);
            return;
        }
        promise.setSuccess();
    }

    @Override
    protected int doReadMessages(List<Object> buf) throws Exception {
        SctpChannel ch = javaChannel();

        RecvByteBufAllocator.Handle allocHandle = recvBufAllocHandle();
        ByteBuf buffer = allocHandle.allocate(config().getAllocator());
        boolean free = true;
        try {
            ByteBuffer data = buffer.internalNioBuffer(buffer.writerIndex(), buffer.writableBytes());
            boolean useInputCopy = false;
            int javaVersion = PlatformDependent.javaVersion();
            if (javaVersion >= 22 && javaVersion < 25 && data.isDirect()) {
                // On Java 22 through 24, we need to avoid using ByteBuffer instances that are
                // backed by MemorySegments, because of https://bugs.openjdk.org/browse/JDK-8357268
                if (inputCopy == null || inputCopy.capacity() < data.remaining()) {
                    inputCopy = ByteBuffer.allocateDirect(data.remaining());
                }
                inputCopy.clear();
                inputCopy.limit(data.remaining());
                useInputCopy = true;
            }
            int pos = data.position();

            MessageInfo messageInfo = ch.receive(useInputCopy ? inputCopy : data, null, notificationHandler);
            if (messageInfo == null) {
                return 0;
            }
            if (useInputCopy) {
                inputCopy.flip();
                data.put(inputCopy);
            }

            allocHandle.lastBytesRead(data.position() - pos);
            buf.add(new SctpMessage(messageInfo,
                    buffer.writerIndex(buffer.writerIndex() + allocHandle.lastBytesRead())));
            free = false;
            return 1;
        } catch (Throwable cause) {
            PlatformDependent.throwException(cause);
            return -1;
        }  finally {
            if (free) {
                buffer.release();
            }
        }
    }

    @Override
    protected boolean doWriteMessage(Object msg, ChannelOutboundBuffer in) throws Exception {
        SctpMessage packet = (SctpMessage) msg;
        ByteBuf data = packet.content();
        int dataLen = data.readableBytes();
        if (dataLen == 0) {
            return true;
        }

        ByteBuffer nioData;
        int javaVersion = PlatformDependent.javaVersion();
        if (javaVersion >= 22 && javaVersion < 25 && data.isDirect() ||
                !data.isDirect() || data.nioBufferCount() != 1) {
            // Ensure that we only use a single, direct ByteBuffer when doing SCTP IO.
            // If the ByteBuf is composite, or is on-heap, we do a copy.
            // On Java 22 through 24, we additionally need to avoid using ByteBuffer instances that are
            // backed by MemorySegments, because of https://bugs.openjdk.org/browse/JDK-8357268
            if (outputCopy == null || outputCopy.capacity() < dataLen) {
                outputCopy = ByteBuffer.allocateDirect(dataLen);
            }
            outputCopy.clear();
            outputCopy.limit(dataLen);
            data.readBytes(outputCopy);
            outputCopy.flip();
            nioData = outputCopy;
        } else {
            nioData = data.nioBuffer();
        }

        final MessageInfo mi = MessageInfo.createOutgoing(association(), null, packet.streamIdentifier());
        mi.payloadProtocolID(packet.protocolIdentifier());
        mi.streamNumber(packet.streamIdentifier());
        mi.unordered(packet.isUnordered());

        final int writtenBytes = javaChannel().send(nioData, mi);
        return writtenBytes > 0;
    }

    @Override
    protected final Object filterOutboundMessage(Object msg) throws Exception {
        if (msg instanceof SctpMessage) {
            SctpMessage m = (SctpMessage) msg;
            ByteBuf buf = m.content();
            if (buf.isDirect() && buf.nioBufferCount() == 1) {
                return m;
            }

            return new SctpMessage(m.protocolIdentifier(), m.streamIdentifier(), m.isUnordered(),
                                   newDirectBuffer(m, buf));
        }

        throw new UnsupportedOperationException(
                "unsupported message type: " + StringUtil.simpleClassName(msg) +
                " (expected: " + StringUtil.simpleClassName(SctpMessage.class));
    }

    @Override
    public ChannelFuture bindAddress(InetAddress localAddress) {
        return bindAddress(localAddress, newPromise());
    }

    @Override
    public ChannelFuture bindAddress(final InetAddress localAddress, final ChannelPromise promise) {
        if (executor().inEventLoop()) {
            try {
                javaChannel().bindAddress(localAddress);
                promise.setSuccess();
            } catch (Throwable t) {
                promise.setFailure(t);
            }
        } else {
            executor().execute(new Runnable() {
                @Override
                public void run() {
                    bindAddress(localAddress, promise);
                }
            });
        }
        return promise;
    }

    @Override
    public ChannelFuture unbindAddress(InetAddress localAddress) {
        return unbindAddress(localAddress, newPromise());
    }

    @Override
    public ChannelFuture unbindAddress(final InetAddress localAddress, final ChannelPromise promise) {
        if (executor().inEventLoop()) {
            try {
                javaChannel().unbindAddress(localAddress);
                promise.setSuccess();
            } catch (Throwable t) {
                promise.setFailure(t);
            }
        } else {
            executor().execute(new Runnable() {
                @Override
                public void run() {
                    unbindAddress(localAddress, promise);
                }
            });
        }
        return promise;
    }

    private final class NioSctpChannelConfig extends DefaultChannelConfig {
        private final SctpChannel javaChannel;

        private NioSctpChannelConfig(NioSctpChannel channel, SctpChannel javaChannel) {
            super(channel);
            this.javaChannel = ObjectUtil.checkNotNull(javaChannel, "javaChannel");

            // Enable TCP_NODELAY by default if possible.
            if (PlatformDependent.canEnableTcpNoDelayByDefault()) {
                try {
                    setSctpNoDelay(true);
                } catch (Exception e) {
                    // Ignore.
                }
            }
        }

        @Override
        public Map<ChannelOption<?>, Object> getOptions() {
            return getOptions(
                    super.getOptions(),
                    SO_RCVBUF, SO_SNDBUF, SCTP_NODELAY, SCTP_INIT_MAXSTREAMS);
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
            if (option == SCTP_NODELAY) {
                return (T) Boolean.valueOf(isSctpNoDelay());
            }
            if (option == SCTP_INIT_MAXSTREAMS) {
                return (T) getInitMaxStreams();
            }
            return super.getOption(option);
        }

        @Override
        public <T> boolean setOption(ChannelOption<T> option, T value) {
            validate(option, value);

            if (option == SO_RCVBUF) {
                setReceiveBufferSize((Integer) value);
            } else if (option == SO_SNDBUF) {
                setSendBufferSize((Integer) value);
            } else if (option == SCTP_NODELAY) {
                setSctpNoDelay((Boolean) value);
            } else if (option == SCTP_INIT_MAXSTREAMS) {
                setInitMaxStreams((SctpStandardSocketOptions.InitMaxStreams) value);
            } else {
                return super.setOption(option, value);
            }

            return true;
        }

        boolean isSctpNoDelay() {
            try {
                return javaChannel.getOption(SctpStandardSocketOptions.SCTP_NODELAY);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
        }

        NioSctpChannelConfig setSctpNoDelay(boolean sctpNoDelay) {
            try {
                javaChannel.setOption(SctpStandardSocketOptions.SCTP_NODELAY, sctpNoDelay);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
            return this;
        }

        int getSendBufferSize() {
            try {
                return javaChannel.getOption(SctpStandardSocketOptions.SO_SNDBUF);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
        }

        NioSctpChannelConfig setSendBufferSize(int sendBufferSize) {
            try {
                javaChannel.setOption(SctpStandardSocketOptions.SO_SNDBUF, sendBufferSize);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
            return this;
        }

        public int getReceiveBufferSize() {
            try {
                return javaChannel.getOption(SctpStandardSocketOptions.SO_RCVBUF);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
        }

        NioSctpChannelConfig setReceiveBufferSize(int receiveBufferSize) {
            try {
                javaChannel.setOption(SctpStandardSocketOptions.SO_RCVBUF, receiveBufferSize);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
            return this;
        }

        SctpStandardSocketOptions.InitMaxStreams getInitMaxStreams() {
            try {
                return javaChannel.getOption(SctpStandardSocketOptions.SCTP_INIT_MAXSTREAMS);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
        }

        NioSctpChannelConfig setInitMaxStreams(SctpStandardSocketOptions.InitMaxStreams initMaxStreams) {
            try {
                javaChannel.setOption(SctpStandardSocketOptions.SCTP_INIT_MAXSTREAMS, initMaxStreams);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
            return this;
        }
        @Override
        protected void autoReadCleared() {
            clearReadPending();
        }
    }
}
