/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.ipfilter;

import java.net.InetSocketAddress;

import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;

/**
 * The listener interface for receiving ipFilter events.
 *
 * @see IpFilteringHandler
 */
public interface IpFilterListener {

    /**
     * Called when the channel has the CONNECTED status and the channel was allowed by a previous call to accept().
     * This method enables your implementation to send a message back to the client before closing
     * or whatever you need. This method returns a ChannelFuture on which the implementation
     * can wait uninterruptibly before continuing.<br>
     * For instance, If a message is sent back, the corresponding ChannelFuture has to be returned.
     *
     * @param inetSocketAddress the remote {@link InetSocketAddress} from client
     * @return the associated ChannelFuture to be waited for before closing the channel. Null is allowed.
     */
    ChannelFuture allowed(ChannelHandlerContext ctx, ChannelEvent e, InetSocketAddress inetSocketAddress);

    /**
     * Called when the channel has the CONNECTED status and the channel was refused by a previous call to accept().
     * This method enables your implementation to send a message back to the client before closing
     * or whatever you need. This method returns a ChannelFuture on which the implementation
     * will wait uninterruptibly before closing the channel.<br>
     * For instance, If a message is sent back, the corresponding ChannelFuture has to be returned.
     *
     * @param inetSocketAddress the remote {@link InetSocketAddress} from client
     * @return the associated ChannelFuture to be waited for before closing the channel. Null is allowed.
     */
    ChannelFuture refused(ChannelHandlerContext ctx, ChannelEvent e, InetSocketAddress inetSocketAddress);

    /**
     * Called in handleUpstream, if this channel was previously blocked,
     * to check if whatever the event, it should be passed to the next entry in the pipeline.<br>
     * If one wants to not block events, just overridden this method by returning always true.<br><br>
     * <b>Note that OPENED and BOUND events are still passed to the next entry in the pipeline since
     * those events come out before the CONNECTED event and so the possibility to filter the connection.</b>
     *
     * @return True if the event should continue, False if the event should not continue
     *         since this channel was blocked by this filter
     */
    boolean continues(ChannelHandlerContext ctx, ChannelEvent e);

}
