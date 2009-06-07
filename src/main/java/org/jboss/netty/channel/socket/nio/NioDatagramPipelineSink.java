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

import static org.jboss.netty.channel.Channels.fireChannelBound;
import static org.jboss.netty.channel.Channels.fireChannelClosed;
import static org.jboss.netty.channel.Channels.fireChannelUnbound;
import static org.jboss.netty.channel.Channels.fireExceptionCaught;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.netty.channel.AbstractChannelSink;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

/**
 * NioDatagramPipelineSink receives downstream events from a ChannelPipeline.
 * <p/>
 * A {@link NioDatagramPipelineSink} contains an array of {@link NioUdpWorker}s
 * 
 * @author <a href="mailto:dbevenius@jboss.com">Daniel Bevenius</a>
 */
public class NioDatagramPipelineSink extends AbstractChannelSink
{
    /**
     * Internal Netty logger.
     */
    private final InternalLogger logger = InternalLoggerFactory.getInstance(NioDatagramPipelineSink.class);

    private static final AtomicInteger nextId = new AtomicInteger();
    private final int id = nextId.incrementAndGet();

    private final NioUdpWorker[] workers;
    private final AtomicInteger workerIndex = new AtomicInteger();
    
    /**
     * Creates a new {@link NioDatagramPipelineSink} with a the number of {@link NioUdpWorker}s specified in workerCount. 
     * The {@link NioUdpWorker}s take care of reading and writing for the {@link NioDatagramChannel}.
     * 
     * @param workerExecutor
     * @param workerCount The number of UdpWorkers for this sink.
     */
    NioDatagramPipelineSink(final Executor workerExecutor, final int workerCount)
    {
        workers = new NioUdpWorker[workerCount];
        for (int i = 0; i < workers.length; i++)
        {
            workers[i] = new NioUdpWorker(id, i + 1, workerExecutor);
        }
    }

    /**
     * Handle downstream event.
     * 
     * @param pipeline The channelpiple line that passed down the downstream event.
     * @param event The downstream event.
     */
    public void eventSunk(final ChannelPipeline pipeline, final ChannelEvent e) throws Exception
    {
        final NioDatagramChannel channel = (NioDatagramChannel) e.getChannel();
        final ChannelFuture future = e.getFuture();
        if (e instanceof ChannelStateEvent)
        {
            final ChannelStateEvent stateEvent = (ChannelStateEvent) e;
            final ChannelState state = stateEvent.getState();
            final Object value = stateEvent.getValue();
            switch (state)
            {
                case OPEN:
                    if (Boolean.FALSE.equals(value))
                    {
                        NioUdpWorker.close(channel, future);
                    }
                    break;
                case BOUND:
                    if (value != null)
                    {
                        bind(channel, future, (InetSocketAddress) value);
                    } 
                    else
                    {
                        NioUdpWorker.close(channel, future);
                    }
                    break;
                case INTEREST_OPS:
                    NioUdpWorker.setInterestOps(channel, future, ((Integer) value).intValue());
                    break;
            }
        } 
        else if (e instanceof MessageEvent)
        {
            final MessageEvent event = (MessageEvent) e;
            final boolean offered = channel.writeBufferQueue.offer(event);
            assert offered;
            NioUdpWorker.write(channel, true);
        }
    }

    private void close(NioDatagramChannel channel, ChannelFuture future)
    {
        try
        {
            channel.getDatagramChannel().socket().close();
            future.setSuccess();
            if (channel.setClosed())
            {
                if (channel.isBound())
                {
                    fireChannelUnbound(channel);
                }
                fireChannelClosed(channel);
            }
        } 
        catch (final Throwable t)
        {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    /**
     * Will bind the DatagramSocket to the passed-in address.
     * Every call bind will spawn a new thread using the that basically inturn 
     */
    private void bind(final NioDatagramChannel channel, final ChannelFuture future, final InetSocketAddress address)
    {
        boolean bound = false;
        boolean started = false;
        try
        {
            // First bind the DatagramSocket the specified port.
            channel.getDatagramChannel().socket().bind(address);
            bound = true;

            future.setSuccess();
            fireChannelBound(channel, address);
            
            nextWorker().register(channel, null);
            started = true;
        } 
        catch (final Throwable t)
        {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        } 
        finally
        {
            if (!started && bound)
            {
                close(channel, future);
            }
        }
    }

    NioUdpWorker nextWorker()
    {
        return workers[Math.abs(workerIndex.getAndIncrement() % workers.length)];
    }

    /**
     * The connection sematics of a NioDatagramPipelineSink are different for datagram sockets than they are for stream
     * sockets. Placing a DatagramChannel into a connected state causes datagrams to be ignored from any source
     * address other than the one to which the channel is connected. Unwanted packets will be dropped.
     * Not sure that this makes sense for a server side component. 
     * 
     * @param channel The UdpChannel to connect from.
     * @param future
     * @param remoteAddress The remote address to connect to.
     */
    @SuppressWarnings("unused")
    private void connect(final NioDatagramChannel channel, ChannelFuture future, SocketAddress remoteAddress)
    {
        try
        {
            try
            {
                channel.getDatagramChannel().socket().connect(remoteAddress);
            } 
            catch (final IOException e)
            {
                future.addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                channel.connectFuture = future;
            }
        } 
        catch (final Throwable t)
        {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

}
