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
package org.jboss.netty.channel.socket.nio;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.util.ThreadNameDeterminer;
import org.jboss.netty.util.ThreadRenamingRunnable;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Executor;

import static org.jboss.netty.channel.Channels.*;

/**
 * Boss implementation which handles accepting of new connections
 */
public final class NioServerBoss extends AbstractNioSelector implements Boss {

    NioServerBoss(Executor bossExecutor) {
        super(bossExecutor);
    }

    NioServerBoss(Executor bossExecutor, ThreadNameDeterminer determiner) {
        super(bossExecutor, determiner);
    }

    void bind(final NioServerSocketChannel channel, final ChannelFuture future,
              final SocketAddress localAddress) {
        registerTask(new RegisterTask(channel, future, localAddress));
    }

    @Override
    protected void close(SelectionKey k) {
        NioServerSocketChannel ch = (NioServerSocketChannel) k.attachment();
        close(ch, succeededFuture(ch));
    }

    void close(NioServerSocketChannel channel, ChannelFuture future) {
        boolean bound = channel.isBound();

        try {
            channel.socket.close();
            increaseCancelledKeys();

            if (channel.setClosed()) {
                future.setSuccess();

                if (bound) {
                    fireChannelUnbound(channel);
                }
                fireChannelClosed(channel);
            } else {
                future.setSuccess();
            }
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    @Override
    protected void process(Selector selector) {
        Set<SelectionKey> selectedKeys = selector.selectedKeys();
        if (selectedKeys.isEmpty()) {
            return;
        }
        for (Iterator<SelectionKey> i = selectedKeys.iterator(); i.hasNext();) {
            SelectionKey k = i.next();
            i.remove();
            NioServerSocketChannel channel = (NioServerSocketChannel) k.attachment();

            try {
                // accept connections in a for loop until no new connection is ready
                for (;;) {
                    SocketChannel acceptedSocket = channel.socket.accept();
                    if (acceptedSocket == null) {
                        break;
                    }
                    registerAcceptedChannel(channel, acceptedSocket, thread);
                }
            } catch (CancelledKeyException e) {
                // Raised by accept() when the server socket was closed.
                k.cancel();
                channel.close();
            } catch (SocketTimeoutException e) {
                // Thrown every second to get ClosedChannelException
                // raised.
            } catch (ClosedChannelException e) {
                // Closed as requested.
            } catch (Throwable t) {
                if (logger.isWarnEnabled()) {
                    logger.warn(
                            "Failed to accept a connection.", t);
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                    // Ignore
                }
            }
        }
    }

    private static void registerAcceptedChannel(NioServerSocketChannel parent, SocketChannel acceptedSocket,
                                         Thread currentThread) {
        try {
            ChannelSink sink = parent.getPipeline().getSink();
            ChannelPipeline pipeline =
                    parent.getConfig().getPipelineFactory().getPipeline();
            NioWorker worker = parent.workerPool.nextWorker();
            worker.register(new NioAcceptedSocketChannel(
                    parent.getFactory(), pipeline, parent, sink
                    , acceptedSocket,
                    worker, currentThread), null);
        } catch (Exception e) {
            if (logger.isWarnEnabled()) {
                logger.warn(
                        "Failed to initialize an accepted socket.", e);
            }

            try {
                acceptedSocket.close();
            } catch (IOException e2) {
                if (logger.isWarnEnabled()) {
                    logger.warn(
                            "Failed to close a partially accepted socket.",
                            e2);
                }
            }
        }
    }

    @Override
    protected int select(Selector selector) throws IOException {
        // Just do a blocking select without any timeout
        // as this thread does not execute anything else.
        return selector.select();
    }

    @Override
    protected ThreadRenamingRunnable newThreadRenamingRunnable(int id, ThreadNameDeterminer determiner) {
        return new ThreadRenamingRunnable(this,
                "New I/O server boss #" + id, determiner);
    }

    @Override
    protected Runnable createRegisterTask(Channel channel, ChannelFuture future) {
        return new RegisterTask((NioServerSocketChannel) channel, future, null);
    }

    private final class RegisterTask implements Runnable {
        private final NioServerSocketChannel channel;
        private final ChannelFuture future;
        private final SocketAddress localAddress;

        public RegisterTask(final NioServerSocketChannel channel, final ChannelFuture future,
                            final SocketAddress localAddress) {
            this.channel = channel;
            this.future = future;
            this.localAddress = localAddress;
        }

        public void run() {
            boolean bound = false;
            boolean registered = false;
            try {
                channel.socket.socket().bind(localAddress, channel.getConfig().getBacklog());
                bound = true;

                future.setSuccess();
                fireChannelBound(channel, channel.getLocalAddress());
                channel.socket.register(selector, SelectionKey.OP_ACCEPT, channel);

                registered = true;
            } catch (Throwable t) {
                future.setFailure(t);
                fireExceptionCaught(channel, t);
            } finally {
                if (!registered && bound) {
                    close(channel, future);
                }
            }
        }
    }
}
