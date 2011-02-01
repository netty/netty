/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel.socket.oio;

import static org.jboss.netty.channel.Channels.*;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.Executor;

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
import org.jboss.netty.util.internal.DeadLockProofWorker;

/**
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2352 $, $Date: 2010-08-26 12:13:14 +0900 (Thu, 26 Aug 2010) $
 *
 */
class OioServerSocketPipelineSink extends AbstractChannelSink {

    static final InternalLogger logger =
        InternalLoggerFactory.getInstance(OioServerSocketPipelineSink.class);

    final Executor workerExecutor;

    OioServerSocketPipelineSink(Executor workerExecutor) {
        this.workerExecutor = workerExecutor;
    }

    public void eventSunk(
            ChannelPipeline pipeline, ChannelEvent e) throws Exception {
        Channel channel = e.getChannel();
        if (channel instanceof OioServerSocketChannel) {
            handleServerSocket(e);
        } else if (channel instanceof OioAcceptedSocketChannel) {
            handleAcceptedSocket(e);
        }
    }

    private void handleServerSocket(ChannelEvent e) {
        if (!(e instanceof ChannelStateEvent)) {
            return;
        }

        ChannelStateEvent event = (ChannelStateEvent) e;
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

    private void handleAcceptedSocket(ChannelEvent e) {
        if (e instanceof ChannelStateEvent) {
            ChannelStateEvent event = (ChannelStateEvent) e;
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
        } else if (e instanceof MessageEvent) {
            MessageEvent event = (MessageEvent) e;
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
            DeadLockProofWorker.start(
                    bossExecutor,
                    new ThreadRenamingRunnable(
                            new Boss(channel),
                            "Old I/O server boss (" + channel + ')'));
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

            // Make sure the boss thread is not running so that that the future
            // is notified after a new connection cannot be accepted anymore.
            // See NETTY-256 for more information.
            channel.shutdownLock.lock();
            try {
                if (channel.setClosed()) {
                    future.setSuccess();
                    if (bound) {
                        fireChannelUnbound(channel);
                    }
                    fireChannelClosed(channel);
                } else {
                    future.setSuccess();
                }
            } finally {
                channel.shutdownLock.unlock();
            }
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    private final class Boss implements Runnable {
        private final OioServerSocketChannel channel;

        Boss(OioServerSocketChannel channel) {
            this.channel = channel;
        }

        public void run() {
            channel.shutdownLock.lock();
            try {
                while (channel.isBound()) {
                    try {
                        Socket acceptedSocket = channel.socket.accept();
                        try {
                            ChannelPipeline pipeline =
                                channel.getConfig().getPipelineFactory().getPipeline();
                            final OioAcceptedSocketChannel acceptedChannel =
                                new OioAcceptedSocketChannel(
                                        channel,
                                        channel.getFactory(),
                                        pipeline,
                                        OioServerSocketPipelineSink.this,
                                        acceptedSocket);
                            DeadLockProofWorker.start(
                                    workerExecutor,
                                    new ThreadRenamingRunnable(
                                            new OioWorker(acceptedChannel),
                                            "Old I/O server worker (parentId: " +
                                            channel.getId() + ", " + channel + ')'));
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
                        // Thrown every second to stop when requested.
                    } catch (Throwable e) {
                        // Do not log the exception if the server socket was closed
                        // by a user.
                        if (!channel.socket.isBound() || channel.socket.isClosed()) {
                            break;
                        }

                        logger.warn(
                                "Failed to accept a connection.", e);
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e1) {
                            // Ignore
                        }
                    }
                }
            } finally {
                channel.shutdownLock.unlock();
            }
        }
    }
}
