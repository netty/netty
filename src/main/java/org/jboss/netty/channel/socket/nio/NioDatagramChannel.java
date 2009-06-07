/*
 * JBoss, Home of Professional Open Source Copyright 2009, Red Hat Middleware
 * LLC, and individual contributors by the @authors tag. See the copyright.txt
 * in the distribution for a full listing of individual contributors.
 * 
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 * 
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package org.jboss.netty.channel.socket.nio;

import static org.jboss.netty.channel.Channels.fireChannelInterestChanged;
import static org.jboss.netty.channel.Channels.fireChannelOpen;

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
import org.jboss.netty.channel.socket.DefaultDatagramChannelConfig;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.LinkedTransferQueue;
import org.jboss.netty.util.ThreadLocalBoolean;

/**
 * NioDatagramChannel provides a connection less NIO UDP channel for Netty.
 * <p/>
 * 
 * @author <a href="mailto:dbevenius@jboss.com">Daniel Bevenius</a>
 * 
 */
public class NioDatagramChannel extends AbstractChannel implements ServerChannel
{
    /**
     * Internal Netty logger.
     */
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NioDatagramChannel.class);
    /**
     * The {@link DatagramChannelConfig}.
     */
    private final DatagramChannelConfig config;
    /**
     * The {@link NioUdpWorker} for this NioDatagramChannnel.
     */
    final NioUdpWorker worker;
    /**
     * The {@link DatagramChannel} that this channel uses.
     */
    private final DatagramChannel datagramChannel;
    /**
     * 
     */
    volatile ChannelFuture connectFuture;
    /**
     * 
     */
    final Object interestOpsLock = new Object();
    /**
     * 
     */
    final Object writeLock = new Object();
    /**
     * 
     */
    final Runnable writeTask = new WriteTask();
    /**
     * Indicates if there is a {@link WriteTask} in the task queue.
     */
    final AtomicBoolean writeTaskInTaskQueue = new AtomicBoolean();
    /**
     * 
     */
    final Queue<MessageEvent> writeBufferQueue = new WriteBufferQueue();
    /**
     * Keeps track of the number of bytes that the {@link WriteBufferQueue} currently
     * contains.
     */
    final AtomicInteger writeBufferSize = new AtomicInteger();
    /**
     * 
     */
    final AtomicInteger highWaterMarkCounter = new AtomicInteger();
    /**
     * 
     */
    MessageEvent currentWriteEvent;
    /**
     * 
     */
    int currentWriteIndex;
    /**
     * 
     */
    volatile boolean inWriteNowLoop;

    /**
     * 
     * @param factory
     * @param pipeline
     * @param sink
     * @param worker
     */
    public NioDatagramChannel(final ChannelFactory factory, final ChannelPipeline pipeline, final ChannelSink sink, final NioUdpWorker worker)
    {
        super(null, factory, pipeline, sink);
        this.worker = worker;
        datagramChannel = openNonBlockingChannel();
        setSoTimeout(1000);
        config = new DefaultDatagramChannelConfig(datagramChannel.socket());
        
        fireChannelOpen(this);
    }

    private DatagramChannel openNonBlockingChannel()
    {
        try
        {
            final DatagramChannel channel = DatagramChannel.open();
            channel.configureBlocking(false);
            return channel;
        } 
        catch (final IOException e)
        {
            throw new ChannelException("Failed to open a DatagramChannel.", e);
        }
    }
    
    private void setSoTimeout(final int timeout)
    {
        try
        {
            datagramChannel.socket().setSoTimeout(timeout);
        } 
        catch (final IOException e)
        {
            try
            {
                datagramChannel.close();
            } 
            catch (final IOException e2)
            {
                logger.warn("Failed to close a partially DatagramSocket.", e2);
            }
            throw new ChannelException("Failed to set the DatagramSocket timeout.", e);
        }
    }

    public InetSocketAddress getLocalAddress()
    {
        return (InetSocketAddress) datagramChannel.socket().getLocalSocketAddress();
    }

    public SocketAddress getRemoteAddress()
    {
        return datagramChannel.socket().getRemoteSocketAddress();
    }

    public boolean isBound()
    {
        return isOpen() && datagramChannel.socket().isBound();
    }

    public boolean isConnected()
    {
        return datagramChannel.socket().isBound();
    }

    @Override
    protected boolean setClosed()
    {
        return super.setClosed();
    }

    @Override
    protected ChannelFuture getSucceededFuture()
    {
        return super.getSucceededFuture();
    }

    public DatagramChannelConfig getConfig()
    {
        return config;
    }
    
    DatagramChannel getDatagramChannel()
    {
        return datagramChannel;
    }

    int getRawInterestOps()
    {
        return super.getInterestOps();
    }

    void setRawInterestOpsNow(int interestOps)
    {
        super.setInterestOpsNow(interestOps);
    }
    
    /**
     * WriteBuffer is an extension of {@link LinkedTransferQueue} that adds 
     * support for highWaterMark checking of the write buffer size.
     * 
     * @author <a href="mailto:dbevenius@jboss.com">Daniel Bevenius</a>
     */
    private final class WriteBufferQueue extends LinkedTransferQueue<MessageEvent> 
    {
        private final ThreadLocalBoolean notifying = new ThreadLocalBoolean();

        /**
         * This method first delegates to {@link LinkedTransferQueue#offer(Object)} and
         * adds support for keeping track of the size of the this writebuffers queue. 
         */
        @Override
        public boolean offer(final MessageEvent e) 
        {
            final boolean success = super.offer(e);
            assert success;

            final int messageSize = ((ChannelBuffer) e.getMessage()).readableBytes();
            
            // Add the ChannelBuffers size to the writeBuffersSize 
            final int newWriteBufferSize = writeBufferSize.addAndGet(messageSize);
            
            final int highWaterMark = getConfig().getWriteBufferHighWaterMark();
            // Check if the newly calculated buffersize exceeds the highWaterMark limit.
            if (newWriteBufferSize >= highWaterMark) 
            {
                // Check to see if the messages size we are adding is what will cause the highWaterMark to be breached.
                if (newWriteBufferSize - messageSize < highWaterMark) 
                {
                    // Increment the highWaterMarkCounter which track of the fact that the count
                    // has been reached.
                    highWaterMarkCounter.incrementAndGet();
                    
                    if (!notifying.get()) 
                    {
                        notifying.set(Boolean.TRUE);
                        fireChannelInterestChanged(NioDatagramChannel.this);
                        notifying.set(Boolean.FALSE);
                    }
                }
            }
            
            return true;
        }

        /**
         * This method first delegates to {@link LinkedTransferQueue#poll(Object)} and
         * adds support for keeping track of the size of the this writebuffers queue. 
         */
        @Override
        public MessageEvent poll() 
        {
            final MessageEvent e = super.poll();
            if (e != null) 
            {
                final int messageSize = ((ChannelBuffer) e.getMessage()).readableBytes();
                // Subract the ChannelBuffers size from the writeBuffersSize 
                final int newWriteBufferSize = writeBufferSize.addAndGet(-messageSize);
                
                final int lowWaterMark = getConfig().getWriteBufferLowWaterMark();

                // Check if the newly calculated buffersize exceeds the lowhWaterMark limit.
                if (newWriteBufferSize == 0 || newWriteBufferSize < lowWaterMark) 
                {
                    if (newWriteBufferSize + messageSize >= lowWaterMark) 
                    {
                        highWaterMarkCounter.decrementAndGet();
                        if (!notifying.get()) 
                        {
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
     * @author <a href="mailto:dbevenius@jboss.com">Daniel Bevenius</a>
     *
     */
    private final class WriteTask implements Runnable 
    {
        public void run() 
        {
            writeTaskInTaskQueue.set(false);
            NioUdpWorker.write(NioDatagramChannel.this, false);
        }
    }
}
