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

import static org.junit.Assert.*;

import java.net.InetSocketAddress;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.Channels;
import io.netty.channel.socket.http.ServerMessageSwitchUpstreamInterface.TunnelStatus;
import io.netty.handler.codec.http.HttpResponse;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests server message switching
 */
@RunWith(JMock.class)
public class ServerMessageSwitchTest {

    public static final InetSocketAddress REMOTE_ADDRESS = InetSocketAddress
            .createUnresolved("test.client.com", 52354);

    private final JUnit4Mockery mockContext = new JUnit4Mockery();

    private ServerMessageSwitch messageSwitch;

    HttpTunnelAcceptedChannelFactory newChannelFactory;

    private FakeChannelSink responseCatcher;

    private FakeSocketChannel htunChannel;

    private FakeSocketChannel requesterChannel;

    HttpTunnelAcceptedChannelReceiver htunAcceptedChannel;

    @Before
    public void setUp() throws Exception {
        newChannelFactory =
                mockContext.mock(HttpTunnelAcceptedChannelFactory.class);
        messageSwitch = new ServerMessageSwitch(newChannelFactory);

        htunAcceptedChannel =
                mockContext.mock(HttpTunnelAcceptedChannelReceiver.class);
        createRequesterChannel();

        mockContext.checking(new Expectations() {
            {
                one(newChannelFactory).newChannel(with(any(String.class)),
                        with(equal(REMOTE_ADDRESS)));
                will(returnValue(htunAcceptedChannel));
                ignoring(newChannelFactory).generateTunnelId();
                will(returnValue("TEST_TUNNEL"));
            }
        });
    }

    private FakeSocketChannel createRequesterChannel() {
        ChannelPipeline requesterChannelPipeline = Channels.pipeline();
        responseCatcher = new FakeChannelSink();
        requesterChannel =
                new FakeSocketChannel(null, null, requesterChannelPipeline,
                        responseCatcher);
        responseCatcher.events.clear();

        return requesterChannel;
    }

    @Test
    public void testRouteInboundData() {
        final ChannelBuffer inboundData = ChannelBuffers.dynamicBuffer();
        inboundData.writeLong(1234L);

        mockContext.checking(new Expectations() {
            {
                one(htunAcceptedChannel).dataReceived(with(same(inboundData)));
            }
        });

        String tunnelId = messageSwitch.createTunnel(REMOTE_ADDRESS);
        messageSwitch.routeInboundData(tunnelId, inboundData);
        mockContext.assertIsSatisfied();
    }

    @Test
    public void testRouteOutboundData_onPoll() {
        ChannelBuffer outboundData = ChannelBuffers.dynamicBuffer();
        outboundData.writeLong(1234L);

        String tunnelId = messageSwitch.createTunnel(REMOTE_ADDRESS);
        messageSwitch.routeOutboundData(tunnelId, outboundData,
                Channels.future(htunChannel));
        messageSwitch.pollOutboundData(tunnelId, requesterChannel);

        assertEquals(1, responseCatcher.events.size());
        HttpResponse response =
                NettyTestUtils.checkIsDownstreamMessageEvent(
                        responseCatcher.events.poll(), HttpResponse.class);
        NettyTestUtils.assertEquals(outboundData, response.getContent());
    }

    @Test
    public void testRouteOutboundData_withDanglingRequest() {
        String tunnelId = messageSwitch.createTunnel(REMOTE_ADDRESS);
        messageSwitch.pollOutboundData(tunnelId, requesterChannel);
        assertEquals(0, responseCatcher.events.size());

        ChannelBuffer outboundData = ChannelBuffers.dynamicBuffer();
        outboundData.writeLong(1234L);

        messageSwitch.routeOutboundData(tunnelId, outboundData,
                Channels.future(htunChannel));
        assertEquals(1, responseCatcher.events.size());
        HttpResponse response =
                NettyTestUtils.checkIsDownstreamMessageEvent(
                        responseCatcher.events.poll(), HttpResponse.class);
        NettyTestUtils.assertEquals(outboundData, response.getContent());
    }

    @Test
    public void testCloseTunnel() {
        String tunnelId = messageSwitch.createTunnel(REMOTE_ADDRESS);
        messageSwitch.serverCloseTunnel(tunnelId);
        assertEquals(
                TunnelStatus.CLOSED,
                messageSwitch.routeInboundData(tunnelId,
                        ChannelBuffers.dynamicBuffer()));
    }

    /* TODO: require tests that check the various permutations of a client sending or polling
       data after the server has closed the connection */

    /* TODO: require tests that check what happens when a client closes a connection */

    @Test
    public void testRouteInboundDataIgnoredAfterClose() {
        ChannelBuffer data = NettyTestUtils.createData(1234L);
        String tunnelId = messageSwitch.createTunnel(REMOTE_ADDRESS);
        messageSwitch.serverCloseTunnel(tunnelId);

        mockContext.checking(new Expectations() {
            {
                never(htunAcceptedChannel).dataReceived(
                        with(any(ChannelBuffer.class)));
            }
        });

        messageSwitch.routeInboundData(tunnelId, data);
        mockContext.assertIsSatisfied();
    }

    @Test
    public void testRouteOutboundDataIgnoredAfterClose() {
        ChannelBuffer data = NettyTestUtils.createData(1234L);
        String tunnelId = messageSwitch.createTunnel(REMOTE_ADDRESS);
        messageSwitch.serverCloseTunnel(tunnelId);
        messageSwitch.routeOutboundData(tunnelId, data,
                Channels.future(htunChannel));
        assertEquals(0, responseCatcher.events.size());
    }
}
