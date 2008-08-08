/*
 * Copyright (C) 2008  Trustin Heuiseung Lee
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, 5th Floor, Boston, MA 02110-1301 USA
 */
package net.gleamynode.netty.channel.socket.nio;

import static net.gleamynode.netty.channel.ChannelUpstream.*;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.gleamynode.netty.channel.AbstractChannelPipelineSink;
import net.gleamynode.netty.channel.Channel;
import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.ChannelFuture;
import net.gleamynode.netty.channel.ChannelState;
import net.gleamynode.netty.channel.ChannelStateEvent;
import net.gleamynode.netty.channel.MessageEvent;
import net.gleamynode.netty.pipeline.Pipeline;
import net.gleamynode.netty.util.NamePreservingRunnable;

class NioServerSocketPipelineSink extends AbstractChannelPipelineSink {

    static final Logger logger =
        Logger.getLogger(NioServerSocketPipelineSink.class.getName());
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

    public void elementSunk(
            Pipeline<ChannelEvent> pipeline, ChannelEvent element) throws Exception {
        Channel channel = element.getChannel();
        if (channel instanceof NioServerSocketChannel) {
            handleServerSocket(element);
        } else if (channel instanceof NioSocketChannel) {
            handleAcceptedSocket(element);
        }
    }

    private void handleServerSocket(ChannelEvent element) {
        if (!(element instanceof ChannelStateEvent)) {
            return;
        }

        ChannelStateEvent event = (ChannelStateEvent) element;
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

    private void handleAcceptedSocket(ChannelEvent element) {
        if (element instanceof ChannelStateEvent) {
            ChannelStateEvent event = (ChannelStateEvent) element;
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
        } else if (element instanceof MessageEvent) {
            MessageEvent event = (MessageEvent) element;
            NioSocketChannel channel = (NioSocketChannel) event.getChannel();
            channel.writeBuffer.offer(event);
            NioWorker.write(channel);
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
            bossExecutor.execute(new NamePreservingRunnable(
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
        private volatile boolean started;

        Boss(NioServerSocketChannel channel) {
            this.channel = channel;
        }

        public void run() {
            started = true;
            do {
                try {
                    SocketChannel acceptedSocket = channel.socket.accept();
                    try {
                        Pipeline<ChannelEvent> pipeline =
                            channel.getConfig().getPipelineFactory().getPipeline();
                        pipeline.setSink(
                                ((NioServerSocketChannelFactory) channel.getFactory()).sink);
                        NioWorker worker = nextWorker();
                        worker.register(new NioAcceptedSocketChannel(
                                        channel.getFactory(), pipeline, channel,
                                        acceptedSocket, worker));
                    } catch (Exception e) {
                        logger.log(
                                Level.WARNING,
                                "Failed to initialize an accepted socket.",
                                e);
                        try {
                            acceptedSocket.close();
                        } catch (IOException e2) {
                            logger.log(
                                    Level.WARNING,
                                    "Failed to close a partially accepted socket.",
                                    e2);
                        }
                    }
                } catch (SocketTimeoutException e) {
                    // Thrown every second to stop when requested.
                } catch (IOException e) {
                    logger.log(
                            Level.WARNING,
                            "Failed to accept a connection.", e);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e1) {
                        // Ignore
                    }
                }
            } while (started);
        }
    }
}
