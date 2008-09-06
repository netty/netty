package org.jboss.netty.util;

import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelUpstreamHandler;

/**
 * A dummy handler for a testing purpose.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 */
@ChannelPipelineCoverage("all")
public class DummyHandler implements ChannelUpstreamHandler {

    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e)
            throws Exception {
        // Do nothing.
    }
}
