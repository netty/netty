/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.ipfilter;

import java.net.InetSocketAddress;

import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.Channels;

// TODO: Auto-generated Javadoc

/** General class that handle Ip Filtering. */
public abstract class IpFilteringHandlerImpl implements ChannelUpstreamHandler, IpFilteringHandler {

    private IpFilterListener listener;

    /**
     * Called when the channel is connected. It returns True if the corresponding connection
     * is to be allowed. Else it returns False.
     *
     * @param inetSocketAddress the remote {@link InetSocketAddress} from client
     * @return True if the corresponding connection is allowed, else False.
     */
    protected abstract boolean accept(ChannelHandlerContext ctx, ChannelEvent e, InetSocketAddress inetSocketAddress)
            throws Exception;

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
    protected ChannelFuture handleRefusedChannel(ChannelHandlerContext ctx, ChannelEvent e,
                                                 InetSocketAddress inetSocketAddress) throws Exception {
        if (listener == null) {
            return null;
        }
        return listener.refused(ctx, e, inetSocketAddress);
    }

    protected ChannelFuture handleAllowedChannel(ChannelHandlerContext ctx, ChannelEvent e,
                                                 InetSocketAddress inetSocketAddress) throws Exception {
        if (listener == null) {
            return null;
        }
        return listener.allowed(ctx, e, inetSocketAddress);
    }

    /**
     * Internal method to test if the current channel is blocked. Should not be overridden.
     *
     * @return True if the current channel is blocked, else False
     */
    protected boolean isBlocked(ChannelHandlerContext ctx) {
        return ctx.getAttachment() != null;
    }

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
    protected boolean continues(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        if (listener != null) {
            return listener.continues(ctx, e);
        } else {
            return false;
        }
    }

    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        if (e instanceof ChannelStateEvent) {
            ChannelStateEvent evt = (ChannelStateEvent) e;
            switch (evt.getState()) {
                case OPEN:
                case BOUND:
                    if (evt.getValue() != Boolean.TRUE) {
                        ctx.sendUpstream(e);
                        return;
                    }

                    // Special case: OPEND and BOUND events are before CONNECTED,
                    // but CLOSED and UNBOUND events are after DISCONNECTED: should those events be blocked too?
                    if (isBlocked(ctx) && !continues(ctx, evt)) {
                        // don't pass to next level since channel was blocked early
                        return;
                    } else {
                        ctx.sendUpstream(e);
                        return;
                    }
                case CONNECTED:
                    if (evt.getValue() != null) {
                        // CONNECTED
                        InetSocketAddress inetSocketAddress = (InetSocketAddress) e.getChannel().getRemoteAddress();
                        if (!accept(ctx, e, inetSocketAddress)) {
                            ctx.setAttachment(Boolean.TRUE);
                            ChannelFuture future = handleRefusedChannel(ctx, e, inetSocketAddress);
                            if (future != null) {
                                future.addListener(ChannelFutureListener.CLOSE);
                            } else {
                                Channels.close(e.getChannel());
                            }
                            if (isBlocked(ctx) && !continues(ctx, evt)) {
                                // don't pass to next level since channel was blocked early
                                return;
                            }
                        } else {
                            handleAllowedChannel(ctx, e, inetSocketAddress);
                        }
                        // This channel is not blocked
                        ctx.setAttachment(null);
                    } else {
                        // DISCONNECTED
                        if (isBlocked(ctx) && !continues(ctx, evt)) {
                            // don't pass to next level since channel was blocked early
                            return;
                        }
                    }
                    break;
            }
        }
        if (isBlocked(ctx) && !continues(ctx, e)) {
            // don't pass to next level since channel was blocked early
            return;
        }
        // Whatever it is, if not blocked, goes to the next level
        ctx.sendUpstream(e);
    }

    public void setIpFilterListener(IpFilterListener listener) {
        this.listener = listener;
    }

    public void removeIpFilterListener() {
        listener = null;
    }
}
