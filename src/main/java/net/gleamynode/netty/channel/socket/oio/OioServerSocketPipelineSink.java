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
package net.gleamynode.netty.channel.socket.oio;

import static net.gleamynode.netty.channel.ChannelUpstream.*;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.Executor;
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

class OioServerSocketPipelineSink extends AbstractChannelPipelineSink {

    static final Logger logger =
        Logger.getLogger(OioServerSocketPipelineSink.class.getName());

    final Executor workerExecutor;

    OioServerSocketPipelineSink(Executor workerExecutor) {
        this.workerExecutor = workerExecutor;
    }

    public void elementSunk(
            Pipeline<ChannelEvent> pipeline, ChannelEvent element) throws Exception {
        Channel channel = element.getChannel();
        if (channel instanceof OioServerSocketChannel) {
            handleServerSocket(element);
        } else if (channel instanceof OioAcceptedSocketChannel) {
            handleAcceptedSocket(element);
        }
    }

    private void handleServerSocket(ChannelEvent element) {
        if (!(element instanceof ChannelStateEvent)) {
            return;
        }

        ChannelStateEvent event = (ChannelStateEvent) element;
        OioServerSocketChannel channel =
            (OioServerSocketChannel) event.getChannel();
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
            OioAcceptedSocketChannel channel =
                (OioAcceptedSocketChannel) event.getChannel();
            ChannelFuture future = event.getFuture();
            ChannelState state = event.getState();
            Object value = event.getValue();

            switch (state) {
            case OPEN:
                if (Boolean.FALSE.equals(value)) {
                    OioWorker.close(channel, future);
                }
                break;
            case BOUND:
            case CONNECTED:
                if (value == null) {
                    OioWorker.close(channel, future);
                }
                break;
            case INTEREST_OPS:
                OioWorker.setInterestOps(channel, future, ((Integer) value).intValue());
                break;
            }
        } else if (element instanceof MessageEvent) {
            MessageEvent event = (MessageEvent) element;
            OioSocketChannel channel = (OioSocketChannel) event.getChannel();
            ChannelFuture future = event.getFuture();
            Object message = event.getMessage();
            OioWorker.write(channel, future, message);
        }
    }

    private void bind(
            OioServerSocketChannel channel, ChannelFuture future,
            SocketAddress localAddress) {

        boolean bound = false;
        boolean bossStarted = false;
        try {
            channel.socket.bind(localAddress, channel.getConfig().getBacklog());
            bound = true;

            future.setSuccess();
            localAddress = channel.getLocalAddress();
            fireChannelBound(channel, localAddress);

            Executor bossExecutor =
                ((OioServerSocketChannelFactory) channel.getFactory()).bossExecutor;
            bossExecutor.execute(new NamePreservingRunnable(
                    new Boss(channel),
                    "Old I/O server boss (channelId: " + channel.getId() +
                    ", " + localAddress + ')'));
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

    private void close(OioServerSocketChannel channel, ChannelFuture future) {
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

    private class Boss implements Runnable {
        private final OioServerSocketChannel channel;

        Boss(OioServerSocketChannel channel) {
            this.channel = channel;
        }

        public void run() {
            while (channel.isBound()) {
                try {
                    Socket acceptedSocket = channel.socket.accept();
                    try {
                        Pipeline<ChannelEvent> pipeline =
                            channel.getConfig().getPipelineFactory().getPipeline();
                        pipeline.setSink(
                                ((OioServerSocketChannelFactory) channel.getFactory()).sink);
                        final OioAcceptedSocketChannel acceptedChannel =
                            new OioAcceptedSocketChannel(
                                    channel, channel.getFactory(), pipeline,
                                    acceptedSocket);
                        workerExecutor.execute(
                                new NamePreservingRunnable(
                                        new OioWorker(acceptedChannel),
                                        "Old I/O server worker (parentId: " +
                                        channel.getId() +
                                        ", channelId: " + acceptedChannel.getId() + ", " +
                                        channel.getRemoteAddress() + " => " +
                                        channel.getLocalAddress() + ')'));
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
            }
        }
    }
}
