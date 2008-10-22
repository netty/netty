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

import java.io.OutputStream;
import java.io.PushbackInputStream;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;

/**
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
class OioWorker implements Runnable {

    private final OioSocketChannel channel;

    OioWorker(OioSocketChannel channel) {
        this.channel = channel;
    }

    public void run() {
        channel.workerThread = Thread.currentThread();
        final PushbackInputStream in = channel.getInputStream();

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

            byte[] buf;
            int readBytes;
            try {
                int bytesToRead = in.available();
                if (bytesToRead > 0) {
                    buf = new byte[bytesToRead];
                    readBytes = in.read(buf);
                } else {
                    int b = in.read();
                    if (b < 0) {
                        break;
                    }
                    in.unread(b);
                    continue;
                }
            } catch (Throwable t) {
                if (!channel.socket.isClosed()) {
                    fireExceptionCaught(channel, t);
                }
                break;
            }

            ChannelBuffer buffer;
            if (readBytes == buf.length) {
                buffer = ChannelBuffers.wrappedBuffer(buf);
            } else {
                // A rare case, but it sometimes happen.
                buffer = ChannelBuffers.wrappedBuffer(buf, 0, readBytes);
            }
            fireMessageReceived(channel, buffer);
        }

        // Setting the workerThread to null will prevent any channel
        // operations from interrupting this thread from now on.
        channel.workerThread = null;

        // Clean up.
        close(channel, channel.getSucceededFuture());
    }

    static void write(
            OioSocketChannel channel, ChannelFuture future,
            Object message) {
        OutputStream out = channel.getOutputStream();
        try {
            ChannelBuffer a = (ChannelBuffer) message;
            synchronized (out) {
                a.getBytes(a.readerIndex(), out, a.readableBytes());
            }
            future.setSuccess();
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    static void setInterestOps(
            OioSocketChannel channel, ChannelFuture future, int interestOps) {

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
                fireChannelInterestChanged(channel, interestOps);
            }
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    static void close(OioSocketChannel channel, ChannelFuture future) {
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

                    if (channel.getInterestOps() != Channel.OP_WRITE) {
                        channel.setInterestOpsNow(Channel.OP_WRITE);
                        fireChannelInterestChanged(channel, Channel.OP_WRITE);
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