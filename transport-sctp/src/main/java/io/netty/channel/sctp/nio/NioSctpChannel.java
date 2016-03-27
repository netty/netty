/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.nio.AbstractNioMessageChannel;
import io.netty.channel.sctp.DefaultSctpChannelConfig;
import io.netty.channel.sctp.SctpChannelConfig;
import io.netty.channel.sctp.SctpMessage;
import io.netty.channel.sctp.SctpNotificationHandler;
import io.netty.channel.sctp.SctpServerChannel;
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
import java.util.Set;

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

    private final SctpChannelConfig config;

    private final NotificationHandler<?> notificationHandler;

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
    public NioSctpChannel() {
        this(newSctpChannel());
    }

    /**
     * Create a new instance using {@link SctpChannel}
     */
    public NioSctpChannel(SctpChannel sctpChannel) {
        this(null, sctpChannel);
    }

    /**
     * Create a new instance
     *
     * @param parent        the {@link Channel} which is the parent of this {@link NioSctpChannel}
     *                      or {@code null}.
     * @param sctpChannel   the underlying {@link SctpChannel}
     */
    public NioSctpChannel(Channel parent, SctpChannel sctpChannel) {
        super(parent, sctpChannel, SelectionKey.OP_READ);
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
    public SctpChannelConfig config() {
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
    protected void doBind(SocketAddress localAddress) throws Exception {
        javaChannel().bind(localAddress);
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
        javaChannel().close();
    }

    @Override
    protected int doReadMessages(List<Object> buf) throws Exception {
        SctpChannel ch = javaChannel();

        RecvByteBufAllocator.Handle allocHandle = unsafe().recvBufAllocHandle();
        ByteBuf buffer = allocHandle.allocate(config().getAllocator());
        boolean free = true;
        try {
            ByteBuffer data = buffer.internalNioBuffer(buffer.writerIndex(), buffer.writableBytes());
            int pos = data.position();

            MessageInfo messageInfo = ch.receive(data, null, notificationHandler);
            if (messageInfo == null) {
                return 0;
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

        ByteBufAllocator alloc = alloc();
        boolean needsCopy = data.nioBufferCount() != 1;
        if (!needsCopy) {
            if (!data.isDirect() && alloc.isDirectBufferPooled()) {
                needsCopy = true;
            }
        }
        ByteBuffer nioData;
        if (!needsCopy) {
            nioData = data.nioBuffer();
        } else {
            data = alloc.directBuffer(dataLen).writeBytes(data);
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
        if (eventLoop().inEventLoop()) {
            try {
                javaChannel().bindAddress(localAddress);
                promise.setSuccess();
            } catch (Throwable t) {
                promise.setFailure(t);
            }
        } else {
            eventLoop().execute(new Runnable() {
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
        if (eventLoop().inEventLoop()) {
            try {
                javaChannel().unbindAddress(localAddress);
                promise.setSuccess();
            } catch (Throwable t) {
                promise.setFailure(t);
            }
        } else {
            eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    unbindAddress(localAddress, promise);
                }
            });
        }
        return promise;
    }

    private final class NioSctpChannelConfig extends DefaultSctpChannelConfig {
        private NioSctpChannelConfig(NioSctpChannel channel, SctpChannel javaChannel) {
            super(channel, javaChannel);
        }

        @Override
        protected void autoReadCleared() {
            clearReadPending();
        }
    }
}
