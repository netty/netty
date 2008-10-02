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
package org.jboss.netty.channel;

import java.net.SocketAddress;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;


/**
 * A {@link ChannelHandler} which provides an individual handler method
 * for each event type.  This handler down-casts the received upstream or
 * or downstream event into more meaningful sub-type event and calls an
 * appropriate handler method with the down-cast event.  For an upstream
 * event, the names of the methods are identical to the upstream event names,
 * as introduced in the {@link ChannelUpstreamHandler} documentation.  For a
 * downstream event, the names of the methods starts with the name of the
 * operation and ends with {@code "Requested"}
 * (e.g. {@link #writeRequested(ChannelHandlerContext, MessageEvent) writeRequested}.)
 *
 * <h3>Overriding the {@link #handleUpstream(ChannelHandlerContext, ChannelEvent) handleUpstream}
 *     and {@link #handleDownstream(ChannelHandlerContext, ChannelEvent) handleDownstream} method</h3>
 * <p>
 * You can override the {@link #handleUpstream(ChannelHandlerContext, ChannelEvent) handleUpstream}
 * and {@link #handleDownstream(ChannelHandlerContext, ChannelEvent) handleDownstream}
 * method just like overriding an ordinary Java method.  Please make sure to
 * call {@code super.handleUpstream()} or {@code super.handleDownstream()} so
 * that other handler methods are invoked properly:
 * </p>
 * <pre>public class MyChannelHandler extends SimpleChannelHandler {
 *
 *     public void handleUpstream({@link ChannelHandlerContext} ctx, {@link ChannelEvent} e) throws Exception {
 *
 *         // Log all channel state changes.
 *         if (e instanceof ChannelStateEvent) {
 *             logger.info("Channel state changed: " + e);
 *         }
 *
 *         <strong>super.handleUpstream(ctx, e);</strong>
 *     }
 *
 *     public void handleDownstream({@link ChannelHandlerContext} ctx, {@link ChannelEvent} e) throws Exception {
 *
 *         // Log all channel state changes.
 *         if (e instanceof MessageEvent) {
 *             logger.info("Writing:: " + e);
 *         }
 *
 *         <strong>super.handleDownstream(ctx, e);</strong>
 *     }
 * }</pre>
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 */
public class SimpleChannelHandler implements ChannelUpstreamHandler, ChannelDownstreamHandler {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(SimpleChannelHandler.class.getName());

    /**
     * Creates a new instance.
     */
    public SimpleChannelHandler() {
        super();
    }

    /**
     * {@inheritDoc}  Down-casts the received upstream event into more
     * meaningful sub-type event and calls an appropriate handler method with
     * the down-casted event.
     */
    public void handleUpstream(
            ChannelHandlerContext ctx, ChannelEvent e) throws Exception {

        if (e instanceof MessageEvent) {
            messageReceived(ctx, (MessageEvent) e);
        } else if (e instanceof ChildChannelStateEvent) {
            ChildChannelStateEvent evt = (ChildChannelStateEvent) e;
            if (evt.getChildChannel().isOpen()) {
                childChannelOpen(ctx, evt);
            } else {
                childChannelClosed(ctx, evt);
            }
        } else if (e instanceof ChannelStateEvent) {
            ChannelStateEvent evt = (ChannelStateEvent) e;
            switch (evt.getState()) {
            case OPEN:
                if (Boolean.TRUE.equals(evt.getValue())) {
                    channelOpen(ctx, evt);
                } else {
                    channelClosed(ctx, evt);
                }
                break;
            case BOUND:
                if (evt.getValue() != null) {
                    channelBound(ctx, evt);
                } else {
                    channelUnbound(ctx, evt);
                }
                break;
            case CONNECTED:
                if (evt.getValue() != null) {
                    channelConnected(ctx, evt);
                } else {
                    channelDisconnected(ctx, evt);
                }
                break;
            case INTEREST_OPS:
                channelInterestChanged(ctx, evt);
                break;
            default:
                ctx.sendDownstream(e);
            }
        } else if (e instanceof ExceptionEvent) {
            exceptionCaught(ctx, (ExceptionEvent) e);
        } else {
            ctx.sendUpstream(e);
        }
    }

    /**
     * Invoked when a message object (e.g: {@link ChannelBuffer}) was received
     * from a remote peer.
     */
    public void messageReceived(
            ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    /**
     * Invoked when an exception was raised by an I/O thread or a
     * {@link ChannelHandler}.
     */
    public void exceptionCaught(
            ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        if (this == ctx.getPipeline().getLast()) {
            logger.warn(
                    "EXCEPTION, please implement " + getClass().getName() +
                    ".exceptionCaught() for proper handling.", e.getCause());
        }
        ctx.sendUpstream(e);
    }

    /**
     * Invoked when a {@link Channel} is open, but not bound nor connected.
     */
    public void channelOpen(
            ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    /**
     * Invoked when a {@link Channel} is open and bound to a local address,
     * but not connected.
     */
    public void channelBound(
            ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    /**
     * Invoked when a {@link Channel} is open, bound to a local address, and
     * connected to a remote address.
     */
    public void channelConnected(
            ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    /**
     * Invoked when a {@link Channel}'s {@link Channel#getInterestOps() interestOps}
     * was changed.
     */
    public void channelInterestChanged(
            ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    /**
     * Invoked when a {@link Channel} was disconnected from its remote peer.
     */
    public void channelDisconnected(
            ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    /**
     * Invoked when a {@link Channel} was unbound from the current local address.
     */
    public void channelUnbound(
            ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    /**
     * Invoked when a {@link Channel} was closed and all its related resources
     * were released.
     */
    public void channelClosed(
            ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    /**
     * Invoked when a child {@link Channel} was open.
     * (e.g. a server channel accepted a connection)
     */
    public void childChannelOpen(
            ChannelHandlerContext ctx, ChildChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    /**
     * Invoked when a child {@link Channel} was closed.
     * (e.g. the accepted connection was closed)
     */
    public void childChannelClosed(
            ChannelHandlerContext ctx, ChildChannelStateEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    /**
     * {@inheritDoc}  Down-casts the received downstream event into more
     * meaningful sub-type event and calls an appropriate handler method with
     * the down-casted event.
     */
    public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent e)
            throws Exception {

        if (e instanceof MessageEvent) {
            writeRequested(ctx, (MessageEvent) e);
        } else if (e instanceof ChannelStateEvent) {
            ChannelStateEvent evt = (ChannelStateEvent) e;
            switch (evt.getState()) {
            case OPEN:
                if (!Boolean.TRUE.equals(evt.getValue())) {
                    closeRequested(ctx, evt);
                }
                break;
            case BOUND:
                if (evt.getValue() != null) {
                    bindRequested(ctx, evt);
                } else {
                    unbindRequested(ctx, evt);
                }
                break;
            case CONNECTED:
                if (evt.getValue() != null) {
                    connectRequested(ctx, evt);
                } else {
                    disconnectRequested(ctx, evt);
                }
                break;
            case INTEREST_OPS:
                setInterestOpsRequested(ctx, evt);
                break;
            default:
                ctx.sendDownstream(e);
            }
        } else {
            ctx.sendDownstream(e);
        }
    }

    /**
     * Invoked when {@link Channel#write(Object)} is called.
     */
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        ctx.sendDownstream(e);
    }

    /**
     * Invoked when {@link Channel#bind(SocketAddress)} was called.
     */
    public void bindRequested(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        ctx.sendDownstream(e);

    }

    /**
     * Invoked when {@link Channel#connect(SocketAddress)} was called.
     */
    public void connectRequested(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        ctx.sendDownstream(e);

    }

    /**
     * Invoked when {@link Channel#setInterestOps(int)} was called.
     */
    public void setInterestOpsRequested(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        ctx.sendDownstream(e);
    }

    /**
     * Invoked when {@link Channel#disconnect()} was called.
     */
    public void disconnectRequested(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        ctx.sendDownstream(e);

    }

    /**
     * Invoked when {@link Channel#unbind()} was called.
     */
    public void unbindRequested(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        ctx.sendDownstream(e);

    }

    /**
     * Invoked when {@link Channel#close()} was called.
     */
    public void closeRequested(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        ctx.sendDownstream(e);
    }
}
