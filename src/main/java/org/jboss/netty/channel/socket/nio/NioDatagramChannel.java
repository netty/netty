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
package org.jboss.netty.channel.socket.nio;

import static org.jboss.netty.channel.Channels.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.AbstractChannel;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.socket.DatagramChannelConfig;
import org.jboss.netty.channel.socket.nio.SocketSendBufferPool.SendBuffer;
import org.jboss.netty.util.internal.LinkedTransferQueue;
import org.jboss.netty.util.internal.ThreadLocalBoolean;

/**
 * Provides an NIO based {@link org.jboss.netty.channel.socket.DatagramChannel}.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @author Daniel Bevenius (dbevenius@jboss.com)
 *
 * @version $Rev: 2202 $, $Date: 2010-02-23 16:18:58 +0900 (Tue, 23 Feb 2010) $
 */
class NioDatagramChannel extends AbstractChannel
                                implements org.jboss.netty.channel.socket.DatagramChannel {

    /**
     * The {@link DatagramChannelConfig}.
     */
    private final NioDatagramChannelConfig config;

    /**
     * The {@link NioDatagramWorker} for this NioDatagramChannnel.
     */
    final NioDatagramWorker worker;

    /**
     * The {@link DatagramChannel} that this channel uses.
     */
    private final java.nio.channels.DatagramChannel datagramChannel;

    /**
     * Monitor object to synchronize access to InterestedOps.
     */
    final Object interestOpsLock = new Object();

    /**
     * Monitor object for synchronizing access to the {@link WriteRequestQueue}.
     */
    final Object writeLock = new Object();

    /**
     * WriteTask that performs write operations.
     */
    final Runnable writeTask = new WriteTask();

    /**
     * Indicates if there is a {@link WriteTask} in the task queue.
     */
    final AtomicBoolean writeTaskInTaskQueue = new AtomicBoolean();

    /**
     * Queue of write {@link MessageEvent}s.
     */
    final Queue<MessageEvent> writeBufferQueue = new WriteRequestQueue();

    /**
     * Keeps track of the number of bytes that the {@link WriteRequestQueue} currently
     * contains.
     */
    final AtomicInteger writeBufferSize = new AtomicInteger();

    /**
     * Keeps track of the highWaterMark.
     */
    final AtomicInteger highWaterMarkCounter = new AtomicInteger();

    /**
     * The current write {@link MessageEvent}
     */
    MessageEvent currentWriteEvent;
    SendBuffer currentWriteBuffer;

    /**
     * Boolean that indicates that write operation is in progress.
     */
    boolean inWriteNowLoop;
    boolean writeSuspended;

    private volatile InetSocketAddress localAddress;
    volatile InetSocketAddress remoteAddress;

    NioDatagramChannel(final ChannelFactory factory,
            final ChannelPipeline pipeline, final ChannelSink sink,
            final NioDatagramWorker worker) {
        super(null, factory, pipeline, sink);
        this.worker = worker;
        datagramChannel = openNonBlockingChannel();
        config = new DefaultNioDatagramChannelConfig(datagramChannel.socket());

        fireChannelOpen(this);
    }

    private DatagramChannel openNonBlockingChannel() {
        try {
            final DatagramChannel channel = DatagramChannel.open();
            channel.configureBlocking(false);
            return channel;
        } catch (final IOException e) {
            throw new ChannelException("Failed to open a DatagramChannel.", e);
        }
    }

    public InetSocketAddress getLocalAddress() {
        InetSocketAddress localAddress = this.localAddress;
        if (localAddress == null) {
            try {
                this.localAddress = localAddress =
                    (InetSocketAddress) datagramChannel.socket().getLocalSocketAddress();
            } catch (Throwable t) {
                // Sometimes fails on a closed socket in Windows.
                return null;
            }
        }
        return localAddress;
    }

    public InetSocketAddress getRemoteAddress() {
        InetSocketAddress remoteAddress = this.remoteAddress;
        if (remoteAddress == null) {
            try {
                this.remoteAddress = remoteAddress =
                    (InetSocketAddress) datagramChannel.socket().getRemoteSocketAddress();
            } catch (Throwable t) {
                // Sometimes fails on a closed socket in Windows.
                return null;
            }
        }
        return remoteAddress;
    }

    public boolean isBound() {
        return isOpen() && datagramChannel.socket().isBound();
    }

    public boolean isConnected() {
        return datagramChannel.isConnected();
    }

    @Override
    protected boolean setClosed() {
        return super.setClosed();
    }

    public NioDatagramChannelConfig getConfig() {
        return config;
    }

    DatagramChannel getDatagramChannel() {
        return datagramChannel;
    }

    @Override
    public int getInterestOps() {
        if (!isOpen()) {
            return Channel.OP_WRITE;
        }

        int interestOps = getRawInterestOps();
        int writeBufferSize = this.writeBufferSize.get();
        if (writeBufferSize != 0) {
            if (highWaterMarkCounter.get() > 0) {
                int lowWaterMark = getConfig().getWriteBufferLowWaterMark();
                if (writeBufferSize >= lowWaterMark) {
                    interestOps |= Channel.OP_WRITE;
                } else {
                    interestOps &= ~Channel.OP_WRITE;
                }
            } else {
                int highWaterMark = getConfig().getWriteBufferHighWaterMark();
                if (writeBufferSize >= highWaterMark) {
                    interestOps |= Channel.OP_WRITE;
                } else {
                    interestOps &= ~Channel.OP_WRITE;
                }
            }
        } else {
            interestOps &= ~Channel.OP_WRITE;
        }

        return interestOps;
    }

    int getRawInterestOps() {
        return super.getInterestOps();
    }

    void setRawInterestOpsNow(int interestOps) {
        super.setInterestOpsNow(interestOps);
    }

    @Override
    public ChannelFuture write(Object message, SocketAddress remoteAddress) {
        if (remoteAddress == null || remoteAddress.equals(getRemoteAddress())) {
            return super.write(message, null);
        } else {
            return super.write(message, remoteAddress);
        }
    }

    /**
     * {@link WriteRequestQueue} is an extension of {@link LinkedTransferQueue}
     * that adds support for highWaterMark checking of the write buffer size.
     */
    private final class WriteRequestQueue extends
            LinkedTransferQueue<MessageEvent> {

        private static final long serialVersionUID = 5057413071460766376L;

        private final ThreadLocalBoolean notifying = new ThreadLocalBoolean();

        WriteRequestQueue() {
            super();
        }

        /**
         * This method first delegates to {@link LinkedTransferQueue#offer(Object)} and
         * adds support for keeping track of the size of the this write buffer.
         */
        @Override
        public boolean offer(MessageEvent e) {
            boolean success = super.offer(e);
            assert success;

            int messageSize = getMessageSize(e);
            int newWriteBufferSize = writeBufferSize.addAndGet(messageSize);
            int highWaterMark = getConfig().getWriteBufferHighWaterMark();

            if (newWriteBufferSize >= highWaterMark) {
                if (newWriteBufferSize - messageSize < highWaterMark) {
                    highWaterMarkCounter.incrementAndGet();
                    if (!notifying.get()) {
                        notifying.set(Boolean.TRUE);
                        fireChannelInterestChanged(NioDatagramChannel.this);
                        notifying.set(Boolean.FALSE);
                    }
                }
            }
            return true;
        }

        /**
         * This method first delegates to {@link LinkedTransferQueue#poll()} and
         * adds support for keeping track of the size of the this writebuffers queue.
         */
        @Override
        public MessageEvent poll() {
            MessageEvent e = super.poll();
            if (e != null) {
                int messageSize = getMessageSize(e);
                int newWriteBufferSize = writeBufferSize.addAndGet(-messageSize);
                int lowWaterMark = getConfig().getWriteBufferLowWaterMark();

                if (newWriteBufferSize == 0 || newWriteBufferSize < lowWaterMark) {
                    if (newWriteBufferSize + messageSize >= lowWaterMark) {
                        highWaterMarkCounter.decrementAndGet();
                        if (isBound() && !notifying.get()) {
                            notifying.set(Boolean.TRUE);
                            fireChannelInterestChanged(NioDatagramChannel.this);
                            notifying.set(Boolean.FALSE);
                        }
                    }
                }
            }
            return e;
        }

        private int getMessageSize(MessageEvent e) {
            Object m = e.getMessage();
            if (m instanceof ChannelBuffer) {
                return ((ChannelBuffer) m).readableBytes();
            }
            return 0;
        }
    }

    /**
     * WriteTask is a simple runnable performs writes by delegating the {@link NioDatagramWorker}.
     *
     */
    private final class WriteTask implements Runnable {
        WriteTask() {
            super();
        }

        public void run() {
            writeTaskInTaskQueue.set(false);
            worker.writeFromTaskLoop(NioDatagramChannel.this);
        }
    }

    public void joinGroup(InetAddress multicastAddress) {
        throw new UnsupportedOperationException();
    }

    public void joinGroup(InetSocketAddress multicastAddress,
            NetworkInterface networkInterface) {
        throw new UnsupportedOperationException();
    }

    public void leaveGroup(InetAddress multicastAddress) {
        throw new UnsupportedOperationException();
    }

    public void leaveGroup(InetSocketAddress multicastAddress,
            NetworkInterface networkInterface) {
        throw new UnsupportedOperationException();
    }
}
