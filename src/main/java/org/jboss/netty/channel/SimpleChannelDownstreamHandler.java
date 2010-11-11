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
 * <pre>public class MyChannelHandler extends {@link SimpleChannelDownstreamHandler} {
 *
 *     {@code @Override}
 *     public void handleDownstream({@link ChannelHandlerContext} ctx, {@link ChannelEvent} e) throws Exception {
 *
 *         // Log all channel state changes.
 *         if (e instanceof {@link MessageEvent}) {
 *             logger.info("Writing:: " + e);
 *         }
 *
 *         <strong>super.handleDownstream(ctx, e);</strong>
 *     }
 * }</pre>
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2122 $, $Date: 2010-02-02 11:00:04 +0900 (Tue, 02 Feb 2010) $
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
