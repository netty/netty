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

import java.util.concurrent.Executor;

import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;


/**
 * Handles or intercepts a upstream {@link ChannelEvent}, and fires a
 * {@link ChannelEvent} to the next or previous handler in a
 * {@link ChannelPipeline}.
 * <p>
 * A upstream event is an event which is supposed to be processed from the
 * first handler to the last handler in the {@link ChannelPipeline}.
 * For example, all events fired by an I/O thread are upstream events.
 * <p>
 * In most cases, you should use a {@link SimpleChannelHandler} to implement
 * this interface more easily.  You might want to implement this interface
 * directly though, when you want to handle various types of events in more
 * generic way.
 *
 * <h3>Firing an event to the next or previous handler</h3>
 * <p>
 * You can forward the received event upstream or downstream.  In most cases,
 * {@link ChannelUpstreamHandler} will fire the event to the next handler
 * (upstream) although it is absolutely normal to fire the event to the
 * previous handler (downstream):
 *
 * <pre>
 * // Sending the event forward (upstream)
 * void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
 *     ...
 *     ctx.sendUpstream(e);
 *     ...
 * }
 *
 * // Sending the event backward (downstream)
 * void handleDownstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
 *     ...
 *     ctx.sendDownstream(new MessageEvent(...));
 *     ...
 * }
 * </pre>
 * <p>
 * You will also find various helper methods in {@link Channels} to be useful
 * to generate and fire an artificial or manipulated event.
 *
 * <a name="thread_safety"></a>
 * <h3>Thread safety</h3>
 * <p>
 * If there's no {@link ExecutionHandler} in the {@link ChannelPipeline},
 * {@link #handleUpstream(ChannelHandlerContext, ChannelEvent) handleUpstream}
 * will be invoked sequentially by the same thread (i.e. an I/O thread).
 * Please note that this doesn't necessarily mean that there's a dedicated
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
 *
 * @apiviz.landmark
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
