/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.netty.handler.trafficshaping;

import java.io.InvalidClassException;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Frederic Bregier (fredbregier@free.fr)
 * @version $Rev$, $Date$
 *
 * TrafficShapingHandler allows to limit the global bandwidth or per session
 * bandwidth, as traffic shaping.<br>
 * <br>
 *
 * The method getMessageSize(MessageEvent) has to be implemented to specify what
 * is the size of the object to be read or write accordingly to the type of
 * object. In simple case, it can be as simple as a call to
 * getChannelBufferMessageSize(MessageEvent).<br>
 * <br>
 *
 * TrafficShapingHandler depends on {@link PerformanceCounterFactory} to create
 * or use the necessary {@link PerformanceCounter} with the necessary options.
 * However, you can change the behavior of both global and channel
 * PerformanceCounter if you like by getting them from this handler and changing
 * their status.
 *
 */
@ChannelPipelineCoverage("one")
public abstract class TrafficShapingHandler extends SimpleChannelHandler {
    /**
     * Channel Monitor
     */
    private PerformanceCounter channelPerformanceCounter = null;

    /**
     * Global Monitor
     */
    private PerformanceCounter globalPerformanceCounter = null;

    /**
     * Factory if used
     */
    private PerformanceCounterFactory factory = null;

    /**
     * Constructor
     *
     * @param factory
     *            the PerformanceCounterFactory from which all Monitors will be
     *            created
     */
    public TrafficShapingHandler(PerformanceCounterFactory factory) {
        super();
        this.factory = factory;
        this.globalPerformanceCounter = this.factory
                .getGlobalPerformanceCounter();
        this.channelPerformanceCounter = null;
        // will be set when connected is called
    }

    /**
     * This method has to be implemented. It returns the size in bytes of the
     * message to be read or written.
     *
     * @param arg1
     *            the MessageEvent to be read or written
     * @return the size in bytes of the given MessageEvent
     * @exception Exception
     *                An exception can be thrown if the object is not of the
     *                expected type
     */
    protected abstract long getMessageSize(MessageEvent arg1) throws Exception;

    /**
     * Example of function (which can be used) for the ChannelBuffer
     *
     * @param arg1
     * @return the size in bytes of the given MessageEvent
     * @throws Exception
     */
    protected long getChannelBufferMessageSize(MessageEvent arg1)
            throws Exception {
        Object o = arg1.getMessage();
        if (!(o instanceof ChannelBuffer)) {
            // Type unimplemented
            throw new InvalidClassException("Wrong object received in " +
                    this.getClass().getName() + " codec " +
                    o.getClass().getName());
        }
        ChannelBuffer dataBlock = (ChannelBuffer) o;
        return dataBlock.readableBytes();
    }

    /*
     * (non-Javadoc)
     *
     * @see org.jboss.netty.channel.SimpleChannelHandler#messageReceived(org.jboss.netty.channel.ChannelHandlerContext,
     *      org.jboss.netty.channel.MessageEvent)
     */
    @Override
    public void messageReceived(ChannelHandlerContext arg0, MessageEvent arg1)
            throws Exception {
        long size = getMessageSize(arg1);
        if (this.channelPerformanceCounter != null) {
            this.channelPerformanceCounter.setReceivedBytes(arg0, size);
        }
        if (this.globalPerformanceCounter != null) {
            this.globalPerformanceCounter.setReceivedBytes(arg0, size);
        }
        // The message is then just passed to the next Codec
        super.messageReceived(arg0, arg1);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.jboss.netty.channel.SimpleChannelHandler#writeRequested(org.jboss.netty.channel.ChannelHandlerContext,
     *      org.jboss.netty.channel.MessageEvent)
     */
    @Override
    public void writeRequested(ChannelHandlerContext arg0, MessageEvent arg1)
            throws Exception {
        long size = getMessageSize(arg1);
        if (this.channelPerformanceCounter != null) {
            this.channelPerformanceCounter.setToWriteBytes(size);
        }
        if (this.globalPerformanceCounter != null) {
            this.globalPerformanceCounter.setToWriteBytes(size);
        }
        // The message is then just passed to the next Codec
        super.writeRequested(arg0, arg1);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.jboss.netty.channel.SimpleChannelHandler#channelClosed(org.jboss.netty.channel.ChannelHandlerContext,
     *      org.jboss.netty.channel.ChannelStateEvent)
     */
    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        if (this.channelPerformanceCounter != null) {
            this.channelPerformanceCounter.stopMonitoring();
            this.channelPerformanceCounter = null;
        }
        super.channelClosed(ctx, e);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.jboss.netty.channel.SimpleChannelHandler#channelConnected(org.jboss.netty.channel.ChannelHandlerContext,
     *      org.jboss.netty.channel.ChannelStateEvent)
     */
    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        // readSuspended = true;
        ctx.setAttachment(Boolean.TRUE);
        ctx.getChannel().setReadable(false);
        if ((this.channelPerformanceCounter == null) && (this.factory != null)) {
            // A factory was used
            this.channelPerformanceCounter = this.factory
                    .createChannelPerformanceCounter(ctx.getChannel());
        }
        if (this.channelPerformanceCounter != null) {
            this.channelPerformanceCounter
                    .setMonitoredChannel(ctx.getChannel());
            this.channelPerformanceCounter.startMonitoring();
        }
        super.channelConnected(ctx, e);
        // readSuspended = false;
        ctx.setAttachment(null);
        ctx.getChannel().setReadable(true);
    }

    @Override
    public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent e)
            throws Exception {
        if (e instanceof ChannelStateEvent) {
            ChannelStateEvent cse = (ChannelStateEvent) e;
            if (cse.getState() == ChannelState.INTEREST_OPS &&
                    (((Integer) cse.getValue()).intValue() & Channel.OP_READ) != 0) {

                // setReadable(true) requested
                boolean readSuspended = ctx.getAttachment() != null;
                if (readSuspended) {
                    // Drop the request silently if PerformanceCounter has
                    // set the flag.
                    e.getFuture().setSuccess();
                    return;
                }
            }
        }
        super.handleDownstream(ctx, e);
    }

    /**
     *
     * @return the current ChannelPerformanceCounter set from the factory (if
     *         channel is still connected) or null if this function was disabled
     *         in the Factory
     */
    public PerformanceCounter getChannelPerformanceCounter() {
        return this.channelPerformanceCounter;
    }

    /**
     *
     * @return the GlobalPerformanceCounter from the factory or null if this
     *         function was disabled in the Factory
     */
    public PerformanceCounter getGlobalPerformanceCounter() {
        return this.globalPerformanceCounter;
    }

}
