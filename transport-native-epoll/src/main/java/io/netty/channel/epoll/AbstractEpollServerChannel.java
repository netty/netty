/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.ServerChannel;
import io.netty.channel.unix.FileDescriptor;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public abstract class AbstractEpollServerChannel extends AbstractEpollChannel implements ServerChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(false, 16);

    protected AbstractEpollServerChannel(int fd) {
        super(fd, Native.EPOLLIN);
    }

    protected AbstractEpollServerChannel(FileDescriptor fd) {
        super(null, fd, Native.EPOLLIN, Native.getSoError(fd.intValue()) == 0);
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof EpollEventLoop;
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
        public void connect(SocketAddress socketAddress, SocketAddress socketAddress2, ChannelPromise channelPromise) {
            // Connect not supported by ServerChannel implementations
            channelPromise.setFailure(new UnsupportedOperationException());
        }

        @Override
        protected EpollRecvByteAllocatorHandle newEpollHandle(RecvByteBufAllocator.Handle handle) {
            return new EpollRecvByteAllocatorMessageHandle(handle, isFlagSet(Native.EPOLLET));
        }

        @Override
        void epollInReady() {
            assert eventLoop().inEventLoop();
            boolean edgeTriggered = isFlagSet(Native.EPOLLET);

            final ChannelConfig config = config();
            if (!readPending && !edgeTriggered && !config.isAutoRead()) {
                // ChannelConfig.setAutoRead(false) was called in the meantime
                clearEpollIn0();
                return;
            }

            final ChannelPipeline pipeline = pipeline();
            final EpollRecvByteAllocatorHandle allocHandle = recvBufAllocHandle();
            allocHandle.reset(config);

            Throwable exception = null;
            try {
                try {
                    do {
                        int socketFd = Native.accept(fd().intValue(), acceptedAddress);
                        if (socketFd == -1) {
                            // this means everything was handled for now
                            break;
                        }
                        readPending = false;
                        allocHandle.incMessagesRead(1);

                        int len = acceptedAddress[0];
                        pipeline.fireChannelRead(newChildChannel(socketFd, acceptedAddress, 1, len));
                    } while (allocHandle.continueReading());
                } catch (Throwable t) {
                    exception = t;
                }
                allocHandle.readComplete();
                pipeline.fireChannelReadComplete();

                if (exception != null) {
                    pipeline.fireExceptionCaught(exception);
                    checkResetEpollIn(edgeTriggered);
                }
            } finally {
                // Check if there is a readPending which was not processed yet.
                // This could be for two reasons:
                // * The user called Channel.read() or ChannelHandlerContext.read() in channelRead(...) method
                // * The user called Channel.read() or ChannelHandlerContext.read() in channelReadComplete(...) method
                //
                // See https://github.com/netty/netty/issues/2254
                if (!readPending && !config.isAutoRead()) {
                    clearEpollIn0();
                }
            }
        }
    }
}
