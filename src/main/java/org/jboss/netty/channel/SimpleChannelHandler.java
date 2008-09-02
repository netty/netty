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
import org.jboss.netty.channel.socket.ServerSocketChannel;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;


/**
 * A generic {@link ChannelUpstreamHandler} implementation that satisfies most
 * use cases.  This handler down-casts the received upstream event into more
 * meaningful sub-type event and calls an appropriate handler method with the
 * down-casted event.  The following table shows the provided handler methods:
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Handler method</th><th>Event type and condition</th><th>Invoked when ...</th>
 * </tr>
 * <tr>
 * <td>{@link #messageReceived(ChannelHandlerContext, MessageEvent) messageReceived}</td>
 * <td>{@link MessageEvent}</td>
 * <td>a message object (e.g. {@link ChannelBuffer}) was received from a remote peer</td>
 * </tr>
 * <tr>
 * <td>{@link #exceptionCaught(ChannelHandlerContext, ExceptionEvent) exceptionCaught}</td>
 * <td>{@link ExceptionEvent}</td>
 * <td>an exception was raised by an I/O thread or a {@link ChannelHandler}</td>
 * </tr>
 * <tr>
 * <td>{@link #channelOpen(ChannelHandlerContext, ChannelStateEvent) channelOpen}</td>
 * <td>{@link ChannelStateEvent}<br/>(state={@link ChannelState#OPEN}, value={@code true})</td>
 * <td>a {@link Channel} is open, but not bound nor connected</td>
 * </tr>
 * <tr>
 * <td>{@link #channelBound(ChannelHandlerContext, ChannelStateEvent) channelBound}</td>
 * <td>{@link ChannelStateEvent}<br/>(state={@link ChannelState#BOUND}, value={@link SocketAddress})</td>
 * <td>a {@link Channel} is open and bound to a local address, but not connected</td>
 * </tr>
 * <tr>
 * <td>{@link #channelConnected(ChannelHandlerContext, ChannelStateEvent) channelConnected}</td>
 * <td>{@link ChannelStateEvent}<br/>(state={@link ChannelState#CONNECTED}, value={@link SocketAddress})</td>
 * <td>a {@link Channel} is open, bound to a local address, and connected to a remote address</td>
 * </tr>
 * <tr>
 * <td>{@link #channelInterestChanged(ChannelHandlerContext, ChannelStateEvent) channelInterestedChanged}</td>
 * <td>{@link ChannelStateEvent}<br/>(state={@link ChannelState#INTEREST_OPS}, value=an integer)</td>
 * <td>a {@link Channel}'s {@link Channel#getInterestOps() interestOps} was changed</td>
 * </tr>
 * <tr>
 * <td>{@link #channelDisconnected(ChannelHandlerContext, ChannelStateEvent) channelDisconnected}</td>
 * <td>{@link ChannelStateEvent}<br/>(state={@link ChannelState#CONNECTED}, value={@code null})</td>
 * <td>a {@link Channel} was disconnected from its remote peer</td>
 * </tr>
 * <tr>
 * <td>{@link #channelUnbound(ChannelHandlerContext, ChannelStateEvent) channelUnbound}</td>
 * <td>{@link ChannelStateEvent}<br/>(state={@link ChannelState#BOUND}, value={@code null})</td>
 * <td>a {@link Channel} was unbound from the current local address</td>
 * </tr>
 * <tr>
 * <td>{@link #channelClosed(ChannelHandlerContext, ChannelStateEvent) channelClosed}</td>
 * <td>{@link ChannelStateEvent}<br/>(state={@link ChannelState#OPEN}, value={@code false})</td>
 * <td>a {@link Channel} was closed and all its related resources were released</td>
 * </tr>
 * </table>
 *
 * <p>
 * These two handler methods are used only for a parent channel which can have
 * a child channel (e.g. {@link ServerSocketChannel}).
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Handler method</th><th>Event type and condition</th><th>Invoked when ...</th>
 * </tr>
 * <tr>
 * <td>{@link #childChannelOpen(ChannelHandlerContext, ChildChannelStateEvent) childChannelOpen}</td>
 * <td>{@link ChildChannelStateEvent}<br/>({@code childChannel.isOpen() = true})</td>
 * <td>a child {@link Channel} was open (e.g. a server channel accepted a connection.)</td>
 * </tr>
 * <tr>
 * <td>{@link #childChannelClosed(ChannelHandlerContext, ChildChannelStateEvent) childChannelClosed}</td>
 * <td>{@link ChildChannelStateEvent}<br/>({@code childChannel.isOpen() = false})</td>
 * <td>a child {@link Channel} was closed (e.g. the accepted connection was closed.)</td>
 * </tr>
 * </table>
 * <p>
 * You also might want to refer to {@link ChannelDownstreamHandler} to see
 * how a {@link ChannelEvent} is interpreted when going downstream.
 *
 * <h3>Overriding {@link #handleUpstream(ChannelHandlerContext, ChannelEvent) handleUpstream} method</h3>
 * <p>
 * You can override the {@link #handleUpstream(ChannelHandlerContext, ChannelEvent) handleUpstream}
 * method just like you can do with other {@link ChannelUpstreamHandler}s.
 * However, please make sure that you call {@code super.handleUpstream()} so
 * that other handler methods are invoked properly:
 * </p>
 * <pre>public class MyChannelHandler extends SimpleChannelHandler {
 *
 *     public void handleUpstream(
 *             ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
 *
 *         // Log all channel state changes.
 *         if (e instanceof ChannelStateEvent) {
 *             logger.info("Channel state changed: " + e);
 *         }
 *
 *         <strong>super.handleUpstream(ctx, e);</strong>
 *     }
 * }</pre>
 *
 * <h3>Thread safety</h3>
 * <p>
 * The same rule with {@link ChannelUpstreamHandler} is applied.  Please refer
 * to the '<a href="ChannelUpstreamHandler.html#thread_safety">thread safety</a>'
 * section.
 *
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 */
public class SimpleChannelHandler implements ChannelUpstreamHandler {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(SimpleChannelHandler.class.getName());

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
            }
        } else if (e instanceof ExceptionEvent) {
            exceptionCaught(ctx, (ExceptionEvent) e);
        } else {
            ctx.sendUpstream(e);
        }
    }

    /**
     * Invoked when a message object (e.g. {@link ChannelBuffer}) was received
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
}
