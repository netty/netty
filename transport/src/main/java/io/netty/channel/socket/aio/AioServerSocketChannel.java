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
package io.netty.channel.socket.aio;

import io.netty.channel.ChannelException;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.aio.AbstractAioChannel;
import io.netty.channel.aio.AioCompletionHandler;
import io.netty.channel.aio.AioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.ServerSocketChannelConfig;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;

/**
 * {@link ServerSocketChannel} implementation which uses NIO2.
 * <p/>
 * NIO2 is only supported on Java 7+.
 */
public class AioServerSocketChannel extends AbstractAioChannel implements ServerSocketChannel {

    private static final ChannelMetadata METADATA = new ChannelMetadata(false);

    private static final AcceptHandler ACCEPT_HANDLER = new AcceptHandler();
    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(AioServerSocketChannel.class);

    private final AioServerSocketChannelConfig config;
    private boolean acceptInProgress;
    private boolean closed;

    /**
     * Create a new instance which has not yet attached an {@link AsynchronousServerSocketChannel}. The {@link
     * AsynchronousServerSocketChannel} will be attached after it was this instance was registered to an {@link
     * EventLoop}.
     */
    public AioServerSocketChannel() {
        super(null, null);
        config = new AioServerSocketChannelConfig(this);
    }

    /**
     * Create a new instance from the given {@link AsynchronousServerSocketChannel}.
     *
     * @param channel the {@link AsynchronousServerSocketChannel} which is used by this instance
     */
    public AioServerSocketChannel(AsynchronousServerSocketChannel channel) {
        super(null, channel);
        config = new AioServerSocketChannelConfig(this, channel);
    }

    private static AsynchronousServerSocketChannel newSocket(AsynchronousChannelGroup group) {
        try {
            return AsynchronousServerSocketChannel.open(group);
        } catch (IOException e) {
            throw new ChannelException("Failed to open a socket.", e);
        }
    }

    @Override
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) super.localAddress();
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return (InetSocketAddress) super.remoteAddress();
    }

    @Override
    protected AsynchronousServerSocketChannel javaChannel() {
        return (AsynchronousServerSocketChannel) super.javaChannel();
    }

    @Override
    public boolean isActive() {
        return ch != null && javaChannel().isOpen() && localAddress0() != null;
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    protected SocketAddress localAddress0() {
        if (ch == null) {
            return null;
        }
        try {
            return javaChannel().getLocalAddress();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return null;
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        AsynchronousServerSocketChannel ch = javaChannel();
        ch.bind(localAddress, config.getBacklog());
    }

    @Override
    protected void doBeginRead() {
        if (acceptInProgress) {
            return;
        }

        acceptInProgress = true;
        javaChannel().accept(this, ACCEPT_HANDLER);
    }

    @Override
    protected void doClose() throws Exception {
        if (!closed) {
            closed = true;
            javaChannel().close();
        }
    }

    @Override
    protected void doConnect(
            SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        promise.setFailure(new UnsupportedOperationException());
    }

    @Override
    protected void doDisconnect() throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void doRegister() throws Exception {
        if (ch == null) {
            AsynchronousServerSocketChannel channel =
                    newSocket(((AioEventLoopGroup) eventLoop().parent()).channelGroup());
            ch = channel;
            config.assign(channel);
        }
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServerSocketChannelConfig config() {
        return config;
    }

    private static final class AcceptHandler
            extends AioCompletionHandler<AsynchronousSocketChannel, AioServerSocketChannel> {

        @Override
        protected void completed0(AsynchronousSocketChannel ch, AioServerSocketChannel channel) {
            channel.acceptInProgress = false;
            ChannelPipeline pipeline = channel.pipeline();

            // create the socket add it to the buffer and fire the event
            pipeline.fireChannelRead(new AioSocketChannel(channel, ch));
            pipeline.fireChannelReadComplete();
        }

        @Override
        protected void failed0(Throwable t, AioServerSocketChannel channel) {
            channel.acceptInProgress = false;
            boolean asyncClosed = false;
            if (t instanceof AsynchronousCloseException) {
                asyncClosed = true;
                channel.closed = true;
            }
            // check if the exception was thrown because the channel was closed before
            // log something
            if (channel.isOpen() && !asyncClosed) {
                logger.warn("Failed to create a new channel from an accepted socket.", t);
                channel.pipeline().fireExceptionCaught(t);
            }
        }
    }
}
