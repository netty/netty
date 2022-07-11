/*
 * Copyright 2015 The Netty Project
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
package io.netty5.channel.epoll;

import io.netty5.channel.Channel;
import io.netty5.channel.ChannelMetadata;
import io.netty5.channel.ChannelOutboundBuffer;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.ChannelShutdownDirection;
import io.netty5.channel.EventLoop;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.ServerChannel;
import io.netty5.channel.ServerChannelRecvBufferAllocator;
import io.netty5.channel.unix.UnixChannel;

import java.net.SocketAddress;

public abstract class AbstractEpollServerChannel
        <P extends UnixChannel, L extends SocketAddress, R extends SocketAddress>
        extends AbstractEpollChannel<P, L, R> implements ServerChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(false, 16);

    private final EventLoopGroup childEventLoopGroup;
    // Will hold the remote address after accept(...) was successful.
    // We need 24 bytes for the address as maximum + 1 byte for storing the length.
    // So use 26 bytes as it's a power of two.
    private final byte[] acceptedAddress = new byte[26];

    AbstractEpollServerChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup,
                               Class<? extends Channel> childChannelType, int fd) {
        this(eventLoop, childEventLoopGroup, childChannelType, new LinuxSocket(fd), false);
    }

    AbstractEpollServerChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup,
                               Class<? extends Channel> childChannelType, LinuxSocket fd) {
        this(eventLoop, childEventLoopGroup, childChannelType, fd, isSoErrorZero(fd));
    }

    AbstractEpollServerChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup,
                               Class<? extends Channel> childChannelType, LinuxSocket fd, boolean active) {
        super(null, eventLoop, METADATA, new ServerChannelRecvBufferAllocator(), fd, active);
        this.childEventLoopGroup = validateEventLoopGroup(childEventLoopGroup, "childEventLoopGroup", childChannelType);
    }

    @Override
    public final EventLoopGroup childEventLoopGroup() {
        return childEventLoopGroup;
    }

    @Override
    protected final R remoteAddress0() {
        return null;
    }

    @Override
    protected final void doWrite(ChannelOutboundBuffer in) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected final Object filterOutboundMessage(Object msg) {
        throw new UnsupportedOperationException();
    }

    abstract Channel newChildChannel(int fd, byte[] remote, int offset, int len) throws Exception;

    @Override
    final void epollInReady() {
        assert executor().inEventLoop();
        if (shouldBreakEpollInReady()) {
            clearEpollIn0();
            return;
        }
        final EpollRecvBufferAllocatorHandle allocHandle = recvBufAllocHandle();

        final ChannelPipeline pipeline = pipeline();
        allocHandle.reset();
        allocHandle.attemptedBytesRead(1);
        epollInBefore();

        Throwable exception = null;
        try {
            try {
                do {
                    // lastBytesRead represents the fd. We use lastBytesRead because it must be set so that the
                    // EpollRecvBufferAllocatorHandle knows if it should try to read again or not when autoRead is
                    // enabled.
                    allocHandle.lastBytesRead(socket.accept(acceptedAddress));
                    if (allocHandle.lastBytesRead() == -1) {
                        // this means everything was handled for now
                        break;
                    }
                    allocHandle.incMessagesRead(1);

                    readPending = false;
                    pipeline.fireChannelRead(newChildChannel(allocHandle.lastBytesRead(), acceptedAddress, 1,
                                                             acceptedAddress[0]));
                } while (allocHandle.continueReading(isAutoRead()) && !isShutdown(ChannelShutdownDirection.Inbound));
            } catch (Throwable t) {
                exception = t;
            }
            allocHandle.readComplete();
            pipeline.fireChannelReadComplete();

            if (exception != null) {
                pipeline.fireChannelExceptionCaught(exception);
            }
            readIfIsAutoRead();
        } finally {
            epollInFinally();
        }
    }

    @Override
    protected final void doShutdown(ChannelShutdownDirection direction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final boolean isShutdown(ChannelShutdownDirection direction) {
        return !isActive();
    }

    @Override
    protected final boolean doFinishConnect(R requestedRemoteAddress) {
        // Connect not supported by ServerChannel implementations
        throw new UnsupportedOperationException();
    }

    @Override
    protected final boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) {
        throw new UnsupportedOperationException();
    }
}
