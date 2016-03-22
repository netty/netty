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
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.ServerChannel;
import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.Socket;

import java.net.InetSocketAddress;
import java.net.SocketAddress;


public abstract class AbstractEpollServerChannel extends AbstractEpollChannel implements ServerChannel {

    /**
     * @deprecated Use {@link #AbstractEpollServerChannel(Socket, boolean)}.
     */
    @Deprecated
    protected AbstractEpollServerChannel(int fd) {
        this(new Socket(fd), false);
    }

    /**
     * @deprecated Use {@link #AbstractEpollServerChannel(Socket, boolean)}.
     */
    @Deprecated
    protected AbstractEpollServerChannel(FileDescriptor fd) {
        this(new Socket(fd.intValue()));
    }

    /**
     * @deprecated Use {@link #AbstractEpollServerChannel(Socket, boolean)}.
     */
    @Deprecated
    protected AbstractEpollServerChannel(Socket fd) {
        this(fd, isSoErrorZero(fd));
    }

    protected AbstractEpollServerChannel(Socket fd, boolean active) {
        super(null, fd, Native.EPOLLIN, active);
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
        // Will hold the remote address after accept(...) was sucesssful.
        // We need 24 bytes for the address as maximum + 1 byte for storing the length.
        // So use 26 bytes as it's a power of two.
        private final byte[] acceptedAddress = new byte[26];

        @Override
        public void connect(SocketAddress socketAddress, SocketAddress socketAddress2, ChannelPromise channelPromise) {
            // Connect not supported by ServerChannel implementations
            channelPromise.setFailure(new UnsupportedOperationException());
        }

        @Override
        void epollInReady() {
            assert eventLoop().inEventLoop();
            if (fd().isInputShutdown()) {
                return;
            }
            boolean edgeTriggered = isFlagSet(Native.EPOLLET);

            final ChannelConfig config = config();
            if (!readPending && !edgeTriggered && !config.isAutoRead()) {
                // ChannelConfig.setAutoRead(false) was called in the meantime
                clearEpollIn0();
                return;
            }

            final ChannelPipeline pipeline = pipeline();
            Throwable exception = null;
            try {
                try {
                    // if edgeTriggered is used we need to read all messages as we are not notified again otherwise.
                    final int maxMessagesPerRead = edgeTriggered
                            ? Integer.MAX_VALUE : config.getMaxMessagesPerRead();
                    int messages = 0;
                    do {
                        int socketFd = fd().accept(acceptedAddress);
                        if (socketFd == -1) {
                            // this means everything was handled for now
                            break;
                        }
                        readPending = false;

                        try {
                            int len = acceptedAddress[0];
                            pipeline.fireChannelRead(newChildChannel(socketFd, acceptedAddress, 1, len));
                        } catch (Throwable t) {
                            // keep on reading as we use epoll ET and need to consume everything from the socket
                            pipeline.fireChannelReadComplete();
                            pipeline.fireExceptionCaught(t);
                        } finally {
                            if (!edgeTriggered && !config.isAutoRead()) {
                                // This is not using EPOLLET so we can stop reading
                                // ASAP as we will get notified again later with
                                // pending data
                                break;
                            }
                        }
                    } while (++ messages < maxMessagesPerRead || isRdHup());
                } catch (Throwable t) {
                    exception = t;
                }
                pipeline.fireChannelReadComplete();

                if (exception != null) {
                    pipeline.fireExceptionCaught(exception);
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
