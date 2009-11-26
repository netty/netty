package org.jboss.netty.channel.socket.httptunnel;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelFuture;

/**
 * @author iain.mcginniss@onedrum.com
 */
public class MockChannelStateListener implements HttpTunnelClientWorkerOwner {

    public boolean fullyEstablished = false;
    public List<ChannelBuffer> messages = new ArrayList<ChannelBuffer>();
    public String tunnelId = null;
    public String serverHostName = null;

    public void fullyEstablished() {
        fullyEstablished = true;
    }

    public void onConnectRequest(ChannelFuture connectFuture, InetSocketAddress remoteAddress) {
        // not relevant for test
    }

    public void onMessageReceived(ChannelBuffer content) {
        messages.add(content);
    }

    public void onTunnelOpened(String tunnelId) {
        this.tunnelId = tunnelId;
    }

    public String getServerHostName() {
        return serverHostName;
    }

}
