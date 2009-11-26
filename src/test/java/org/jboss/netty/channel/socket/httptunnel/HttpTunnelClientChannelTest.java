package org.jboss.netty.channel.socket.httptunnel;


import static org.junit.Assert.*;

import java.net.InetSocketAddress;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.junit.Before;
import org.junit.Test;

/**
 * @author iain.mcginniss@onedrum.com
 */
public class HttpTunnelClientChannelTest {

    public static final InetSocketAddress LOCAL_ADDRESS = InetSocketAddress.createUnresolved("localhost", 50123);
    public static final InetSocketAddress REMOTE_ADDRESS = InetSocketAddress.createUnresolved("test.server.com", 12345);

    private UpstreamEventCatcher upstreamCatcher;
    private HttpTunnelClientChannel channel;

    private FakeClientSocketChannelFactory outboundFactory;
    private FakeSocketChannel sendChannel;
    private FakeSocketChannel pollChannel;
    private FakeChannelSink sendSink;
    private FakeChannelSink pollSink;

    @Before
    public void setUp() throws Exception {
        ChannelPipeline pipeline = Channels.pipeline();
        upstreamCatcher = new UpstreamEventCatcher();
        pipeline.addLast(UpstreamEventCatcher.NAME, upstreamCatcher);

        outboundFactory = new FakeClientSocketChannelFactory();

        HttpTunnelClientChannelFactory factory =  new HttpTunnelClientChannelFactory(outboundFactory);
        channel = factory.newChannel(pipeline);

        assertEquals(2, outboundFactory.createdChannels.size());

        sendChannel = outboundFactory.createdChannels.get(0);
        pollChannel = outboundFactory.createdChannels.get(1);
        sendSink = (FakeChannelSink) sendChannel.sink;
        pollSink = (FakeChannelSink) pollChannel.sink;
    }

    @Test
    public void testConnect() {
        Channels.connect(channel, REMOTE_ADDRESS);

        // this should result in a CONNECTED state event on the send channel, but not on the poll
        // channel just yet
        assertEquals(1, sendSink.events.size());
        assertEquals(0, pollSink.events.size());
        ChannelEvent sendChannelEvent = sendSink.events.poll();
        NettyTestUtils.checkIsStateEvent(sendChannelEvent, ChannelState.CONNECTED, REMOTE_ADDRESS);

        // once the send channel indicates that it is connected, we should see the tunnel open request
        // being sent
        sendChannel.emulateConnected(LOCAL_ADDRESS, REMOTE_ADDRESS, ((ChannelStateEvent)sendChannelEvent).getFuture());
        assertEquals(1, sendSink.events.size());
        ChannelEvent openTunnelRequest = sendSink.events.poll();
        NettyTestUtils.checkIsDownstreamMessageEvent(openTunnelRequest, ChannelBuffer.class);
    }

    @Test
    public void testBind() {
        ChannelFuture bindFuture = Channels.bind(channel, LOCAL_ADDRESS);
        assertFalse(bindFuture.isDone());

        assertEquals(1, sendSink.events.size());
        assertEquals(1, pollSink.events.size());

        ChannelEvent sendChannelEvent = sendSink.events.poll();
        NettyTestUtils.checkIsStateEvent(sendChannelEvent, ChannelState.BOUND, LOCAL_ADDRESS);
        ChannelEvent pollChannelEvent = pollSink.events.poll();
        NettyTestUtils.checkIsStateEvent(pollChannelEvent, ChannelState.BOUND, LOCAL_ADDRESS);

        sendChannel.emulateBound(LOCAL_ADDRESS, sendChannelEvent.getFuture());
        assertFalse(bindFuture.isDone());
        pollChannel.emulateBound(LOCAL_ADDRESS, pollChannelEvent.getFuture());
        assertTrue(bindFuture.isDone());
        assertTrue(bindFuture.isSuccess());
    }

    @Test
    public void testBind_sendBindFails() {
        ChannelFuture bindFuture = Channels.bind(channel, LOCAL_ADDRESS);
        assertFalse(bindFuture.isDone());

        Exception bindFailureReason = new Exception("could not bind");
        ((ChannelStateEvent) sendSink.events.poll()).getFuture().setFailure(bindFailureReason);
        assertTrue(bindFuture.isDone());
        assertFalse(bindFuture.isSuccess());
        assertSame(bindFailureReason, bindFuture.getCause());
    }

    @Test
    public void testBind_pollBindFails() {
        ChannelFuture bindFuture = Channels.bind(channel, LOCAL_ADDRESS);
        assertFalse(bindFuture.isDone());

        Exception bindFailureReason = new Exception("could not bind");
        ((ChannelStateEvent) pollSink.events.poll()).getFuture().setFailure(bindFailureReason);
        assertTrue(bindFuture.isDone());
        assertFalse(bindFuture.isSuccess());
        assertSame(bindFailureReason, bindFuture.getCause());
    }
}
