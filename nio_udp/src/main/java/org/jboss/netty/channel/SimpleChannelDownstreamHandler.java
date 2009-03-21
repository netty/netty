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


/**
 * A {@link ChannelDownstreamHandler} which provides an individual handler
 * method for each event type.  This handler down-casts the received downstream
 * event into more meaningful sub-type event and calls an appropriate handler
 * method with the down-cast event.  The names of the methods starts with the
 * name of the operation and ends with {@code "Requested"}
 * (e.g. {@link #writeRequested(ChannelHandlerContext, MessageEvent) writeRequested}.)
 * <p>
 * Please use {@link SimpleChannelHandler} if you need to implement both
 * {@link ChannelUpstreamHandler} and {@link ChannelDownstreamHandler}.
 *
 * <h3>Overriding the {@link #handleDownstream(ChannelHandlerContext, ChannelEvent) handleDownstream} method</h3>
 * <p>
 * You can override the {@link #handleDownstream(ChannelHandlerContext, ChannelEvent) handleDownstream}
 * method just like overriding an ordinary Java method.  Please make sure to
 * call {@code super.handleDownstream()} so that other handler methods are
 * invoked properly:
 * </p>
 * <pre>public class MyChannelHandler extends SimpleChannelDownstreamHandler {
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
public class SimpleChannelDownstreamHandler implements ChannelDownstreamHandler {

    /**
     * Creates a new instance.
     */
    public SimpleChannelDownstreamHandler() {
        super();
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
