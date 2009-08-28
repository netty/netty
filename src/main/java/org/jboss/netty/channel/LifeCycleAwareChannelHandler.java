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
 * A {@link ChannelHandler} that is notified when it is added to or removed
 * from a {@link ChannelPipeline}.
 * <p>
 * Please note that the methods of this handler is called only when the
 * {@link ChannelPipeline} it belongs to has been
 * {@linkplain ChannelPipeline#attach(Channel, ChannelSink) attached}.
 * That is, if you add a {@link LifeCycleAwareChannelHandler} to the unattached
 * {@link ChannelPipeline}, the life cycle handler methods in this handler will
 * not be invoked because there's no associated {@link ChannelHandlerContext}:
 * <pre>
 * // Create a new pipeline which is unattached initially.
 * ChannelPipeline pipeline = Channels.pipeline();
 * // beforeAdd() and afterAdd() will not be called.
 * pipeline.addLast("handler", new MyLifeCycleAwareChannelHandler());
 * // beforeRemove() and afterRemove() will not be called.
 * pipeline.remove("handler");
 * </pre>
 * However, once the {@link ChannelPipeline} is attached and the
 * {@code channelOpen} event is fired, the life cycle handler methods will
 * be invoked on addition or removal:
 * <pre>
 * public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent evt) {
 *     // channelOpen event has been triggered, which means the pipeline has
 *     // been attached to the channel.
 *     ChannelPipeline pipeline = ctx.getChannel().getPipeline();
 *     // beforeAdd() and afterAdd() will be called.
 *     pipeline.addLast("handler", new MyLifeCycleAwareChannelHandler());
 * }
 *
 * public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent evt) {
 *     // Once attached, the pipeline is never detached.
 *     ChannelPipeline pipeline = ctx.getChannel().getPipeline();
 *     // beforeRemove() and afterRemove() will be called.
 *     pipeline.remove("handler");
 * }
 * </pre>
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public interface LifeCycleAwareChannelHandler extends ChannelHandler {
    void beforeAdd(ChannelHandlerContext ctx) throws Exception;
    void afterAdd(ChannelHandlerContext ctx) throws Exception;
    void beforeRemove(ChannelHandlerContext ctx) throws Exception;
    void afterRemove(ChannelHandlerContext ctx) throws Exception;
}
