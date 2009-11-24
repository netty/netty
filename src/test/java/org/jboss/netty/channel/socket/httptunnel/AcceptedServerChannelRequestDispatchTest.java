package org.jboss.netty.channel.socket.httptunnel;


import static org.junit.Assert.*;

import java.net.InetSocketAddress;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpRequest;
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
public class AcceptedServerChannelRequestDispatchTest {

    private static final String HOST = "test.server.com";

    private static final String KNOWN_TUNNEL_ID = "1";
    protected static final String UNKNOWN_TUNNEL_ID = "unknownTunnel";

    private final JUnit4Mockery mockContext = new JUnit4Mockery();

    private AcceptedServerChannelRequestDispatch handler;
    FakeSocketChannel channel;
    private FakeChannelSink sink;
    ServerMessageSwitchUpstreamInterface messageSwitch;

    @Before
    public void setUp() throws Exception {
        ChannelPipeline pipeline = Channels.pipeline();
        messageSwitch = mockContext.mock(ServerMessageSwitchUpstreamInterface.class);
        handler = new AcceptedServerChannelRequestDispatch(messageSwitch);
        pipeline.addLast(AcceptedServerChannelRequestDispatch.NAME, handler);
        sink = new FakeChannelSink();
        channel = new FakeSocketChannel(null, null, pipeline, sink);
        channel.remoteAddress = InetSocketAddress.createUnresolved("test.client.com", 51231);

        mockContext.checking(new Expectations() {{
            ignoring(messageSwitch).isOpenTunnel(KNOWN_TUNNEL_ID); will(returnValue(true));
        }});
    }

    @Test
    public void testTunnelOpenRequest() {
        mockContext.checking(new Expectations() {{
            one(messageSwitch).createTunnel(channel.remoteAddress); will(returnValue(KNOWN_TUNNEL_ID));
        }});

        Channels.fireMessageReceived(channel, HttpTunnelMessageUtils.createOpenTunnelRequest(HOST));
        assertEquals(1, sink.events.size());
        HttpResponse response = NettyTestUtils.checkIsDownstreamMessageEvent(sink.events.poll(), HttpResponse.class);
        assertTrue(HttpTunnelMessageUtils.isTunnelOpenResponse(response));
    }

    @Test
    public void testTunnelCloseRequest() {
        mockContext.checking(new Expectations() {{
            one(messageSwitch).closeTunnel(KNOWN_TUNNEL_ID);
        }});

        HttpRequest request = HttpTunnelMessageUtils.createCloseTunnelRequest(HOST, KNOWN_TUNNEL_ID);
        Channels.fireMessageReceived(channel, request);
        assertEquals(1, sink.events.size());
        HttpResponse response = NettyTestUtils.checkIsDownstreamMessageEvent(sink.events.poll(), HttpResponse.class);
        assertTrue(HttpTunnelMessageUtils.isTunnelCloseResponse(response));
    }

    @Test
    public void testTunnelCloseRequestWithoutTunnelIdRejected() {
        HttpRequest request = HttpTunnelMessageUtils.createCloseTunnelRequest(HOST, null);
        checkRequestWithoutTunnelIdIsRejected(request);
    }

    @Test
    public void testTunnelCloseRequestWithUnknownTunnelId() {
        HttpRequest request = HttpTunnelMessageUtils.createCloseTunnelRequest(HOST, UNKNOWN_TUNNEL_ID);
        checkRequestWithUnknownTunnelIdIsRejected(request);
    }

    @Test
    public void testSendDataRequest() {
        final ChannelBuffer expectedData = ChannelBuffers.dynamicBuffer();
        expectedData.writeLong(1234L);
        mockContext.checking(new Expectations() {{
            one(messageSwitch).routeInboundData(KNOWN_TUNNEL_ID, expectedData);
        }});

        HttpRequest request = HttpTunnelMessageUtils.createSendDataRequest(HOST, KNOWN_TUNNEL_ID, expectedData);
        Channels.fireMessageReceived(channel, request);

        assertEquals(1, sink.events.size());
        HttpResponse response = NettyTestUtils.checkIsDownstreamMessageEvent(sink.events.poll(), HttpResponse.class);
        assertTrue(HttpTunnelMessageUtils.isOKResponse(response));
    }

    @Test
    public void testSendDataRequestWithNoContentRejected() {
        HttpRequest request = HttpTunnelMessageUtils.createSendDataRequest(HOST, KNOWN_TUNNEL_ID, ChannelBuffers.dynamicBuffer());
        Channels.fireMessageReceived(channel, request);

        assertEquals(1, sink.events.size());
        checkResponseIsRejection("Send data requests must contain data");
    }

    @Test
    public void testSendDataRequestForUnknownTunnelIdRejected() {
        HttpRequest request = HttpTunnelMessageUtils.createSendDataRequest(HOST, UNKNOWN_TUNNEL_ID, ChannelBuffers.dynamicBuffer());
        checkRequestWithUnknownTunnelIdIsRejected(request);
    }

    @Test
    public void testSendDataRequestWithoutTunnelIdRejected() {
        HttpRequest request = HttpTunnelMessageUtils.createSendDataRequest(HOST, null, ChannelBuffers.dynamicBuffer());
        checkRequestWithoutTunnelIdIsRejected(request);
    }

    @Test
    public void testReceiveDataRequest() {
        mockContext.checking(new Expectations() {{
            one(messageSwitch).pollOutboundData(KNOWN_TUNNEL_ID, channel);
        }});
        HttpRequest request = HttpTunnelMessageUtils.createReceiveDataRequest(HOST, KNOWN_TUNNEL_ID);
        Channels.fireMessageReceived(channel, request);
    }

    @Test
    public void testReceiveDataRequestWithoutTunnelIdRejected() {
        HttpRequest request = HttpTunnelMessageUtils.createReceiveDataRequest(HOST, null);
        checkRequestWithoutTunnelIdIsRejected(request);
    }

    @Test
    public void testReceiveDataRequestForUnknownTunnelIdRejected() {
        HttpRequest request = HttpTunnelMessageUtils.createReceiveDataRequest(HOST, UNKNOWN_TUNNEL_ID);
        checkRequestWithUnknownTunnelIdIsRejected(request);
    }

    private void checkRequestWithoutTunnelIdIsRejected(HttpRequest request) {
        Channels.fireMessageReceived(channel, request);
        assertEquals(1, sink.events.size());

        HttpResponse response = NettyTestUtils.checkIsDownstreamMessageEvent(sink.events.poll(), HttpResponse.class);
        assertTrue(HttpTunnelMessageUtils.isRejection(response));
        assertEquals("no tunnel id specified in send data request", HttpTunnelMessageUtils.extractErrorMessage(response));
    }

    private void checkRequestWithUnknownTunnelIdIsRejected(HttpRequest request) {
        mockContext.checking(new Expectations() {{
            one(messageSwitch).isOpenTunnel(UNKNOWN_TUNNEL_ID); will(returnValue(false));
        }});

        Channels.fireMessageReceived(channel, request);
        assertEquals(1, sink.events.size());

        checkResponseIsRejection("tunnel id \"unknownTunnel\" is either closed or does not exist");
    }

    private void checkResponseIsRejection(String errorMessage) {
        HttpResponse response = NettyTestUtils.checkIsDownstreamMessageEvent(sink.events.poll(), HttpResponse.class);
        assertTrue(HttpTunnelMessageUtils.isRejection(response));
        assertEquals(errorMessage, HttpTunnelMessageUtils.extractErrorMessage(response));
    }
}
