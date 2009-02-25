package org.jboss.netty.channel.xnio;

import static org.jboss.netty.channel.Channels.*;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public class XnioClientChannelHandler extends AbstractXnioChannelHandler {

    public void handleOpened(java.nio.channels.Channel channel) {
        XnioChannel c = XnioChannelRegistry.getChannel(channel);
        fireChannelOpen(c);
        if (c.isBound()) {
            fireChannelBound(c, c.getLocalAddress());
            if (c.isConnected()) {
                fireChannelConnected(c, c.getRemoteAddress());
            }
        }

        // Start to read.
        resumeRead(channel);
    }
}
