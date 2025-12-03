/*
 * Copyright 2014 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.ServerSocketChannelConfig;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static io.netty.channel.epoll.LinuxSocket.newSocket;
import static io.netty.channel.epoll.Native.IS_SUPPORTING_TCP_FASTOPEN_SERVER;
import static io.netty.channel.unix.NativeInetAddress.address;
import static io.netty.channel.unix.Socket.isIPv6Preferred;

/**
 * {@link ServerSocketChannel} implementation that uses linux EPOLL Edge-Triggered Mode for
 * maximal performance.
 */
public final class EpollServerSocketChannel extends AbstractEpollChannel implements ServerSocketChannel {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(
            EpollServerSocketChannel.class);
    private static final ChannelMetadata METADATA = new ChannelMetadata(false, 16);

    private final EventLoopGroup childEventLoopGroup;

    // Will hold the remote address after accept(...) was successful.
    // We need 24 bytes for the address as maximum + 1 byte for storing the length.
    // So use 26 bytes as it's a power of two.
    private final byte[] acceptedAddress = new byte[26];

    private final EpollServerSocketChannelConfig config;
    private volatile Collection<InetAddress> tcpMd5SigAddresses = Collections.emptyList();

    public EpollServerSocketChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup) {
        this(eventLoop, childEventLoopGroup, (SocketProtocolFamily) null);
    }

    public EpollServerSocketChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup,
                                    SocketProtocolFamily protocol) {
        this(eventLoop, childEventLoopGroup, newSocket(protocol), false);
    }

    public EpollServerSocketChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup, int fd) {
        // Must call this constructor to ensure this object's local address is configured correctly.
        // The local address can only be obtained from a Socket object.
        this(eventLoop, childEventLoopGroup, new LinuxSocket(fd, isIPv6Preferred() ?
                SocketProtocolFamily.INET6 : SocketProtocolFamily.UNIX), false);
    }

    EpollServerSocketChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup, LinuxSocket fd) {
        this(eventLoop, childEventLoopGroup, fd, isSoErrorZero(fd));
    }

    private EpollServerSocketChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup,
                                         LinuxSocket fd, boolean active) {
        super(eventLoop, null, fd, active, EpollIoOps.valueOf(0));
        this.childEventLoopGroup =
                validateEventLoopGroup(childEventLoopGroup, "childEventLoopGroup", EpollIoHandle.class);
        config = new EpollServerSocketChannelConfig(this);
    }

    @Override
    public EventLoopGroup childEventExecutorGroup() {
        return childEventLoopGroup;
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    protected InetSocketAddress remoteAddress0() {
        return null;
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Object filterOutboundMessage(Object msg) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void doConnect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        // Connect not supported by ServerChannel implementations
        promise.setFailure(new UnsupportedOperationException());
    }

    @Override
    void epollInReady() {
        assert executor().inEventLoop();
        final ChannelConfig config = config();
        if (shouldBreakEpollInReady(config)) {
            clearEpollIn0();
            return;
        }
        final RecvByteBufAllocator.Handle allocHandle = recvBufAllocHandle();
        final ChannelPipeline pipeline = pipeline();
        allocHandle.reset(config);
        allocHandle.attemptedBytesRead(1);

        Throwable exception = null;
        try {
            try {
                do {
                    // lastBytesRead represents the fd. We use lastBytesRead because it must be set so that the
                    // EpollRecvByteAllocatorHandle knows if it should try to read again or not when autoRead is
                    // enabled.
                    allocHandle.lastBytesRead(socket.accept(acceptedAddress));
                    if (allocHandle.lastBytesRead() == -1) {
                        // this means everything was handled for now
                        break;
                    }
                    allocHandle.incMessagesRead(1);

                    readPending = false;
                    pipeline.fireChannelRead(newChildChannel(childEventExecutorGroup().next(),
                            allocHandle.lastBytesRead(), acceptedAddress, 1, acceptedAddress[0]));
                } while (allocHandle.continueReading());
            } catch (Throwable t) {
                exception = t;
            }
            allocHandle.readComplete();
            pipeline.fireChannelReadComplete();

            if (exception != null) {
                pipeline.fireExceptionCaught(exception);
            }
        } finally {
            if (shouldStopReading(config)) {
                clearEpollIn();
            }
        }
    }

    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void doBind(SocketAddress localAddress, ChannelPromise promise) {
        super.doBind(localAddress, newPromise().addListener(f -> {
            if (f.isSuccess()) {
                try {
                    final int tcpFastopen;
                    if (IS_SUPPORTING_TCP_FASTOPEN_SERVER && (tcpFastopen = config.getTcpFastopen()) > 0) {
                        socket.setTcpFastOpen(tcpFastopen);
                    }
                    socket.listen(config.getBacklog());
                    active = true;
                } catch (Throwable cause) {
                    promise.setFailure(cause);
                    return;
                }
                promise.setSuccess();
            } else {
                promise.setFailure(f.cause());
            }
        }));
    }

    @Override
    protected void doClose(ChannelPromise promise) {
        if (socket.protocolFamily() == SocketProtocolFamily.UNIX) {
            DomainSocketAddress local = (DomainSocketAddress) localAddress();
            super.doClose(promise.addListener(f -> {
                if (local != null) {
                    // Delete the socket file if possible.
                    File socketFile = new File(local.path());
                    boolean success = socketFile.delete();
                    if (!success && logger.isDebugEnabled()) {
                        logger.debug("Failed to delete a domain socket file: {}", local.path());
                    }
                }
            }));
        } else {
            super.doClose(promise);
        }
    }

    @Override
    public ServerSocketChannelConfig config() {
        return config;
    }

    private Channel newChildChannel(EventLoop eventLoop, int fd, byte[] address, int offset, int len) {
        if (socket.protocolFamily() ==  SocketProtocolFamily.UNIX) {
            return new EpollSocketChannel(eventLoop, this, new LinuxSocket(fd, SocketProtocolFamily.UNIX));
        }
        return new EpollSocketChannel(eventLoop, this,
                new LinuxSocket(fd, socket.protocolFamily()), address(address, offset, len));
    }

    Collection<InetAddress> tcpMd5SigAddresses() {
        return tcpMd5SigAddresses;
    }

    void setTcpMd5Sig(Map<InetAddress, byte[]> keys) throws IOException {
        // Add synchronized as newTcpMp5Sigs might do multiple operations on the socket itself.
        synchronized (this) {
            tcpMd5SigAddresses = TcpMd5Util.newTcpMd5Sigs(this, tcpMd5SigAddresses, keys);
        }
    }
}
