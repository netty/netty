/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.channel.socket.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.InetSocketAddress;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelState;
import io.netty.channel.Channels;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests an accepted server channel request dispatch
 */
@RunWith(JMock.class)
public class AcceptedServerChannelRequestDispatchTest {

    private static final String HOST = "test.server.com";

    private static final String KNOWN_TUNNEL_ID = "1";

    protected static final String UNKNOWN_TUNNEL_ID = "unknownTunnel";

    JUnit4Mockery mockContext = new JUnit4Mockery();

    private AcceptedServerChannelRequestDispatch handler;

    FakeSocketChannel channel;

    private FakeChannelSink sink;

    ServerMessageSwitchUpstreamInterface messageSwitch;

    @Before
    public void setUp() throws Exception {
        ChannelPipeline pipeline = Channels.pipeline();
        messageSwitch =
                mockContext.mock(ServerMessageSwitchUpstreamInterface.class);
        handler = new AcceptedServerChannelRequestDispatch(messageSwitch);
        pipeline.addLast(AcceptedServerChannelRequestDispatch.NAME, handler);
        sink = new FakeChannelSink();
        channel = new FakeSocketChannel(null, null, pipeline, sink);
        channel.remoteAddress =
                InetSocketAddress.createUnresolved("test.client.com", 51231);

        mockContext.checking(new Expectations() {
            {
                ignoring(messageSwitch).isOpenTunnel(KNOWN_TUNNEL_ID);
                will(returnValue(true));
            }
        });
    }

    @Test
    public void testTunnelOpenRequest() {
        mockContext.checking(new Expectations() {
            {
                one(messageSwitch).createTunnel(channel.remoteAddress);
                will(returnValue(KNOWN_TUNNEL_ID));
            }
        });

        Channels.fireMessageReceived(channel,
                HttpTunnelMessageUtils.createOpenTunnelRequest(HOST));
        assertEquals(1, sink.events.size());
        HttpResponse response =
                NettyTestUtils.checkIsDownstreamMessageEvent(
                        sink.events.poll(), HttpResponse.class);
        assertTrue(HttpTunnelMessageUtils.isTunnelOpenResponse(response));
    }

    @Test
    public void testTunnelCloseRequest() {
        mockContext.checking(new Expectations() {
            {
                one(messageSwitch).clientCloseTunnel(KNOWN_TUNNEL_ID);
            }
        });

        HttpRequest request =
                HttpTunnelMessageUtils.createCloseTunnelRequest(HOST,
                        KNOWN_TUNNEL_ID);
        Channels.fireMessageReceived(channel, request);
        assertEquals(1, sink.events.size());
        ChannelEvent responseEvent = sink.events.poll();
        HttpResponse response =
                NettyTestUtils.checkIsDownstreamMessageEvent(responseEvent,
                        HttpResponse.class);
        assertTrue(HttpTunnelMessageUtils.isTunnelCloseResponse(response));
        checkClosesAfterWrite(responseEvent);
    }

    @Test
    public void testTunnelCloseRequestWithoutTunnelIdRejected() {
        HttpRequest request =
                HttpTunnelMessageUtils.createCloseTunnelRequest(HOST, null);
        checkRequestWithoutTunnelIdIsRejected(request);
    }

    @Test
    public void testTunnelCloseRequestWithUnknownTunnelId() {
        HttpRequest request =
                HttpTunnelMessageUtils.createCloseTunnelRequest(HOST,
                        UNKNOWN_TUNNEL_ID);
        checkRequestWithUnknownTunnelIdIsRejected(request);
    }

    @Test
    public void testSendDataRequest() {
        final ChannelBuffer expectedData = ChannelBuffers.dynamicBuffer();
        expectedData.writeLong(1234L);
        mockContext.checking(new Expectations() {
            {
                one(messageSwitch).routeInboundData(KNOWN_TUNNEL_ID,
                        expectedData);
            }
        });

        HttpRequest request =
                HttpTunnelMessageUtils.createSendDataRequest(HOST,
                        KNOWN_TUNNEL_ID, expectedData);
        Channels.fireMessageReceived(channel, request);

        assertEquals(1, sink.events.size());
        HttpResponse response =
                NettyTestUtils.checkIsDownstreamMessageEvent(
                        sink.events.poll(), HttpResponse.class);
        assertTrue(HttpTunnelMessageUtils.isOKResponse(response));
    }

    @Test
    public void testSendDataRequestWithNoContentRejected() {
        HttpRequest request =
                HttpTunnelMessageUtils.createSendDataRequest(HOST,
                        KNOWN_TUNNEL_ID, ChannelBuffers.dynamicBuffer());
        Channels.fireMessageReceived(channel, request);

        assertEquals(1, sink.events.size());
        checkResponseIsRejection("Send data requests must contain data");
    }

    @Test
    public void testSendDataRequestForUnknownTunnelIdRejected() {
        HttpRequest request =
                HttpTunnelMessageUtils.createSendDataRequest(HOST,
                        UNKNOWN_TUNNEL_ID, ChannelBuffers.dynamicBuffer());
        checkRequestWithUnknownTunnelIdIsRejected(request);
    }

    @Test
    public void testSendDataRequestWithoutTunnelIdRejected() {
        HttpRequest request =
                HttpTunnelMessageUtils.createSendDataRequest(HOST, null,
                        ChannelBuffers.dynamicBuffer());
        checkRequestWithoutTunnelIdIsRejected(request);
    }

    @Test
    public void testReceiveDataRequest() {
        mockContext.checking(new Expectations() {
            {
                one(messageSwitch).pollOutboundData(KNOWN_TUNNEL_ID, channel);
            }
        });
        HttpRequest request =
                HttpTunnelMessageUtils.createReceiveDataRequest(HOST,
                        KNOWN_TUNNEL_ID);
        Channels.fireMessageReceived(channel, request);
    }

    @Test
    public void testReceiveDataRequestWithoutTunnelIdRejected() {
        HttpRequest request =
                HttpTunnelMessageUtils.createReceiveDataRequest(HOST, null);
        checkRequestWithoutTunnelIdIsRejected(request);
    }

    @Test
    public void testReceiveDataRequestForUnknownTunnelIdRejected() {
        HttpRequest request =
                HttpTunnelMessageUtils.createReceiveDataRequest(HOST,
                        UNKNOWN_TUNNEL_ID);
        checkRequestWithUnknownTunnelIdIsRejected(request);
    }

    private void checkRequestWithoutTunnelIdIsRejected(HttpRequest request) {
        Channels.fireMessageReceived(channel, request);
        assertEquals(1, sink.events.size());
        ChannelEvent responseEvent =
                checkResponseIsRejection("no tunnel id specified in request");
        checkClosesAfterWrite(responseEvent);
    }

    private void checkRequestWithUnknownTunnelIdIsRejected(HttpRequest request) {
        mockContext.checking(new Expectations() {
            {
                one(messageSwitch).isOpenTunnel(UNKNOWN_TUNNEL_ID);
                will(returnValue(false));
            }
        });

        Channels.fireMessageReceived(channel, request);
        assertEquals(1, sink.events.size());
        ChannelEvent responseEvent =
                checkResponseIsRejection("specified tunnel is either closed or does not exist");
        checkClosesAfterWrite(responseEvent);
    }

    private ChannelEvent checkResponseIsRejection(String errorMessage) {
        ChannelEvent responseEvent = sink.events.poll();

        HttpResponse response =
                NettyTestUtils.checkIsDownstreamMessageEvent(responseEvent,
                        HttpResponse.class);
        assertTrue(HttpTunnelMessageUtils.isRejection(response));
        assertEquals(errorMessage,
                HttpTunnelMessageUtils.extractErrorMessage(response));

        return responseEvent;
    }

    private void checkClosesAfterWrite(ChannelEvent responseEvent) {
        responseEvent.getFuture().setSuccess();
        assertEquals(1, sink.events.size());
        NettyTestUtils.checkIsStateEvent(sink.events.poll(), ChannelState.OPEN,
                false);
    }
}
