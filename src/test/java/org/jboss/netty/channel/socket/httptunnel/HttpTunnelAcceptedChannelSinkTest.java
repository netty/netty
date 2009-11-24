package org.jboss.netty.channel.socket.httptunnel;

import static org.junit.Assert.*;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;
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
public class HttpTunnelAcceptedChannelSinkTest {

    private static final String TUNNEL_ID = "1";
    private final JUnit4Mockery mockContext = new JUnit4Mockery();
    ServerMessageSwitchDownstreamInterface messageSwitch;
    private HttpTunnelAcceptedChannelSink sink;
    private FakeSocketChannel channel;
    private UpstreamEventCatcher upstreamCatcher;

    @Before
    public void setUp() throws Exception {
        messageSwitch = mockContext.mock(ServerMessageSwitchDownstreamInterface.class);
        sink = new HttpTunnelAcceptedChannelSink(messageSwitch, TUNNEL_ID);
        ChannelPipeline pipeline = Channels.pipeline();
        upstreamCatcher = new UpstreamEventCatcher();
        pipeline.addLast(UpstreamEventCatcher.NAME, upstreamCatcher);
        channel = new FakeSocketChannel(null, null, pipeline, sink);
        upstreamCatcher.events.clear();
    }

    @Test
    public void testSendData() {
        final ChannelBuffer outboundData = NettyTestUtils.createData(1234L);

        mockContext.checking(new Expectations() {{
            one(messageSwitch).routeOutboundData(with(equal(TUNNEL_ID)), with(same(outboundData)), with(any(ChannelFuture.class)));
        }});

        Channels.write(channel, outboundData);
    }

    @Test
    public void testSendInvalidDataType() {
        Channels.write(channel, new Object());
        assertEquals(1, upstreamCatcher.events.size());
        NettyTestUtils.checkIsExceptionEvent(upstreamCatcher.events.poll());
    }

    @Test
    public void testUnbind() {
        mockContext.checking(new Expectations() {{
            one(messageSwitch).closeTunnel(TUNNEL_ID);
        }});
        Channels.unbind(channel);
    }

    @Test
    public void testDisconnect() {
        mockContext.checking(new Expectations() {{
            one(messageSwitch).closeTunnel(TUNNEL_ID);
        }});

        Channels.disconnect(channel);
    }
}
