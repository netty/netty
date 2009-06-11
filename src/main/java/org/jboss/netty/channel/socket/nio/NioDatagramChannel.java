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
package org.jboss.netty.channel.socket.nio;

import static org.jboss.netty.channel.Channels.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.AbstractChannel;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.ServerChannel;
import org.jboss.netty.channel.socket.DatagramChannelConfig;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.internal.LinkedTransferQueue;
import org.jboss.netty.util.internal.ThreadLocalBoolean;

/**
 * NioDatagramChannel provides a connection less NIO UDP channel for Netty.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @author Daniel Bevenius (dbevenius@jboss.com)
 *
 * @version $Rev$, $Date$
 */
public class NioDatagramChannel extends AbstractChannel implements
        ServerChannel {
    /**
     * Internal Netty logger.
     */
    private static final InternalLogger logger = InternalLoggerFactory
            .getInstance(NioDatagramChannel.class);

    /**
     * The {@link DatagramChannelConfig}.
     */
    private final NioDatagramChannelConfig config;

    /**
     * The {@link NioUdpWorker} for this NioDatagramChannnel.
     */
    final NioUdpWorker worker;

    /**
     * The {@link DatagramChannel} that this channel uses.
     */
    private final DatagramChannel datagramChannel;

    /**
     * Monitor object to synchronize access to InterestedOps.
     */
    final Object interestOpsLock = new Object();

    /**
     * Monitor object for synchronizing access to the {@link WriteBufferQueue}.
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
    final Queue<MessageEvent> writeBufferQueue = new WriteBufferQueue();

    /**
     * Keeps track of the number of bytes that the {@link WriteBufferQueue} currently
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

    /**
     * The current write index.
     */
    int currentWriteIndex;

    /**
     * Boolean that indicates that write operation is in progress.
     */
    volatile boolean inWriteNowLoop;

    /**
     *
     * @param factory
     * @param pipeline
     * @param sink
     * @param worker
     */
    public NioDatagramChannel(final ChannelFactory factory,
            final ChannelPipeline pipeline, final ChannelSink sink,
            final NioUdpWorker worker) {
        super(null, factory, pipeline, sink);
        this.worker = worker;
        datagramChannel = openNonBlockingChannel();
        setSoTimeout(1000);
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

    private void setSoTimeout(final int timeout) {
        try {
            datagramChannel.socket().setSoTimeout(timeout);
        } catch (final IOException e) {
            try {
                datagramChannel.close();
            } catch (final IOException e2) {
                logger.warn("Failed to close a partially DatagramSocket.", e2);
            }
            throw new ChannelException(
                    "Failed to set the DatagramSocket timeout.", e);
        }
    }

    public InetSocketAddress getLocalAddress() {
        return (InetSocketAddress) datagramChannel.socket()
                .getLocalSocketAddress();
    }

    public SocketAddress getRemoteAddress() {
        return datagramChannel.socket().getRemoteSocketAddress();
    }

    public boolean isBound() {
        return isOpen() && datagramChannel.socket().isBound();
    }

    public boolean isConnected() {
        return datagramChannel.socket().isBound();
    }

    @Override
    protected boolean setClosed() {
        return super.setClosed();
    }

    @Override
    protected ChannelFuture getSucceededFuture() {
        return super.getSucceededFuture();
    }

    public NioDatagramChannelConfig getConfig() {
        return config;
    }

    DatagramChannel getDatagramChannel() {
        return datagramChannel;
    }

    int getRawInterestOps() {
        return super.getInterestOps();
    }

    void setRawInterestOpsNow(int interestOps) {
        super.setInterestOpsNow(interestOps);
    }

    /**
     * WriteBuffer is an extension of {@link LinkedTransferQueue} that adds
     * support for highWaterMark checking of the write buffer size.
     */
    private final class WriteBufferQueue extends
            LinkedTransferQueue<MessageEvent> {
        private final ThreadLocalBoolean notifying = new ThreadLocalBoolean();

        WriteBufferQueue() {
            super();
        }

        /**
         * This method first delegates to {@link LinkedTransferQueue#offer(Object)} and
         * adds support for keeping track of the size of the this write buffer.
         */
        @Override
        public boolean offer(final MessageEvent e) {
            final boolean success = super.offer(e);
            assert success;

            final int messageSize = ((ChannelBuffer) e.getMessage())
                    .readableBytes();

            final int newWriteBufferSize = writeBufferSize
                    .addAndGet(messageSize);

            final int highWaterMark = getConfig().getWriteBufferHighWaterMark();
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
            final MessageEvent e = super.poll();
            if (e != null) {
                final int messageSize = ((ChannelBuffer) e.getMessage())
                        .readableBytes();
                final int newWriteBufferSize = writeBufferSize
                        .addAndGet(-messageSize);

                final int lowWaterMark = getConfig()
                        .getWriteBufferLowWaterMark();

                if (newWriteBufferSize == 0 ||
                        newWriteBufferSize < lowWaterMark) {
                    if (newWriteBufferSize + messageSize >= lowWaterMark) {
                        highWaterMarkCounter.decrementAndGet();
                        if (!notifying.get()) {
                            notifying.set(Boolean.TRUE);
                            fireChannelInterestChanged(NioDatagramChannel.this);
                            notifying.set(Boolean.FALSE);
                        }
                    }
                }
            }
            return e;
        }
    }

    /**
     * WriteTask is a simple runnable performs writes by delegating the {@link NioUdpWorker}.
     *
     */
    private final class WriteTask implements Runnable {
        WriteTask() {
            super();
        }

        public void run() {
            writeTaskInTaskQueue.set(false);
            NioUdpWorker.write(NioDatagramChannel.this, false);
        }
    }
}
