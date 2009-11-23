package org.jboss.netty.channel.socket.httptunnel;

import static org.jboss.netty.channel.Channels.*;

import java.net.InetSocketAddress;

import org.jboss.netty.channel.AbstractChannel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.socket.ServerSocketChannel;
import org.jboss.netty.channel.socket.ServerSocketChannelConfig;

/**
 * @author iain.mcginniss@onedrum.com
 */
public class FakeServerSocketChannel extends AbstractChannel implements ServerSocketChannel {

    public boolean bound;
    public boolean connected;
    public InetSocketAddress remoteAddress;
    public InetSocketAddress localAddress;
    public ServerSocketChannelConfig config = new FakeServerSocketChannelConfig();

    public FakeServerSocketChannel(ChannelFactory factory, ChannelPipeline pipeline, ChannelSink sink) {
        super(null, factory, pipeline, sink);
    }

    public ServerSocketChannelConfig getConfig() {
        return config;
    }

    public InetSocketAddress getLocalAddress() {
        return localAddress;
    }

    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public boolean isBound() {
        return bound;
    }

    public boolean isConnected() {
        return connected;
    }
    
    
    public FakeSocketChannel acceptNewConnection(InetSocketAddress remoteAddress, ChannelSink sink) throws Exception {
        ChannelPipeline newPipeline = getConfig().getPipelineFactory().getPipeline();
        FakeSocketChannel newChannel = new FakeSocketChannel(this, getFactory(), newPipeline, sink);
        newChannel.localAddress = this.localAddress;
        newChannel.remoteAddress = remoteAddress;
        fireChannelOpen(newChannel);
        fireChannelBound(newChannel, newChannel.localAddress);
        fireChannelConnected(this, newChannel.remoteAddress);
        
        return newChannel;
    }

    
}
