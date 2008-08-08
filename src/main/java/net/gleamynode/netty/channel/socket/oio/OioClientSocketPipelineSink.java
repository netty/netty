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
import java.io.PushbackInputStream;
import java.net.SocketAddress;

import net.gleamynode.netty.array.ByteArray;
import net.gleamynode.netty.array.HeapByteArray;
import net.gleamynode.netty.channel.AbstractChannelPipelineSink;
import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.ChannelFuture;
import net.gleamynode.netty.channel.ChannelFutureListener;
import net.gleamynode.netty.channel.ChannelState;
import net.gleamynode.netty.channel.ChannelStateEvent;
import net.gleamynode.netty.channel.DefaultChannelStateEvent;
import net.gleamynode.netty.channel.DefaultExceptionEvent;
import net.gleamynode.netty.channel.DefaultMessageEvent;
import net.gleamynode.netty.channel.MessageEvent;
import net.gleamynode.netty.pipeline.Pipeline;
import net.gleamynode.netty.util.NamePreservingRunnable;

class OioClientSocketPipelineSink extends AbstractChannelPipelineSink {

    OioClientSocketPipelineSink() {
        super();
    }

    public void elementSunk(
            Pipeline<ChannelEvent> pipeline, ChannelEvent element) throws Exception {
        OioClientSocketChannel channel = (OioClientSocketChannel) element.getChannel();
        ChannelFuture future = element.getFuture();
        if (element instanceof ChannelStateEvent) {
            ChannelStateEvent stateEvent = (ChannelStateEvent) element;
            ChannelState state = stateEvent.getState();
            Object value = stateEvent.getValue();
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
            case CONNECTED:
                if (value != null) {
                    connect(channel, future, (SocketAddress) value);
                } else {
                    close(channel, future);
                }
                break;
            }
        } else if (element instanceof MessageEvent) {
            write(
                    channel, future,
                    (ByteArray) ((MessageEvent) element).getMessage());
        }
    }

    private void bind(
            OioClientSocketChannel channel, ChannelFuture future,
            SocketAddress localAddress) {
        try {
            channel.socket.bind(localAddress);
            future.setSuccess();
        } catch (IOException e) {
            future.setFailure(e);
        }
    }

    private void connect(
            OioClientSocketChannel channel, ChannelFuture future,
            SocketAddress remoteAddress) {

        boolean bound = channel.isBound();
        boolean connected = false;
        boolean workerStarted = false;

        future.addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture future) {
                future.getChannel().close();
            }
        });

        try {
            channel.socket.connect(
                    remoteAddress, channel.getConfig().getConnectTimeoutMillis());
            connected = true;

            // Obtain I/O stream.
            channel.in = new PushbackInputStream(channel.socket.getInputStream(), 1);
            channel.out = channel.socket.getOutputStream();

            // Fire events.
            if (!bound) {
                channel.getPipeline().sendUpstream(new DefaultChannelStateEvent(
                        channel, channel.succeededFuture,
                        ChannelState.BOUND, channel.getLocalAddress()));
            }
            channel.getPipeline().sendUpstream(new DefaultChannelStateEvent(
                    channel, channel.succeededFuture,
                    ChannelState.CONNECTED, channel.getRemoteAddress()));

            // Start the business.
            channel.factory.workerExecutor.execute(new NamePreservingRunnable(
                    new Worker(channel),
                    "Old I/O client worker (channelId: " + channel.getId() + ", " +
                    channel.getLocalAddress() + " => " +
                    channel.getRemoteAddress() + ')'));

            workerStarted = true;

            future.setSuccess();
        } catch (IOException e) {
            future.setFailure(e);
        } finally {
            if (connected && !workerStarted) {
                close(channel, future);
            }
        }
    }

    private void write(
            OioClientSocketChannel channel, ChannelFuture future, ByteArray array) {
        try {
            array.copyTo(channel.out);
            future.setSuccess();
        } catch (IOException e) {
            future.setFailure(e);
        }
    }

    void close(OioClientSocketChannel channel, ChannelFuture future) {
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

    private class Worker implements Runnable {
        private final OioClientSocketChannel channel;

        Worker(OioClientSocketChannel channel) {
            this.channel = channel;
        }

        public void run() {
            final PushbackInputStream in = channel.in;

            for (;;) {
                byte[] buf;
                try {
                    int bytesToRead = in.available();
                    if (bytesToRead > 0) {
                        buf = new byte[bytesToRead];
                        if (in.read(buf) != bytesToRead) {
                            // Shouldn't reach here.
                            throw new IllegalStateException();
                        }
                    } else {
                        int b = in.read();
                        if (b < 0) {
                            break;
                        }
                        in.unread(b);
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

                channel.getPipeline().sendUpstream(
                        new DefaultMessageEvent(
                                channel, channel.succeededFuture,
                                new HeapByteArray(buf), null));
            }
            close(channel, channel.succeededFuture);
        }
    }
}
