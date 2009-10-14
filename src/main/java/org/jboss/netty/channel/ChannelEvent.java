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
 * An I/O event or I/O request associated with a {@link Channel}.
 * <p>
 * A {@link ChannelEvent} is supposed to be handled by the
 * {@link ChannelPipeline} which is attached to the {@link Channel} that
 * the event belongs to.  Once an event is sent to a {@link ChannelPipeline},
 * it is handled by a list of {@link ChannelHandler}s.
 *
 * <h3>Upstream events and downstream events, and their interpretation</h3>
 * <p>
 * Every event is either an upstream event or a downstream event.
 * If an event flows forward from the first handler to the last handler in a
 * {@link ChannelPipeline}, we call it an upstream event and say <strong>"an
 * event goes upstream."</strong>  If an event flows backward from the last
 * handler to the first handler in a {@link ChannelPipeline}, we call it a
 * downstream event and say <strong>"an event goes downstream."</strong>
 * (Please refer to the diagram in {@link ChannelPipeline} for more explanation.)
 * <p>
 * A {@link ChannelEvent} is interpreted differently by a {@link ChannelHandler}
 * depending on whether the event is an upstream event or a downstream event.
 * An upstream event represents the notification of what happened in the past.
 * By contrast, a downstream event represents the request of what should happen
 * in the future.  For example, a {@link MessageEvent} represents the
 * notification of a received message when it goes upstream, while it
 * represents the request of writing a message when it goes downstream.
 *
 * <h4>Additional resources worth reading</h4>
 * <p>
 * Please refer to the documentation of {@link ChannelHandler} and its sub-types
 * ({@link ChannelUpstreamHandler} for upstream events and
 *  {@link ChannelDownstreamHandler} for downstream events) to find out how
 * a {@link ChannelEvent} is interpreted depending on the type of the handler
 * more in detail.  Also, please refer to the {@link ChannelPipeline}
 * documentation to find out how an event flows in a pipeline.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 * @apiviz.composedOf org.jboss.netty.channel.ChannelFuture
 */
public interface ChannelEvent {

    /**
     * Returns the {@link Channel} which is associated with this event.
     */
    Channel getChannel();

    /**
     * Returns the {@link ChannelFuture} which is associated with this event.
     * If this event is an upstream event, this method will always return a
     * {@link SucceededChannelFuture} because the event has occurred already.
     * If this event is a downstream event (i.e. I/O request), the returned
     * future will be notified when the I/O request succeeds or fails.
     */
    ChannelFuture getFuture();
}
