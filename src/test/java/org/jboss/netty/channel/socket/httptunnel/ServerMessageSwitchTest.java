package org.jboss.netty.channel.socket.httptunnel;

import static org.junit.Assert.*;

import java.net.InetSocketAddress;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author iain.mcginniss@onedrum.com
 */
@RunWith(JMock.class)
@Ignore
public class ServerMessageSwitchTest {

    public static final InetSocketAddress REMOTE_ADDRESS = InetSocketAddress.createUnresolved("test.client.com", 52354);

    private final JUnit4Mockery mockContext = new JUnit4Mockery();

    private ServerMessageSwitch messageSwitch;
    HttpTunnelAcceptedChannelFactory newChannelFactory;

    private UpstreamEventCatcher htunEventCatcher;
    private FakeChannelSink responseCatcher;

    private FakeSocketChannel htunChannel;
    private FakeSocketChannel requesterChannel;

    @Before
    public void setUp() throws Exception {
        newChannelFactory = mockContext.mock(HttpTunnelAcceptedChannelFactory.class);
        messageSwitch = new ServerMessageSwitch(newChannelFactory);

        final Channel htunAcceptedChannel = createTunnelChannel();
        createRequesterChannel();

        mockContext.checking(new Expectations() {{
            one(newChannelFactory).newChannel(with(any(String.class)), with(equal(REMOTE_ADDRESS))); will(returnValue(htunAcceptedChannel));
        }});
    }

    private Channel createTunnelChannel() {
        ChannelPipeline acceptedChannelPipeline = Channels.pipeline();
        htunEventCatcher = new UpstreamEventCatcher();
        acceptedChannelPipeline.addLast(UpstreamEventCatcher.NAME, htunEventCatcher);
        FakeSocketChannel htunAcceptedChannel = new FakeSocketChannel(null, null, acceptedChannelPipeline, new FakeChannelSink());
        htunEventCatcher.events.clear();

        return htunAcceptedChannel;
    }

    private FakeSocketChannel createRequesterChannel() {
        ChannelPipeline requesterChannelPipeline = Channels.pipeline();
        responseCatcher = new FakeChannelSink();
        requesterChannel = new FakeSocketChannel(null, null, requesterChannelPipeline, responseCatcher);
        responseCatcher.events.clear();

        return requesterChannel;
    }

    @Test
    public void testRouteInboundData() {
        ChannelBuffer inboundData = ChannelBuffers.dynamicBuffer();
        inboundData.writeLong(1234L);

        String tunnelId = messageSwitch.createTunnel(REMOTE_ADDRESS);
        messageSwitch.routeInboundData(tunnelId, inboundData);
        assertEquals(1, htunEventCatcher.events.size());
        ChannelBuffer receivedData = NettyTestUtils.checkIsUpstreamMessageEvent(htunEventCatcher.events.poll(), ChannelBuffer.class);
        assertSame(receivedData, inboundData);
    }

    @Test
    public void testRouteOutboundData_onPoll() {
        ChannelBuffer outboundData = ChannelBuffers.dynamicBuffer();
        outboundData.writeLong(1234L);

        String tunnelId = messageSwitch.createTunnel(REMOTE_ADDRESS);
        messageSwitch.routeOutboundData(tunnelId, outboundData, Channels.future(htunChannel));
        messageSwitch.pollOutboundData(tunnelId, requesterChannel);

        assertEquals(1, responseCatcher.events.size());
        HttpResponse response = NettyTestUtils.checkIsDownstreamMessageEvent(responseCatcher.events.poll(), HttpResponse.class);
        assertSame(outboundData, response.getContent());
    }

    @Test
    public void testRouteOutboundData_withDanglingRequest() {
        String tunnelId = messageSwitch.createTunnel(REMOTE_ADDRESS);
        messageSwitch.pollOutboundData(tunnelId, requesterChannel);
        assertEquals(0, responseCatcher.events.size());

        ChannelBuffer outboundData = ChannelBuffers.dynamicBuffer();
        outboundData.writeLong(1234L);

        messageSwitch.routeOutboundData(tunnelId, outboundData, Channels.future(htunChannel));
        assertEquals(1, responseCatcher.events.size());
        HttpResponse response = NettyTestUtils.checkIsDownstreamMessageEvent(responseCatcher.events.poll(), HttpResponse.class);
        assertSame(outboundData, response.getContent());
    }

    @Test
    public void testCloseTunnel() {
        String tunnelId = messageSwitch.createTunnel(REMOTE_ADDRESS);
        messageSwitch.closeTunnel(tunnelId);
        assertFalse(messageSwitch.isOpenTunnel(tunnelId));
    }

    @Test
    public void testRouteInboundDataIgnoredAfterClose() {
        ChannelBuffer data = NettyTestUtils.createData(1234L);
        String tunnelId = messageSwitch.createTunnel(REMOTE_ADDRESS);
        messageSwitch.closeTunnel(tunnelId);
        messageSwitch.routeInboundData(tunnelId, data);
        assertEquals(0, htunEventCatcher.events.size());
    }

    @Test
    public void testRouteOutboundDataIgnoredAfterClose() {
        ChannelBuffer data = NettyTestUtils.createData(1234L);
        String tunnelId = messageSwitch.createTunnel(REMOTE_ADDRESS);
        messageSwitch.closeTunnel(tunnelId);
        messageSwitch.routeOutboundData(tunnelId, data, Channels.future(htunChannel));
        assertEquals(0, responseCatcher.events.size());
    }
}
