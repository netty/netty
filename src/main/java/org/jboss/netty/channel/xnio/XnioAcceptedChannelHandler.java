/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.netty.channel.xnio;

import static org.jboss.netty.channel.Channels.*;

import java.net.SocketAddress;

import org.jboss.xnio.IoUtils;
import org.jboss.xnio.channels.BoundChannel;
import org.jboss.xnio.channels.MultipointMessageChannel;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
@SuppressWarnings("unchecked")
final class XnioAcceptedChannelHandler extends AbstractXnioChannelHandler {

    public void handleOpened(java.nio.channels.Channel channel) {
        // Get the parent channel
        DefaultXnioServerChannel parent = null;
        if (channel instanceof BoundChannel) {
            SocketAddress localAddress = (SocketAddress) ((BoundChannel) channel).getLocalAddress();
            parent = XnioChannelRegistry.getServerChannel(localAddress);
            if (parent == null) {
                // An accepted channel with no parent
                // probably a race condition or a port not bound by Netty.
                IoUtils.safeClose(channel);
                return;
            }
        } else {
            // Should not reach here.
            IoUtils.safeClose(channel);
            return;
        }

        if (parent.xnioChannel instanceof MultipointMessageChannel) {
            // Multipoint channel
            XnioChannelRegistry.registerChannelMapping(parent);
        } else {
            // Accepted child channel
            try {
                BaseXnioChannel c = new XnioAcceptedChannel(
                        parent, parent.getFactory(),
                        parent.getConfig().getPipelineFactory().getPipeline(),
                        parent.getFactory().sink);
                c.xnioChannel = channel;
                fireChannelOpen(c);
                if (c.isBound()) {
                    fireChannelBound(c, c.getLocalAddress());
                    if (c.isConnected()) {
                        fireChannelConnected(c, c.getRemoteAddress());
                    }
                }
                XnioChannelRegistry.registerChannelMapping(c);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        // Start to read.
        resumeRead(channel);
    }
}
