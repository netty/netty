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

import static org.jboss.netty.channel.Channels.fireChannelOpen;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;

import org.jboss.netty.channel.AbstractChannel;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.ServerChannel;
import org.jboss.netty.channel.socket.DatagramChannelConfig;
import org.jboss.netty.channel.socket.DefaultDatagramChannelConfig;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

/**
 * NioDatagramChannel provides a connection less NIO UDP channel for Netty.
 * <p/>
 * 
 * @author <a href="mailto:dbevenius@jboss.com">Daniel Bevenius</a>
 * 
 */
public class NioDatagramChannel extends AbstractChannel implements ServerChannel
{
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NioDatagramChannel.class);
    
    final NioUdpWorker worker;
    volatile ChannelFuture connectFuture;
    final Object interestOpsLock = new Object();
    
    private final DatagramChannel datagramChannel;
    private final DatagramChannelConfig config;

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
            DatagramChannel channel = DatagramChannel.open();
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
}
