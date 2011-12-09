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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelState;
import io.netty.channel.ChannelStateEvent;
import io.netty.channel.Channels;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests HTTP tunnel client channels
 */
public class HttpTunnelClientChannelTest {

    public static final int LOCAL_PORT = 50123;

    /** used to emulate the selection of a random port in response to a bind request
     * on an ephemeral port.
     */
    public static final int OTHER_LOCAL_PORT = 40652;

    public static final InetSocketAddress LOCAL_ADDRESS = InetSocketAddress
            .createUnresolved("localhost", LOCAL_PORT);

    public static final InetSocketAddress LOCAL_ADDRESS_EPHEMERAL_PORT =
            InetSocketAddress.createUnresolved("localhost", 0);

    public static final InetSocketAddress REMOTE_ADDRESS = InetSocketAddress
            .createUnresolved("test.server.com", 12345);

    public static final InetSocketAddress RESOLVED_LOCAL_ADDRESS_IPV4;

    public static final InetSocketAddress RESOLVED_LOCAL_ADDRESS_IPV6;

    public static final InetSocketAddress RESOLVED_LOCAL_ADDRESS_IPV4_EPHEMERAL_PORT;

    public static final InetSocketAddress RESOLVED_LOCAL_ADDRESS_IPV6_EPHEMERAL_PORT;

    public static final InetSocketAddress RESOLVED_LOCAL_ADDRESS_IPV4_SELECTED_PORT;

    public static final InetSocketAddress RESOLVED_LOCAL_ADDRESS_IPV6_SELECTED_PORT;

    static {
        try {
            InetAddress localhostIPV4 =
                    InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 });
            InetAddress localhostIPV6 =
                    InetAddress.getByAddress(new byte[] { 0, 0, 0, 0, 0, 0, 0,
                            0, 0, 0, 0, 0, 0, 0, 0, 1 });
            RESOLVED_LOCAL_ADDRESS_IPV4 =
                    new InetSocketAddress(localhostIPV4, LOCAL_PORT);
            RESOLVED_LOCAL_ADDRESS_IPV6 =
                    new InetSocketAddress(localhostIPV6, LOCAL_PORT);
            RESOLVED_LOCAL_ADDRESS_IPV4_EPHEMERAL_PORT =
                    new InetSocketAddress(localhostIPV4, 0);
            RESOLVED_LOCAL_ADDRESS_IPV6_EPHEMERAL_PORT =
                    new InetSocketAddress(localhostIPV6, 0);
            RESOLVED_LOCAL_ADDRESS_IPV4_SELECTED_PORT =
                    new InetSocketAddress(localhostIPV4, OTHER_LOCAL_PORT);
            RESOLVED_LOCAL_ADDRESS_IPV6_SELECTED_PORT =
                    new InetSocketAddress(localhostIPV6, OTHER_LOCAL_PORT);
        } catch (UnknownHostException e) {
            throw new RuntimeException(
                    "Creation of InetAddresses should not fail when explicitly specified and the correct length",
                    e);
        }
    }

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

        HttpTunnelClientChannelFactory factory =
                new HttpTunnelClientChannelFactory(outboundFactory);
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
        NettyTestUtils.checkIsStateEvent(sendChannelEvent,
                ChannelState.CONNECTED, REMOTE_ADDRESS);

        // once the send channel indicates that it is connected, we should see the tunnel open request
        // being sent
        sendChannel.emulateConnected(LOCAL_ADDRESS, REMOTE_ADDRESS,
                ((ChannelStateEvent) sendChannelEvent).getFuture());
        assertEquals(1, sendSink.events.size());
        ChannelEvent openTunnelRequest = sendSink.events.poll();
        NettyTestUtils.checkIsDownstreamMessageEvent(openTunnelRequest,
                ChannelBuffer.class);
    }

    @Test
    public void testBind_unresolvedAddress() {
        // requesting a binding with an unresolved local address
        // should attempt to bind the send channel with that address unaltered
        // and attempt to bind the poll address with the same host name but
        // an ephemeral port. We emulate a resolved IPV4 address for the bind
        // response.
        checkBinding(LOCAL_ADDRESS, LOCAL_ADDRESS,
                LOCAL_ADDRESS_EPHEMERAL_PORT, RESOLVED_LOCAL_ADDRESS_IPV4,
                RESOLVED_LOCAL_ADDRESS_IPV4_SELECTED_PORT);
    }

    @Test
    public void testBind_resolvedAddress_ipv4() {
        // variant that uses resolved addresses. The bind request
        // for the poll channel should also use a resolved address,
        // built from the provided resolved address.
        checkBinding(RESOLVED_LOCAL_ADDRESS_IPV4, RESOLVED_LOCAL_ADDRESS_IPV4,
                RESOLVED_LOCAL_ADDRESS_IPV4_EPHEMERAL_PORT,
                RESOLVED_LOCAL_ADDRESS_IPV4,
                RESOLVED_LOCAL_ADDRESS_IPV4_SELECTED_PORT);
    }

    @Test
    public void testBind_resolvedAddress_ipv6() {
        // variant that uses a resolved IPV6 address.
        // bind request on the poll channel should use the same
        // IPv6 host, with an ephemeral port.
        checkBinding(RESOLVED_LOCAL_ADDRESS_IPV6, RESOLVED_LOCAL_ADDRESS_IPV6,
                RESOLVED_LOCAL_ADDRESS_IPV6_EPHEMERAL_PORT,
                RESOLVED_LOCAL_ADDRESS_IPV6,
                RESOLVED_LOCAL_ADDRESS_IPV6_SELECTED_PORT);
    }

    private void checkBinding(InetSocketAddress requestedBindAddress,
            InetSocketAddress expectedPollBindRequest,
            InetSocketAddress expectedSendBindRequest,
            InetSocketAddress emulatedPollBindAddress,
            InetSocketAddress emulatedSendBindAddress) {

        ChannelFuture bindFuture = Channels.bind(channel, requestedBindAddress);
        assertFalse(bindFuture.isDone());

        assertEquals(1, sendSink.events.size());
        assertEquals(1, pollSink.events.size());

        ChannelEvent sendChannelEvent = sendSink.events.poll();
        NettyTestUtils.checkIsStateEvent(sendChannelEvent, ChannelState.BOUND,
                expectedPollBindRequest);
        ChannelEvent pollChannelEvent = pollSink.events.poll();
        NettyTestUtils.checkIsStateEvent(pollChannelEvent, ChannelState.BOUND,
                expectedSendBindRequest);

        sendChannel.emulateBound(emulatedPollBindAddress,
                sendChannelEvent.getFuture());
        assertFalse(bindFuture.isDone());
        pollChannel.emulateBound(emulatedSendBindAddress,
                pollChannelEvent.getFuture());
        assertTrue(bindFuture.isDone());
        assertTrue(bindFuture.isSuccess());

        assertEquals(channel.getLocalAddress(), emulatedPollBindAddress);
    }

    @Test
    public void testBind_preResolvedAddress_ipv6() {
        ChannelFuture bindFuture =
                Channels.bind(channel, RESOLVED_LOCAL_ADDRESS_IPV6);
        assertFalse(bindFuture.isDone());

        assertEquals(1, sendSink.events.size());
        assertEquals(1, pollSink.events.size());

        ChannelEvent sendChannelEvent = sendSink.events.poll();
        NettyTestUtils.checkIsStateEvent(sendChannelEvent, ChannelState.BOUND,
                RESOLVED_LOCAL_ADDRESS_IPV6);
        ChannelEvent pollChannelEvent = pollSink.events.poll();
        NettyTestUtils.checkIsStateEvent(pollChannelEvent, ChannelState.BOUND,
                RESOLVED_LOCAL_ADDRESS_IPV6_EPHEMERAL_PORT);

        sendChannel.emulateBound(RESOLVED_LOCAL_ADDRESS_IPV6,
                sendChannelEvent.getFuture());
        assertFalse(bindFuture.isDone());
        pollChannel.emulateBound(RESOLVED_LOCAL_ADDRESS_IPV4_SELECTED_PORT,
                pollChannelEvent.getFuture());
        assertTrue(bindFuture.isDone());
        assertTrue(bindFuture.isSuccess());

        assertEquals(channel.getLocalAddress(), RESOLVED_LOCAL_ADDRESS_IPV6);
    }

    @Test
    public void testBind_sendBindFails() {
        ChannelFuture bindFuture = Channels.bind(channel, LOCAL_ADDRESS);
        assertFalse(bindFuture.isDone());

        Exception bindFailureReason = new Exception("could not bind");
        ((ChannelStateEvent) sendSink.events.poll()).getFuture().setFailure(
                bindFailureReason);
        assertTrue(bindFuture.isDone());
        assertFalse(bindFuture.isSuccess());
        assertSame(bindFailureReason, bindFuture.getCause());
    }

    @Test
    public void testBind_pollBindFails() {
        ChannelFuture bindFuture = Channels.bind(channel, LOCAL_ADDRESS);
        assertFalse(bindFuture.isDone());

        Exception bindFailureReason = new Exception("could not bind");
        ((ChannelStateEvent) pollSink.events.poll()).getFuture().setFailure(
                bindFailureReason);
        assertTrue(bindFuture.isDone());
        assertFalse(bindFuture.isSuccess());
        assertSame(bindFailureReason, bindFuture.getCause());
    }
}
