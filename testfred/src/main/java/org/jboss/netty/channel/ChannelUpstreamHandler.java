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
import java.util.concurrent.Executor;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.socket.ServerSocketChannel;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;


/**
 * Handles or intercepts an upstream {@link ChannelEvent}, and sends a
 * {@link ChannelEvent} to the next or previous handler in a
 * {@link ChannelPipeline}.
 *
 * <h3>Upstream events</h3>
 * <p>
 * An upstream event is an event which is supposed to be processed from the
 * first handler to the last handler in the {@link ChannelPipeline}.
 * For example, all events initiated by an I/O thread are upstream events, and
 * they have the following meaning:
 * <p>
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Event name</th></th><th>Event type and condition</th><th>Meaning</th>
 * </tr>
 * <tr>
 * <td>{@code "messageReceived"}</td>
 * <td>{@link MessageEvent}</td>
 * <td>a message object (e.g. {@link ChannelBuffer}) was received from a remote peer</td>
 * </tr>
 * <tr>
 * <td>{@code "exceptionCaught"}</td>
 * <td>{@link ExceptionEvent}</td>
 * <td>an exception was raised by an I/O thread or a {@link ChannelHandler}</td>
 * </tr>
 * <tr>
 * <td>{@code "channelOpen"}</td>
 * <td>{@link ChannelStateEvent}<br/>(state = {@link ChannelState#OPEN OPEN}, value = {@code true})</td>
 * <td>a {@link Channel} is open, but not bound nor connected</td>
 * </tr>
 * <tr>
 * <td>{@code "channelClosed"}</td>
 * <td>{@link ChannelStateEvent}<br/>(state = {@link ChannelState#OPEN OPEN}, value = {@code false})</td>
 * <td>a {@link Channel} was closed and all its related resources were released</td>
 * </tr>
 * <tr>
 * <td>{@code "channelBound"}</td>
 * <td>{@link ChannelStateEvent}<br/>(state = {@link ChannelState#BOUND BOUND}, value = {@link SocketAddress})</td>
 * <td>a {@link Channel} is open and bound to a local address, but not connected</td>
 * </tr>
 * <tr>
 * <td>{@code "channelUnbound"}</td>
 * <td>{@link ChannelStateEvent}<br/>(state = {@link ChannelState#BOUND BOUND}, value = {@code null})</td>
 * <td>a {@link Channel} was unbound from the current local address</td>
 * </tr>
 * <tr>
 * <td>{@code "channelConnected"}</td>
 * <td>{@link ChannelStateEvent}<br/>(state = {@link ChannelState#CONNECTED CONNECTED}, value = {@link SocketAddress})</td>
 * <td>a {@link Channel} is open, bound to a local address, and connected to a remote address</td>
 * </tr>
 * <tr>
 * <td>{@code "writeComplete"}</td>
 * <td>{@link WriteCompletionEvent}</td>
 * <td>something has been written to a remote peer</td>
 * </tr>
 * <tr>
 * <td>{@code "channelDisconnected"}</td>
 * <td>{@link ChannelStateEvent}<br/>(state = {@link ChannelState#CONNECTED CONNECTED}, value = {@code null})</td>
 * <td>a {@link Channel} was disconnected from its remote peer</td>
 * </tr>
 * <tr>
 * <td>{@code "channelInterestChanged"}</td>
 * <td>{@link ChannelStateEvent}<br/>(state = {@link ChannelState#INTEREST_OPS INTEREST_OPS}, no value)</td>
 * <td>a {@link Channel}'s {@link Channel#getInterestOps() interestOps} was changed</td>
 * </tr>
 * </table>
 * <p>
 * These two additional event types are used only for a parent channel which
 * can have a child channel (e.g. {@link ServerSocketChannel}).
 * <p>
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Event name</th><th>Event type and condition</th><th>Meaning</th>
 * </tr>
 * <tr>
 * <td>{@code "childChannelOpen"}</td>
 * <td>{@link ChildChannelStateEvent}<br/>({@code childChannel.isOpen() = true})</td>
 * <td>a child {@link Channel} was open (e.g. a server channel accepted a connection.)</td>
 * </tr>
 * <tr>
 * <td>{@code "childChannelClosed"}</td>
 * <td>{@link ChildChannelStateEvent}<br/>({@code childChannel.isOpen() = false})</td>
 * <td>a child {@link Channel} was closed (e.g. the accepted connection was closed.)</td>
 * </tr>
 * </table>
 *
 * <h4>Additional resources worth reading</h4>
 * <p>
 * You might want to refer to {@link ChannelDownstreamHandler} to see how a
 * {@link ChannelEvent} is interpreted when going downstream.  Also, please
 * refer to the {@link ChannelEvent} documentation to find out what an upstream
 * event and a downstream event are and what fundamental differences they have,
 * if you didn't read yet.
 *
 * <h3>{@link SimpleChannelHandler}</h3>
 * <p>
 * In most cases, you will get to use a {@link SimpleChannelHandler} to
 * implement an upstream handler because it provides an individual handler
 * method for each event type.  You might want to implement this interface
 * directly though if you want to handle various types of events in more
 * generic way.
 *
 * <h3>Firing an event to the next or previous handler</h3>
 * <p>
 * You can forward the received event upstream or downstream.  In most cases,
 * {@link ChannelUpstreamHandler} will sent the event to the next handler
 * (upstream) although it is absolutely normal to sent the event to the
 * previous handler (downstream):
 *
 * <pre>
 * // Sending the event forward (upstream)
 * void handleUpstream({@link ChannelHandlerContext} ctx, {@link ChannelEvent} e) throws Exception {
 *     ...
 *     ctx.sendUpstream(e);
 *     ...
 * }
 *
 * // Sending the event backward (downstream)
 * void handleDownstream({@link ChannelHandlerContext} ctx, {@link ChannelEvent} e) throws Exception {
 *     ...
 *     ctx.sendDownstream(new MessageEvent(...));
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
 * If there's no {@link ExecutionHandler} in the {@link ChannelPipeline},
 * {@link #handleUpstream(ChannelHandlerContext, ChannelEvent) handleUpstream}
 * will be invoked sequentially by the same thread (i.e. an I/O thread).
 * Please note that this does not necessarily mean that there's a dedicated
 * thread per {@link Channel}; the I/O thread of some transport can serve more
 * than one {@link Channel} (e.g. NIO transport), while the I/O thread of
 * other transports can serve only one (e.g. OIO transport).
 * <p>
 * If an {@link ExecutionHandler} is added in the {@link ChannelPipeline},
 * {@link #handleUpstream(ChannelHandlerContext, ChannelEvent) handleUpstream}
 * may be invoked by different threads at the same time, depending on what
 * {@link Executor} implementation is used with the {@link ExecutionHandler}.
 * <p>
 * {@link OrderedMemoryAwareThreadPoolExecutor} is provided to guarantee the
 * order of {@link ChannelEvent}s.  It does not guarantee that the invocation
 * will be made by the same thread for the same channel, but it does guarantee
 * that the invocation will be made sequentially for the events of the same
 * channel.  For example, the events can be processed as depicted below:
 *
 * <pre>
 *           -----------------------------------&gt; Timeline -----------------------------------&gt;
 *
 * Thread X: --- Channel A (Event 1) --.   .-- Channel B (Event 2) --- Channel B (Event 3) ---&gt;
 *                                      \ /
 *                                       X
 *                                      / \
 * Thread Y: --- Channel B (Event 1) --'   '-- Channel A (Event 2) --- Channel A (Event 3) ---&gt;
 * </pre>
 * <p>
 * Also, please refer to the {@link ChannelPipelineCoverage} annotation to
 * understand the relationship between a handler and its stateful properties.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 */
public interface ChannelUpstreamHandler extends ChannelHandler {

    /**
     * Handles the specified upstream event.
     *
     * @param ctx  the context object for this handler
     * @param e    the upstream event to process or intercept
     */
    void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception;
}
