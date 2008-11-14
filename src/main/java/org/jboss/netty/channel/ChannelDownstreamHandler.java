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
 * Handles or intercepts a downstream {@link ChannelEvent}, and sends a
 * {@link ChannelEvent} to the previous or next handler in a
 * {@link ChannelPipeline}.
 *
 * <h3>Downstream events</h3>
 * <p>
 * A downstream event is an event which is supposed to be processed from the
 * last handler to the first handler in the {@link ChannelPipeline}.
 * For example, all I/O requests made by a user application are downstream
 * events.
 * <p>
 * The most common use case of this interface is to intercept an I/O request
 * such as {@link Channel#write(Object)} and {@link Channel#close()}.  The
 * received {@link ChannelEvent} object is interpreted as described in the
 * following table:
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Event name</th><th>Event type and condition</th><th>Meaning</th>
 * </tr>
 * <tr>
 * <td>{@code "write"}</td>
 * <td>{@link MessageEvent}</td><td>Send a message to the {@link Channel}.</td>
 * </tr>
 * <tr>
 * <td>{@code "bind"}</td>
 * <td>{@link ChannelStateEvent}<br/>(state = {@link ChannelState#BOUND BOUND}, value = {@link SocketAddress})</td>
 * <td>Bind the {@link Channel} to the specified local address.</td>
 * </tr>
 * <tr>
 * <td>{@code "unbind"}</td>
 * <td>{@link ChannelStateEvent}<br/>(state = {@link ChannelState#BOUND BOUND}, value = {@code null})</td>
 * <td>Unbind the {@link Channel} from the current local address.</td>
 * </tr>
 * <tr>
 * <td>{@code "connect"}</td>
 * <td>{@link ChannelStateEvent}<br/>(state = {@link ChannelState#CONNECTED CONNECTED}, value = {@link SocketAddress})</td>
 * <td>Connect the {@link Channel} to the specified remote address.</td>
 * </tr>
 * <tr>
 * <td>{@code "disconnect"}</td>
 * <td>{@link ChannelStateEvent}<br/>(state = {@link ChannelState#CONNECTED CONNECTED}, value = {@code null})</td>
 * <td>Disconnect the {@link Channel} from the current remote address.</td>
 * </tr>
 * <tr>
 * <td>{@code "close"}</td>
 * <td>{@link ChannelStateEvent}<br/>(state = {@link ChannelState#OPEN OPEN}, value = {@code false})</td>
 * <td>Close the {@link Channel}.</td>
 * </tr>
 * </table>
 * <p>
 * Other event types and conditions which were not addressed here will be
 * ignored and discarded.  Please note that there's no {@code "open"} in the
 * table.  It is because a {@link Channel} is always open when it is created
 * by a {@link ChannelFactory}.
 *
 * <h4>Additional resources worth reading</h4>
 * <p>
 * You might want to refer to {@link ChannelUpstreamHandler} to see how a
 * {@link ChannelEvent} is interpreted when going upstream.  Also, please refer
 * to the {@link ChannelEvent} documentation to find out what an upstream event
 * and a downstream event are and what fundamental differences they have, if
 * you didn't read yet.
 *
 * <h3>Firing an event to the previous or next handler</h3>
 * <p>
 * You can forward the received event downstream or upstream.  In most cases,
 * {@link ChannelDownstreamHandler} will pass the event to the previous
 * handler (downstream) although it is absolutely normal to pass the event
 * to the next handler (upstream):
 *
 * <pre>
 * // Sending the event forward (downstream)
 * void handleDownstream({@link ChannelHandlerContext} ctx, {@link ChannelEvent} e) throws Exception {
 *     ...
 *     ctx.sendDownstream(e);
 *     ...
 * }
 *
 * // Sending the event backward (upstream)
 * void handleDownstream({@link ChannelHandlerContext} ctx, {@link ChannelEvent} e) throws Exception {
 *     ...
 *     ctx.sendUpstream(new DefaultChannelStateEvent(...));
 *     ...
 * }
 * </pre>
 *
 * <h4>Using the helper class to send an event</h4>
 * <p>
 * You will also find various helper methods in {@link Channels} to be useful
 * to generate and send an artificial or manipulated event.
 *
 * <h3>Thread safety</h3>
 * <p>
 * {@link #handleDownstream(ChannelHandlerContext, ChannelEvent) handleDownstream}
 * may be invoked by more than one thread simultaneously.  If the handler
 * accesses a shared resource or stores stateful information, you might need
 * proper synchronization in the handler implementation.
 * <p>
 * Also, please refer to the {@link ChannelPipelineCoverage} annotation to
 * understand the relationship between a handler and its stateful properties.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 */
public interface ChannelDownstreamHandler extends ChannelHandler {

    /**
     * Handles the specified downstream event.
     *
     * @param ctx  the context object for this handler
     * @param e    the downstream event to process or intercept
     */
    void handleDownstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception;
}
