/*
 * Copyright 2016 The Netty Project
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
package io.netty.channel.kqueue;

import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ChannelShutdownType;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.File;
import java.net.SocketAddress;

import static io.netty.channel.kqueue.BsdSocket.newSocket;
import static io.netty.channel.unix.NativeInetAddress.address;
import static io.netty.channel.unix.Socket.isIPv6Preferred;

public final class KQueueServerSocketChannel extends AbstractKQueueChannel implements ServerSocketChannel {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(
            KQueueServerSocketChannel.class);

    private final KQueueServerSocketChannelConfig config;

    private final EventLoopGroup childEventLoopGroup;

    // Will hold the remote address after accept(...) was successful.
    // We need 24 bytes for the address as maximum + 1 byte for storing the capacity.
    private final byte[] acceptedAddress = new byte[25];

    public KQueueServerSocketChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup, int fd) {
        // Must call this constructor to ensure this object's local address is configured correctly.
        // The local address can only be obtained from a Socket object.
        this(eventLoop, childEventLoopGroup, new BsdSocket(fd, isIPv6Preferred() ?
                SocketProtocolFamily.INET6 : SocketProtocolFamily.INET));
    }

    public KQueueServerSocketChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup) {
        this(eventLoop, childEventLoopGroup, (SocketProtocolFamily) null);
    }

    public KQueueServerSocketChannel(
            EventLoop eventLoop, EventLoopGroup childEventLoopGroup, SocketProtocolFamily protocol) {
        this(eventLoop, childEventLoopGroup, newSocket(protocol), false);
    }

    private KQueueServerSocketChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup, BsdSocket fd) {
        this(eventLoop, childEventLoopGroup, fd, isSoErrorZero(fd));
    }

    private KQueueServerSocketChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup,
                                      BsdSocket fd, boolean active) {
        super(eventLoop, null, fd, active, false);
        this.childEventLoopGroup =
                validateEventLoopGroup(childEventLoopGroup, "childEventLoopGroup", KQueueIoHandle.class);
        config = new KQueueServerSocketChannelConfig(this);
    }

    @Override
    protected void doShutdown(ChannelShutdownType type, ChannelPromise promise) {
        promise.setFailure(new UnsupportedOperationException());
    }

    @Override
    public EventLoopGroup childEventExecutorGroup() {
        return childEventLoopGroup;
    }

    @Override
    protected SocketAddress remoteAddress0() {
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
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    void readReady(KQueueRecvByteAllocatorHandle allocHandle) {
        assert executor().inEventLoop();
        final ChannelConfig config = config();
        if (shouldBreakReadReady()) {
            clearReadFilter0();
            return;
        }
        final ChannelPipeline pipeline = pipeline();
        allocHandle.reset(config);
        allocHandle.attemptedBytesRead(1);

        Throwable exception = null;
        try {
            try {
                do {
                    int acceptFd = socket.accept(acceptedAddress);
                    if (acceptFd == -1) {
                        // this means everything was handled for now
                        allocHandle.lastBytesRead(-1);
                        break;
                    }
                    allocHandle.lastBytesRead(1);
                    allocHandle.incMessagesRead(1);

                    readPending = false;
                    pipeline.fireChannelRead(newChildChannel(childEventExecutorGroup().next(),
                            acceptFd, acceptedAddress, 1, acceptedAddress[0]));
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
                clearReadFilter0();
            }
        }
    }

    @Override
    protected void doBind(SocketAddress localAddress, ChannelPromise promise) {
        super.doBind(localAddress, newPromise().addListener(f -> {
            if (f.isSuccess()) {
                try {
                    socket.listen(config.getBacklog());
                    if (config.isTcpFastOpen()) {
                        socket.setTcpFastOpen(true);
                    }
                    active = true;
                } catch (Throwable cause) {
                    promise.setFailure(cause);
                    return;
                }
                promise.setSuccess();
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
    public ChannelConfig config() {
        return config;
    }

    private Channel newChildChannel(EventLoop eventLoop, int fd, byte[] address, int offset, int len) {
        if (socket.protocolFamily() == SocketProtocolFamily.UNIX) {
            return new KQueueSocketChannel(eventLoop, this, new BsdSocket(fd, SocketProtocolFamily.UNIX), true);
        } else {
            return new KQueueSocketChannel(eventLoop, this,
                    new BsdSocket(fd, socket.protocolFamily()), address(address, offset, len));
        }
    }
}
