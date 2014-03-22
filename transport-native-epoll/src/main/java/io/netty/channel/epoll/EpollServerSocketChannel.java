/*
 * Copyright 2014 The Netty Project
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

import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.ServerSocketChannelConfig;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * {@link ServerSocketChannel} implementation that uses linux EPOLL Edge-Triggered Mode for
 * maximal performance.
 */
public final class EpollServerSocketChannel extends AbstractEpollChannel implements ServerSocketChannel {

    private final EpollServerSocketChannelConfig config;
    private volatile InetSocketAddress local;

    public EpollServerSocketChannel() {
        super(Native.EPOLLACCEPT);
        config = new EpollServerSocketChannelConfig(this);
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof EpollEventLoop;
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        InetSocketAddress addr = (InetSocketAddress) localAddress;
        checkResolvable(addr);
        Native.bind(fd, addr.getAddress(), addr.getPort());
        local = Native.localAddress(fd);
        Native.listen(fd, config.getBacklog());
        active = true;
    }

    @Override
    public ServerSocketChannelConfig config() {
        return config;
    }

    @Override
    protected InetSocketAddress localAddress0() {
        return local;
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
    protected void doWrite(ChannelOutboundBuffer in) {
        throw new UnsupportedOperationException();
    }

    final class EpollServerSocketUnsafe extends AbstractEpollUnsafe {

        @Override
        public void connect(SocketAddress socketAddress, SocketAddress socketAddress2, ChannelPromise channelPromise) {
            // Connect not supported by ServerChannel implementations
            channelPromise.setFailure(new UnsupportedOperationException());
        }

        @Override
        void epollInReady() {
            assert eventLoop().inEventLoop();
            final ChannelPipeline pipeline = pipeline();
            Throwable exception = null;
            try {
                try {
                    for (;;) {
                        int socketFd = Native.accept(fd);
                        if (socketFd == -1) {
                            // this means everything was handled for now
                            break;
                        }
                        try {
                            readPending = false;
                            pipeline.fireChannelRead(new EpollSocketChannel(EpollServerSocketChannel.this, socketFd));
                        } catch (Throwable t) {
                            // keep on reading as we use epoll ET and need to consume everything from the socket
                            pipeline.fireChannelReadComplete();
                            pipeline.fireExceptionCaught(t);
                        }
                    }
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
                if (!config.isAutoRead() && !readPending) {
                    clearEpollIn();
                }
            }
        }
    }
}
