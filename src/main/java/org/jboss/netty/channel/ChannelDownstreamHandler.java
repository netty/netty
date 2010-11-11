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

/**
 * Handles or intercepts a downstream {@link ChannelEvent}, and sends a
 * {@link ChannelEvent} to the next handler in a {@link ChannelPipeline}.
 * <p>
 * The most common use case of this interface is to intercept an I/O request
 * such as {@link Channel#write(Object)} and {@link Channel#close()}.
 *
 * <h3>{@link SimpleChannelDownstreamHandler}</h3>
 * <p>
 * In most cases, you will get to use a {@link SimpleChannelDownstreamHandler}
 * to implement a downstream handler because it provides an individual handler
 * method for each event type.  You might want to implement this interface
 * directly though if you want to handle various types of events in more
 * generic way.
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
 *     ctx.sendUpstream(new {@link UpstreamChannelStateEvent}(...));
 *     ...
 * }
 * </pre>
 *
 * <h4>Using the helper class to send an event</h4>
 * <p>
 * You will also find various helper methods in {@link Channels} to be useful
 * to generate and send an artificial or manipulated event.
 *
 * <h3>State management</h3>
 *
 * Please refer to {@link ChannelHandler}.
 *
 * <h3>Thread safety</h3>
 * <p>
 * {@link #handleDownstream(ChannelHandlerContext, ChannelEvent) handleDownstream}
 * may be invoked by more than one thread simultaneously.  If the handler
 * accesses a shared resource or stores stateful information, you might need
 * proper synchronization in the handler implementation.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2122 $, $Date: 2010-02-02 11:00:04 +0900 (Tue, 02 Feb 2010) $
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
