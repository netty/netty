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
package io.netty.channel.socket.nio2;

import io.netty.buffer.ChannelBufType;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ServerChannel;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

public class AsyncServerSocketChannel extends AbstractAsyncChannel implements ServerChannel {

    private static final AcceptHandler ACCEPT_HANDLER = new AcceptHandler();
    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(AsyncServerSocketChannel.class);
    private volatile AsyncServerSocketChannelConfig config;

    public AsyncServerSocketChannel() {
        super(null, null);
    }


    @Override
    protected AsynchronousServerSocketChannel javaChannel() {
        return (AsynchronousServerSocketChannel) super.javaChannel();
    }

    @Override
    public boolean isActive() {
        AsynchronousServerSocketChannel channel = javaChannel();
        try {
            if (channel != null && channel.getLocalAddress() != null) {
                return true;
            }
        } catch (IOException e) {
            return true;
        }
        return false;
    }

    @Override
    public ChannelBufType bufferType() {
        return ChannelBufType.MESSAGE;
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
        javaChannel().bind(localAddress);
        javaChannel().accept(this, ACCEPT_HANDLER);

    }

    @Override
    protected void doClose() throws Exception {
        javaChannel().close();
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
        ch = AsynchronousServerSocketChannel.open(AsynchronousChannelGroup.withThreadPool(eventLoop()));
        config = new AsyncServerSocketChannelConfig(javaChannel());

        return null;
    }

    private static final class AcceptHandler
            implements CompletionHandler<AsynchronousSocketChannel, AsyncServerSocketChannel> {
        public void completed(AsynchronousSocketChannel ch, AsyncServerSocketChannel channel) {
            // register again this handler to accept new connections
            channel.javaChannel().accept(channel, this);

            // create the socket add it to the buffer and fire the event
            channel.pipeline().inboundMessageBuffer().add(new AsyncSocketchannel(channel, null, ch));
            channel.pipeline().fireInboundBufferUpdated();
        }

        public void failed(Throwable t, AsyncServerSocketChannel channel) {
            logger.warn("Failed to create a new channel from an accepted socket.", t);
        }
    }

    @Override
    public AsyncServerSocketChannelConfig config() {
        if (config == null) {
            throw new IllegalStateException("Channel not registered yet");
        }
        return config;
    }
}
