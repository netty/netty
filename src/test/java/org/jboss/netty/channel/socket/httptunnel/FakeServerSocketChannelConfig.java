package org.jboss.netty.channel.socket.httptunnel;

import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.buffer.HeapChannelBufferFactory;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.DefaultChannelConfig;
import org.jboss.netty.channel.socket.ServerSocketChannelConfig;

/**
 * @author iain.mcginniss@onedrum.com
 */
public class FakeServerSocketChannelConfig extends DefaultChannelConfig implements ServerSocketChannelConfig {

    public int backlog = 5;
    public int receiveBufferSize = 1024;
    public boolean reuseAddress = false;
    public int connectionTimeout = 5000;
    public ChannelPipelineFactory pipelineFactory;
    public int writeTimeout = 5000;
    public ChannelBufferFactory bufferFactory = new HeapChannelBufferFactory();

    public int getBacklog() {
        return backlog;
    }

    public void setBacklog(int backlog) {
        this.backlog = backlog;
    }

    public int getReceiveBufferSize() {
        return receiveBufferSize;
    }

    public void setReceiveBufferSize(int receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
    }

    public boolean isReuseAddress() {
        return reuseAddress;
    }

    public void setReuseAddress(boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
    }

    public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
        // ignore
    }
}
