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
package org.jboss.netty.handler.traffic;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.util.DefaultObjectSizeEstimator;
import org.jboss.netty.util.ObjectSizeEstimator;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Frederic Bregier (fredbregier@free.fr)
 * @version $Rev$, $Date$
 *
 * TrafficShapingHandler allows to limit the global bandwidth or per session
 * bandwidth, as traffic shaping.<br>
 * <br>
 *
 * An {@link ObjectSizeEstimator} can be passed at construction to specify what
 * is the size of the object to be read or write accordingly to the type of
 * object. If not specified, it will used the {@link DefaultObjectSizeEstimator} implementation.<br>
 * <br>
 *
 * TrafficShapingHandler depends on {@link TrafficCounterFactory} to create
 * or use the necessary {@link TrafficCounter} with the necessary options.
 * However, you can change the behavior of both global and channel
 * TrafficCounter if you like by getting them from this handler and changing
 * their status.
 *
 */
@ChannelPipelineCoverage("one")
public class TrafficShapingHandler extends SimpleChannelHandler {
    /**
     * Channel Monitor
     */
    private TrafficCounter channelTrafficCounter = null;

    /**
     * Global Monitor
     */
    private TrafficCounter globalTrafficCounter = null;

    /**
     * Factory if used
     */
    private TrafficCounterFactory factory = null;
    /**
     * ObjectSizeEstimator
     */
    private ObjectSizeEstimator objectSizeEstimator = null;
    /**
     * Constructor using default {@link ObjectSizeEstimator}
     *
     * @param factory
     *            the TrafficCounterFactory from which all Monitors will be
     *            created
     */
    public TrafficShapingHandler(TrafficCounterFactory factory) {
        super();
        this.factory = factory;
        this.globalTrafficCounter = this.factory
                .getGlobalTrafficCounter();
        this.channelTrafficCounter = null;
        this.objectSizeEstimator = new DefaultObjectSizeEstimator();
        // will be set when connected is called
    }
    /**
     * Constructor using the specified ObjectSizeEstimator
     *
     * @param factory
     *            the TrafficCounterFactory from which all Monitors will be
     *            created
     * @param objectSizeEstimator
     *            the {@link ObjectSizeEstimator} that will be used to compute 
     *            the size of the message
     */
    public TrafficShapingHandler(TrafficCounterFactory factory, ObjectSizeEstimator objectSizeEstimator) {
        super();
        this.factory = factory;
        this.globalTrafficCounter = this.factory
                .getGlobalTrafficCounter();
        this.channelTrafficCounter = null;
        this.objectSizeEstimator = objectSizeEstimator;
        // will be set when connected is called
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
        long size = this.objectSizeEstimator.estimateSize(arg1.getMessage());
        if (this.channelTrafficCounter != null) {
            this.channelTrafficCounter.bytesRecvFlowControl(arg0, size);
        }
        if (this.globalTrafficCounter != null) {
            this.globalTrafficCounter.bytesRecvFlowControl(arg0, size);
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
        long size = this.objectSizeEstimator.estimateSize(arg1.getMessage());
        if (this.channelTrafficCounter != null) {
            this.channelTrafficCounter.bytesWriteFlowControl(size);
        }
        if (this.globalTrafficCounter != null) {
            this.globalTrafficCounter.bytesWriteFlowControl(size);
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
        if (this.channelTrafficCounter != null) {
            this.channelTrafficCounter.stop();
            this.channelTrafficCounter = null;
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
        if ((this.channelTrafficCounter == null) && (this.factory != null)) {
            // A factory was used
            this.channelTrafficCounter = this.factory
                    .createChannelTrafficCounter(ctx.getChannel());
        }
        if (this.channelTrafficCounter != null) {
            this.channelTrafficCounter
                    .setMonitoredChannel(ctx.getChannel());
            this.channelTrafficCounter.start();
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
     * @return the current Channel TrafficCounter set from the factory (if
     *         channel is still connected) or null if this function was disabled
     *         in the Factory
     */
    public TrafficCounter getChannelTrafficCounter() {
        return this.channelTrafficCounter;
    }

    /**
     *
     * @return the Global TrafficCounter from the factory or null if this
     *         function was disabled in the Factory
     */
    public TrafficCounter getGlobalTrafficCounter() {
        return this.globalTrafficCounter;
    }

}
