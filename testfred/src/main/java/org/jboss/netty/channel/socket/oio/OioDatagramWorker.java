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
package org.jboss.netty.channel.socket.oio;

import static org.jboss.netty.channel.Channels.*;

import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.net.SocketAddress;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ReceiveBufferSizePredictor;

/**
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
class OioDatagramWorker implements Runnable {

    private final OioDatagramChannel channel;

    OioDatagramWorker(OioDatagramChannel channel) {
        this.channel = channel;
    }

    public void run() {
        channel.workerThread = Thread.currentThread();
        final MulticastSocket socket = channel.socket;

        while (channel.isOpen()) {
            synchronized (this) {
                while (!channel.isReadable()) {
                    try {
                        // notify() is not called at all.
                        // close() and setInterestOps() calls Thread.interrupt()
                        this.wait();
                    } catch (InterruptedException e) {
                        if (!channel.isOpen()) {
                            break;
                        }
                    }
                }
            }

            ReceiveBufferSizePredictor predictor =
                channel.getConfig().getReceiveBufferSizePredictor();

            byte[] buf = new byte[predictor.nextReceiveBufferSize()];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(packet);
            } catch (Throwable t) {
                if (!channel.socket.isClosed()) {
                    fireExceptionCaught(channel, t);
                }
                break;
            }

            ChannelBuffer buffer;
            int readBytes = packet.getLength();
            if (readBytes == buf.length) {
                buffer = ChannelBuffers.wrappedBuffer(buf);
            } else {
                buffer = ChannelBuffers.wrappedBuffer(buf, 0, readBytes);
            }

            fireMessageReceived(channel, buffer, packet.getSocketAddress());
        }

        // Setting the workerThread to null will prevent any channel
        // operations from interrupting this thread from now on.
        channel.workerThread = null;

        // Clean up.
        close(channel, succeededFuture(channel));
    }

    static void write(
            OioDatagramChannel channel, ChannelFuture future,
            Object message, SocketAddress remoteAddress) {
        try {
            // FIXME: Avoid extra copy.
            ChannelBuffer a = (ChannelBuffer) message;
            byte[] b = new byte[a.readableBytes()];
            a.getBytes(0, b);
            channel.socket.send(new DatagramPacket(b, b.length, remoteAddress));
            fireWriteComplete(channel, b.length);
            future.setSuccess();
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    static void setInterestOps(
            OioDatagramChannel channel, ChannelFuture future, int interestOps) {

        // Override OP_WRITE flag - a user cannot change this flag.
        interestOps &= ~Channel.OP_WRITE;
        interestOps |= channel.getInterestOps() & Channel.OP_WRITE;

        boolean changed = false;
        try {
            if (channel.getInterestOps() != interestOps) {
                if ((interestOps & Channel.OP_READ) != 0) {
                    channel.setInterestOpsNow(Channel.OP_READ);
                } else {
                    channel.setInterestOpsNow(Channel.OP_NONE);
                }
                changed = true;
            }

            future.setSuccess();
            if (changed) {
                // Notify the worker so it stops or continues reading.
                Thread currentThread = Thread.currentThread();
                Thread workerThread = channel.workerThread;
                if (workerThread != null && currentThread != workerThread) {
                    workerThread.interrupt();
                }

                channel.setInterestOpsNow(interestOps);
                fireChannelInterestChanged(channel);
            }
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    static void disconnect(OioDatagramChannel channel, ChannelFuture future) {
        boolean connected = channel.isConnected();
        try {
            channel.socket.disconnect();
            future.setSuccess();
            if (connected) {
                fireChannelDisconnected(channel);
            }

            Thread workerThread = channel.workerThread;
            if (workerThread != null) {
                try {
                    workerThread.setName(
                            "Old I/O datagram worker (channelId: " +
                            channel.getId() + ", " + channel.getLocalAddress() + ')');
                } catch (SecurityException e) {
                    // Ignore.
                }
            }

        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    static void close(OioDatagramChannel channel, ChannelFuture future) {
        boolean connected = channel.isConnected();
        boolean bound = channel.isBound();
        try {
            channel.socket.close();
            future.setSuccess();
            if (channel.setClosed()) {
                if (connected) {
                    // Notify the worker so it stops reading.
                    Thread currentThread = Thread.currentThread();
                    Thread workerThread = channel.workerThread;
                    if (workerThread != null && currentThread != workerThread) {
                        workerThread.interrupt();
                    }
                    fireChannelDisconnected(channel);
                }
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
}