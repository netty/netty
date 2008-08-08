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

import static net.gleamynode.netty.channel.ChannelUtil.*;

import java.io.OutputStream;
import java.io.PushbackInputStream;

import net.gleamynode.netty.array.ByteArray;
import net.gleamynode.netty.array.HeapByteArray;
import net.gleamynode.netty.array.StaticPartialByteArray;
import net.gleamynode.netty.channel.Channel;
import net.gleamynode.netty.channel.ChannelFuture;

class OioWorker implements Runnable {

    private final OioSocketChannel channel;

    OioWorker(OioSocketChannel channel) {
        this.channel = channel;
    }

    public void run() {
        channel.workerThread = Thread.currentThread();
        final PushbackInputStream in = channel.getInputStream();

        for (;;) {
            synchronized (this) {
                while (!channel.isReadable()) {
                    try {
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

            ByteArray array;
            if (readBytes == buf.length) {
                array = new HeapByteArray(buf);
            } else {
                array = new StaticPartialByteArray(buf, 0, readBytes);
            }
            fireMessageReceived(channel, array);
        }
        close(channel, channel.getSucceededFuture());
    }

    static void write(
            OioSocketChannel channel, ChannelFuture future,
            Object message) {
        OutputStream out = channel.getOutputStream();
        try {
            synchronized (out) {
                ((ByteArray) message).copyTo(out);
            }
            future.setSuccess();
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    static void setInterestOps(
            OioSocketChannel channel, ChannelFuture future, int interestOps) {

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
                // Notify the worker so it stops reading.
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