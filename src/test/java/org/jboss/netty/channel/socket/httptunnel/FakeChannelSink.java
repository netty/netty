package org.jboss.netty.channel.socket.httptunnel;

import java.util.LinkedList;
import java.util.Queue;

import org.jboss.netty.channel.AbstractChannelSink;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelPipeline;

/**
 * @author iain.mcginniss@onedrum.com
 */
public class FakeChannelSink extends AbstractChannelSink {

    public Queue<ChannelEvent> events = new LinkedList<ChannelEvent>();
    
    public void eventSunk(ChannelPipeline pipeline, ChannelEvent e) throws Exception {
        events.add(e);
    }

}
