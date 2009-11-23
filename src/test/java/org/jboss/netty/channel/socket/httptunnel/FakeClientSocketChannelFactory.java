package org.jboss.netty.channel.socket.httptunnel;

import java.util.ArrayList;
import java.util.List;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.SocketChannel;

/**
 * @author iain.mcginniss@onedrum.com
 */
public class FakeClientSocketChannelFactory implements ClientSocketChannelFactory {

    public List<FakeSocketChannel> createdChannels;
    
    public FakeClientSocketChannelFactory() {
        createdChannels = new ArrayList<FakeSocketChannel>();
    }
    
    public SocketChannel newChannel(ChannelPipeline pipeline) {
        FakeSocketChannel channel = new FakeSocketChannel(null, this, pipeline, new FakeChannelSink());
        createdChannels.add(channel);
        return channel;
    }

    public void releaseExternalResources() {
        // nothing to do
    }

}
