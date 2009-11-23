package org.jboss.netty.channel.socket.httptunnel;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.socket.ServerSocketChannel;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;

/**
 * @author iain.mcginniss@onedrum.com
 */
public class FakeServerSocketChannelFactory implements ServerSocketChannelFactory {

    public ChannelSink sink = new FakeChannelSink();
    public FakeServerSocketChannel createdChannel;
    
    public ServerSocketChannel newChannel(ChannelPipeline pipeline) {
        createdChannel = new FakeServerSocketChannel(this, pipeline, sink);
        return createdChannel;
    }

    public void releaseExternalResources() {
        // nothing to do
    }

}
