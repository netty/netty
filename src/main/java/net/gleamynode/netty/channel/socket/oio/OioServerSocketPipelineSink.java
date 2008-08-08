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

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.gleamynode.netty.array.ByteArray;
import net.gleamynode.netty.array.HeapByteArray;
import net.gleamynode.netty.array.PartialByteArray;
import net.gleamynode.netty.channel.AbstractChannelPipelineSink;
import net.gleamynode.netty.channel.Channel;
import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.ChannelFuture;
import net.gleamynode.netty.channel.ChannelState;
import net.gleamynode.netty.channel.ChannelStateEvent;
import net.gleamynode.netty.channel.DefaultChannelStateEvent;
import net.gleamynode.netty.channel.DefaultExceptionEvent;
import net.gleamynode.netty.channel.DefaultMessageEvent;
import net.gleamynode.netty.channel.MessageEvent;
import net.gleamynode.netty.pipeline.Pipeline;
import net.gleamynode.netty.util.NamePreservingRunnable;

class OioServerSocketPipelineSink extends AbstractChannelPipelineSink {

    static final Logger logger =
        Logger.getLogger(OioServerSocketPipelineSink.class.getName());

    OioServerSocketPipelineSink() {
        super();
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
                    close(channel, future);
                }
                break;
            case BOUND:
            case CONNECTED:
                if (value == null) {
                    close(channel, future);
                }
            }
        } else if (element instanceof MessageEvent) {
            MessageEvent event = (MessageEvent) element;
            OioAcceptedSocketChannel channel =
                (OioAcceptedSocketChannel) event.getChannel();
            ChannelFuture future = event.getFuture();
            Object message = event.getMessage();
            write(channel, future, message);
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

            localAddress = channel.getLocalAddress();
            channel.getPipeline().sendUpstream(new DefaultChannelStateEvent(
                    channel, channel.succeededFuture,
                    ChannelState.BOUND, localAddress));

            Executor bossExecutor =
                ((OioServerSocketChannelFactory) channel.getFactory()).bossExecutor;
            bossExecutor.execute(new NamePreservingRunnable(
                    new Boss(channel),
                    "Old I/O server boss (channelId: " + channel.getId() +
                    ", " + localAddress + ')'));
            bossStarted = true;

            future.setSuccess();
        } catch (IOException e) {
            future.setFailure(e);
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
            if (channel.setClosed()) {
                if (bound) {
                    channel.getPipeline().sendUpstream(new DefaultChannelStateEvent(
                            channel, channel.succeededFuture, ChannelState.BOUND, null));
                }
                channel.getPipeline().sendUpstream(new DefaultChannelStateEvent(
                        channel, channel.succeededFuture, ChannelState.OPEN, Boolean.FALSE));
            }
            future.setSuccess();
        } catch (IOException e) {
            future.setFailure(e);
        }
    }

    private void write(
            OioAcceptedSocketChannel channel, ChannelFuture future,
            Object message) {
        try {
            ((ByteArray) message).copyTo(channel.out);
            future.setSuccess();
        } catch (IOException e) {
            future.setFailure(e);
        }
    }

    void close(OioAcceptedSocketChannel channel, ChannelFuture future) {
        boolean connected = channel.isConnected();
        boolean bound = channel.isBound();
        try {
            channel.socket.close();
            if (channel.setClosed()) {
                if (connected) {
                    channel.getPipeline().sendUpstream(new DefaultChannelStateEvent(
                            channel, channel.succeededFuture, ChannelState.CONNECTED, null));
                }
                if (bound) {
                    channel.getPipeline().sendUpstream(new DefaultChannelStateEvent(
                            channel, channel.succeededFuture, ChannelState.BOUND, null));
                }
                channel.getPipeline().sendUpstream(new DefaultChannelStateEvent(
                        channel, channel.succeededFuture, ChannelState.OPEN, Boolean.FALSE));
            }
            future.setSuccess();
        } catch (IOException e) {
            future.setFailure(e);
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
                        pipeline.setSink(channel.factory.sink);
                        channel.factory.workerExecutor.execute(
                                new NamePreservingRunnable(
                                        new Worker(new OioAcceptedSocketChannel(
                                                channel.factory, channel,
                                                acceptedSocket, pipeline)),
                                        "Old I/O server worker (parentId: " +
                                        channel.getParent().getId() +
                                        ", channelId: " + channel.getId() + ", " +
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

    private class Worker implements Runnable {
        private final OioAcceptedSocketChannel channel;

        Worker(OioAcceptedSocketChannel channel) {
            this.channel = channel;
        }

        public void run() {
            for (;;) {
                byte[] buf;
                int readBytes;
                try {
                    int bytesToRead = channel.in.available();
                    if (bytesToRead > 0) {
                        buf = new byte[bytesToRead];
                        readBytes = channel.in.read(buf);
                    } else {
                        int b = channel.in.read();
                        if (b < 0) {
                            break;
                        }
                        channel.in.unread(b);
                        continue;
                    }
                } catch (IOException e) {
                    if (!channel.socket.isClosed()) {
                        channel.getPipeline().sendUpstream(
                                new DefaultExceptionEvent(
                                        channel, channel.succeededFuture, e));
                    }
                    break;
                }

                ByteArray array;
                if (readBytes == buf.length) {
                    array = new HeapByteArray(buf);
                } else {
                    array = new PartialByteArray(new HeapByteArray(buf), 0, readBytes);
                }
                channel.getPipeline().sendUpstream(
                        new DefaultMessageEvent(
                                channel, channel.succeededFuture,
                                array, null));
            }
            close(channel, channel.succeededFuture);
        }
    }
}
