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
