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
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.ServerChannel;
import io.netty.util.internal.UnstableApi;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

@UnstableApi
public abstract class AbstractKQueueServerChannel extends AbstractKQueueChannel implements ServerChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(false, 16);

    AbstractKQueueServerChannel(BsdSocket fd) {
        this(fd, isSoErrorZero(fd));
    }

    AbstractKQueueServerChannel(BsdSocket fd, boolean active) {
        super(null, fd, active);
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof KQueueEventLoop;
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
        void readReady(KQueueRecvByteAllocatorHandle allocHandle) {
            assert eventLoop().inEventLoop();
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
            } finally {
                readReadyFinally(config);
            }
        }
    }
}
