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

import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ReceiveBufferSizePredictor;

/**
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2341 $, $Date: 2010-07-07 13:44:23 +0900 (Wed, 07 Jul 2010) $
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
            synchronized (channel.interestOpsLock) {
                while (!channel.isReadable()) {
                    try {
                        // notify() is not called at all.
                        // close() and setInterestOps() calls Thread.interrupt()
                        channel.interestOpsLock.wait();
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
            } catch (InterruptedIOException e) {
                // Can happen on interruption.
                // Keep receiving unless the channel is closed.
                continue;
            } catch (Throwable t) {
                if (!channel.socket.isClosed()) {
                    fireExceptionCaught(channel, t);
                }
                break;
            }

            fireMessageReceived(
                    channel,
                    channel.getConfig().getBufferFactory().getBuffer(buf, 0, packet.getLength()),
                    packet.getSocketAddress());
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
            ChannelBuffer buf = (ChannelBuffer) message;
            int offset = buf.readerIndex();
            int length = buf.readableBytes();
            ByteBuffer nioBuf = buf.toByteBuffer();
            DatagramPacket packet;
            if (nioBuf.hasArray()) {
                // Avoid copy if the buffer is backed by an array.
                packet = new DatagramPacket(
                        nioBuf.array(), nioBuf.arrayOffset() + offset, length);
            } else {
                // Otherwise it will be expensive.
                byte[] arrayBuf = new byte[length];
                buf.getBytes(0, arrayBuf);
                packet = new DatagramPacket(arrayBuf, length);
            }

            if (remoteAddress != null) {
                packet.setSocketAddress(remoteAddress);
            }
            channel.socket.send(packet);
            fireWriteComplete(channel, length);
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
                synchronized (channel.interestOpsLock) {
                    channel.setInterestOpsNow(interestOps);

                    // Notify the worker so it stops or continues reading.
                    Thread currentThread = Thread.currentThread();
                    Thread workerThread = channel.workerThread;
                    if (workerThread != null && currentThread != workerThread) {
                        workerThread.interrupt();
                    }
                }

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
                // Update the worker's thread name to reflect the state change.
                Thread workerThread = channel.workerThread;
                if (workerThread != null) {
                    try {
                        workerThread.setName(
                                "Old I/O datagram worker (" + channel + ')');
                    } catch (SecurityException e) {
                        // Ignore.
                    }
                }

                // Notify.
                fireChannelDisconnected(channel);
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
            if (channel.setClosed()) {
                future.setSuccess();
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
            } else {
                future.setSuccess();
            }
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }
}
