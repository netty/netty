/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.channel.socket.nio;

import static org.jboss.netty.channel.Channels.*;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.netty.channel.AbstractChannelSink;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.ThreadRenamingRunnable;

class NioServerSocketPipelineSink extends AbstractChannelSink {

    static final InternalLogger logger =
        InternalLoggerFactory.getInstance(NioServerSocketPipelineSink.class);
    private static final AtomicInteger nextId = new AtomicInteger();

    private final int id = nextId.incrementAndGet();
    private final NioWorker[] workers;
    private final AtomicInteger workerIndex = new AtomicInteger();

    NioServerSocketPipelineSink(Executor workerExecutor, int workerCount) {
        workers = new NioWorker[workerCount];
        for (int i = 0; i < workers.length; i ++) {
            workers[i] = new NioWorker(id, i + 1, workerExecutor);
        }
    }

    public void eventSunk(
            ChannelPipeline pipeline, ChannelEvent e) throws Exception {
        Channel channel = e.getChannel();
        if (channel instanceof NioServerSocketChannel) {
            handleServerSocket(e);
        } else if (channel instanceof NioSocketChannel) {
            handleAcceptedSocket(e);
        }
    }

    private void handleServerSocket(ChannelEvent e) {
        if (!(e instanceof ChannelStateEvent)) {
            return;
        }

        ChannelStateEvent event = (ChannelStateEvent) e;
        NioServerSocketChannel channel =
            (NioServerSocketChannel) event.getChannel();
        ChannelFuture future = event.getFuture();
        ChannelState state = event.getState();
        Object value = event.getValue();

        switch (state) {
        case OPEN:
            if (Boolean.FALSE.equals(value)) {
                close(channel, future);
            }
            break;
        case BOUND:
            if (value != null) {
                bind(channel, future, (SocketAddress) value);
            } else {
                close(channel, future);
            }
            break;
        }
    }

    private void handleAcceptedSocket(ChannelEvent e) {
        if (e instanceof ChannelStateEvent) {
            ChannelStateEvent event = (ChannelStateEvent) e;
            NioSocketChannel channel = (NioSocketChannel) event.getChannel();
            ChannelFuture future = event.getFuture();
            ChannelState state = event.getState();
            Object value = event.getValue();

            switch (state) {
            case OPEN:
                if (Boolean.FALSE.equals(value)) {
                    NioWorker.close(channel, future);
                }
                break;
            case BOUND:
            case CONNECTED:
                if (value == null) {
                    NioWorker.close(channel, future);
                }
                break;
            case INTEREST_OPS:
                NioWorker.setInterestOps(channel, future, ((Integer) value).intValue());
                break;
            }
        } else if (e instanceof MessageEvent) {
            MessageEvent event = (MessageEvent) e;
            NioSocketChannel channel = (NioSocketChannel) event.getChannel();
            channel.writeBuffer.offer(event);
            NioWorker.write(channel, true);
        }
    }

    private void bind(
            NioServerSocketChannel channel, ChannelFuture future,
            SocketAddress localAddress) {

        boolean bound = false;
        boolean bossStarted = false;
        try {
            channel.socket.socket().bind(localAddress, channel.getConfig().getBacklog());
            bound = true;

            future.setSuccess();
            fireChannelBound(channel, channel.getLocalAddress());

            Executor bossExecutor =
                ((NioServerSocketChannelFactory) channel.getFactory()).bossExecutor;
            bossExecutor.execute(new ThreadRenamingRunnable(
                    new Boss(channel),
                    "New I/O server boss #" + id +" (channelId: " + channel.getId() +
                    ", " + channel.getLocalAddress() + ')'));
            bossStarted = true;
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        } finally {
            if (!bossStarted && bound) {
                close(channel, future);
            }
        }
    }

    private void close(NioServerSocketChannel channel, ChannelFuture future) {
        boolean bound = channel.isBound();
        try {
            channel.socket.close();
            future.setSuccess();
            if (channel.setClosed()) {
                if (bound) {
                    fireChannelUnbound(channel);
                }
                fireChannelClosed(channel);
            }
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    NioWorker nextWorker() {
        return workers[Math.abs(
                workerIndex.getAndIncrement() % workers.length)];
    }

    private class Boss implements Runnable {
        private final NioServerSocketChannel channel;

        Boss(NioServerSocketChannel channel) {
            this.channel = channel;
        }

        public void run() {
            for (;;) {
                try {
                    SocketChannel acceptedSocket = channel.socket.accept();
                    try {
                        ChannelPipeline pipeline =
                            channel.getConfig().getPipelineFactory().getPipeline();
                        NioWorker worker = nextWorker();
                        worker.register(new NioAcceptedSocketChannel(
                                        channel.getFactory(), pipeline, channel,
                                        NioServerSocketPipelineSink.this,
                                        acceptedSocket, worker), null);
                    } catch (Exception e) {
                        logger.warn(
                                "Failed to initialize an accepted socket.", e);
                        try {
                            acceptedSocket.close();
                        } catch (IOException e2) {
                            logger.warn(
                                    "Failed to close a partially accepted socket.",
                                    e2);
                        }
                    }
                } catch (SocketTimeoutException e) {
                    // Thrown every second to get ClosedChannelException
                    // raised.
                } catch (ClosedChannelException e) {
                    // Closed as requested.
                    break;
                } catch (IOException e) {
                    logger.warn(
                            "Failed to accept a connection.", e);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e1) {
                        // Ignore
                    }
                }
            }
        }
    }
}
