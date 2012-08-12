/*
 * Copyright 2012 The Netty Project
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

import io.netty.buffer.ChannelBufType;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.ServerSocketChannelConfig;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;

public class AioServerSocketChannel extends AbstractAioChannel implements ServerSocketChannel {

    private static final ChannelMetadata METADATA = new ChannelMetadata(ChannelBufType.MESSAGE, false);

    private static final AcceptHandler ACCEPT_HANDLER = new AcceptHandler();
    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(AioServerSocketChannel.class);

    private final AioServerSocketChannelConfig config;
    private boolean closed;
    private AtomicBoolean readSuspended = new AtomicBoolean();

    private final Runnable acceptTask = new Runnable() {

        @Override
        public void run() {
            doAccept();
        }
    };

    private static AsynchronousServerSocketChannel newSocket(AsynchronousChannelGroup group) {
        try {
            return AsynchronousServerSocketChannel.open(group);
        } catch (IOException e) {
            throw new ChannelException("Failed to open a socket.", e);
        }
    }

    public AioServerSocketChannel(AioEventLoopGroup eventLoop) {
        super(null, null, eventLoop, newSocket(eventLoop.group));
        config = new AioServerSocketChannelConfig(javaChannel());
    }

    @Override
    protected AsynchronousServerSocketChannel javaChannel() {
        return (AsynchronousServerSocketChannel) super.javaChannel();
    }

    @Override
    public boolean isActive() {
        return javaChannel().isOpen() && localAddress0() != null;
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    protected SocketAddress localAddress0() {
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
        ch.bind(localAddress);
        doAccept();
    }

    private void doAccept() {
        if (readSuspended.get()) {
            return;
        }
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
    protected boolean isFlushPending() {
        return false;
    }

    @Override
    protected void doConnect(
            SocketAddress remoteAddress, SocketAddress localAddress, ChannelFuture future) {
        future.setFailure(new UnsupportedOperationException());
    }

    @Override
    protected void doDisconnect() throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Runnable doRegister() throws Exception {
        return super.doRegister();
    }

    private static final class AcceptHandler
            extends AioCompletionHandler<AsynchronousSocketChannel, AioServerSocketChannel> {

        @Override
        protected void completed0(AsynchronousSocketChannel ch, AioServerSocketChannel channel) {
            // register again this handler to accept new connections
            channel.doAccept();

            // create the socket add it to the buffer and fire the event
            channel.pipeline().inboundMessageBuffer().add(
                    new AioSocketChannel(channel, null, channel.group, ch));
            if (!channel.readSuspended.get()) {
                channel.pipeline().fireInboundBufferUpdated();
            }
        }

        @Override
        protected void failed0(Throwable t, AioServerSocketChannel channel) {
            boolean asyncClosed = false;
            if (t instanceof AsynchronousCloseException) {
                asyncClosed = true;
                channel.closed = true;
            }
            // check if the exception was thrown because the channel was closed before
            // log something
            if (channel.isOpen() && ! asyncClosed) {
                logger.warn("Failed to create a new channel from an accepted socket.", t);
            }
        }
    }

    @Override
    public ServerSocketChannelConfig config() {
        return config;
    }

    @Override
    protected Unsafe newUnsafe() {
        return new AioServerSocketUnsafe();
    }

    private final class AioServerSocketUnsafe extends AbstractAioUnsafe {

        @Override
        public void suspendRead() {
            readSuspended.set(true);
        }

        @Override
        public void resumeRead() {
            if (readSuspended.compareAndSet(true, false)) {
                if (eventLoop().inEventLoop()) {
                    doAccept();
                } else {
                    eventLoop().execute(acceptTask);
                }
            }
        }
    }
}
