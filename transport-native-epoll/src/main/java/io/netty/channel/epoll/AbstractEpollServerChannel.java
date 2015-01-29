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
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.ServerChannel;

import java.net.InetSocketAddress;
import java.net.SocketAddress;


public abstract class AbstractEpollServerChannel extends AbstractEpollChannel implements ServerChannel {

    protected AbstractEpollServerChannel(int fd) {
        super(fd, Native.EPOLLACCEPT);
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

    protected abstract Channel newChildChannel(int fd) throws Exception;

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
                        int socketFd = Native.accept(fd().intValue());
                        if (socketFd == -1) {
                            // this means everything was handled for now
                            break;
                        }
                        readPending = false;

                        try {
                            pipeline.fireChannelRead(newChildChannel(socketFd));
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
                if (!config().isAutoRead() && !readPending) {
                    clearEpollIn0();
                }
            }
        }
    }
}
