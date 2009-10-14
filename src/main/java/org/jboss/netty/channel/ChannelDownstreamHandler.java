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
 * Handles or intercepts a downstream {@link ChannelEvent}, and sends a
 * {@link ChannelEvent} to the next handler in a {@link ChannelPipeline}.
 *
 * <h3>Downstream events</h3>
 * <p>
 * A downstream event is an event which is supposed to be processed by
 * a series of downstream handlers in the {@link ChannelPipeline}.  It is often
 * an I/O request made by a user application.
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
 * to the {@link ChannelEvent} and {@link ChannelPipeline} documentation to find
 * out what an upstream event and a downstream event are, what fundamental
 * differences they have, and how they flow in a pipeline.
 *
 * <h3>Firing an event to the next handler</h3>
 * <p>
 * You can forward the received event downstream or upstream.  In most cases,
 * {@link ChannelDownstreamHandler} will send the event downstream
 * (i.e. outbound) although it is legal to send the event upstream (i.e. inbound):
 *
 * <pre>
 * // Sending the event downstream (outbound)
 * void handleDownstream({@link ChannelHandlerContext} ctx, {@link ChannelEvent} e) throws Exception {
 *     ...
 *     ctx.sendDownstream(e);
 *     ...
 * }
 *
 * // Sending the event upstream (inbound)
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
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.exclude ^org\.jboss\.netty\.handler\..*$
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
