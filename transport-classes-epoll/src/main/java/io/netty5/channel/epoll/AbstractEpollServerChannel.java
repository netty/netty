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

import static java.util.Objects.requireNonNull;

import io.netty5.channel.Channel;
import io.netty5.channel.ChannelConfig;
import io.netty5.channel.ChannelMetadata;
import io.netty5.channel.ChannelOutboundBuffer;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.EventLoop;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.ServerChannel;
import io.netty5.util.concurrent.Promise;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public abstract class AbstractEpollServerChannel extends AbstractEpollChannel implements ServerChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(false, 16);

    private final EventLoopGroup childEventLoopGroup;

    protected AbstractEpollServerChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup, int fd) {
        this(eventLoop, childEventLoopGroup, new LinuxSocket(fd), false);
    }

    AbstractEpollServerChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup, LinuxSocket fd) {
        this(eventLoop, childEventLoopGroup, fd, isSoErrorZero(fd));
    }

    AbstractEpollServerChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup,
                               LinuxSocket fd, boolean active) {
        super(null, eventLoop, fd, active);
        this.childEventLoopGroup = requireNonNull(childEventLoopGroup, "childEventLoopGroup");
    }

    @Override
    public EventLoopGroup childEventLoopGroup() {
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
    protected AbstractEpollUnsafe newUnsafe() {
        return new EpollServerSocketUnsafe();
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Object filterOutboundMessage(Object msg) throws Exception {
        throw new UnsupportedOperationException();
    }

    abstract Channel newChildChannel(int fd, byte[] remote, int offset, int len) throws Exception;

    final class EpollServerSocketUnsafe extends AbstractEpollUnsafe {
        // Will hold the remote address after accept(...) was successful.
        // We need 24 bytes for the address as maximum + 1 byte for storing the length.
        // So use 26 bytes as it's a power of two.
        private final byte[] acceptedAddress = new byte[26];

        @Override
        public void connect(SocketAddress socketAddress, SocketAddress socketAddress2, Promise<Void> channelPromise) {
            // Connect not supported by ServerChannel implementations
            channelPromise.setFailure(new UnsupportedOperationException());
        }

        @Override
        void epollInReady() {
            assert executor().inEventLoop();
            final ChannelConfig config = config();
            if (shouldBreakEpollInReady(config)) {
                clearEpollIn0();
                return;
            }
            final EpollRecvBufferAllocatorHandle allocHandle = recvBufAllocHandle();

            final ChannelPipeline pipeline = pipeline();
            allocHandle.reset(config);
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
                    } while (allocHandle.continueReading());
                } catch (Throwable t) {
                    exception = t;
                }
                allocHandle.readComplete();
                pipeline.fireChannelReadComplete();

                if (exception != null) {
                    pipeline.fireExceptionCaught(exception);
                }
                readIfIsAutoRead();
            } finally {
                epollInFinally(config);
            }
        }
    }

    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        throw new UnsupportedOperationException();
    }
}
