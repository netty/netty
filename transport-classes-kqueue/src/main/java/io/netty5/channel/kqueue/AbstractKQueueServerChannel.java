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
package io.netty5.channel.kqueue;

import static java.util.Objects.requireNonNull;

import io.netty5.channel.Channel;
import io.netty5.channel.ChannelConfig;
import io.netty5.channel.ChannelMetadata;
import io.netty5.channel.ChannelOutboundBuffer;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.EventLoop;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.ServerChannel;
import io.netty5.util.internal.UnstableApi;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

@UnstableApi
public abstract class AbstractKQueueServerChannel extends AbstractKQueueChannel implements ServerChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(false, 16);
    private final EventLoopGroup childEventLoopGroup;

    AbstractKQueueServerChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup, BsdSocket fd) {
        this(eventLoop, childEventLoopGroup, fd, isSoErrorZero(fd));
    }

    AbstractKQueueServerChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup, BsdSocket fd, boolean active) {
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
    protected AbstractKQueueUnsafe newUnsafe() {
        return new KQueueServerSocketUnsafe();
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

    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        throw new UnsupportedOperationException();
    }

    final class KQueueServerSocketUnsafe extends AbstractKQueueUnsafe {
        // Will hold the remote address after accept(...) was successful.
        // We need 24 bytes for the address as maximum + 1 byte for storing the capacity.
        // So use 26 bytes as it's a power of two.
        private final byte[] acceptedAddress = new byte[26];

        @Override
        void readReady(KQueueRecvBufferAllocatorHandle allocHandle) {
            assert executor().inEventLoop();
            final ChannelConfig config = config();
            if (shouldBreakReadReady(config)) {
                clearReadFilter0();
                return;
            }
            final ChannelPipeline pipeline = pipeline();
            allocHandle.reset(config);
            allocHandle.attemptedBytesRead(1);
            readReadyBefore();

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
                        pipeline.fireChannelRead(newChildChannel(acceptFd, acceptedAddress, 1,
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
                readReadyFinally(config);
            }
        }
    }
}
