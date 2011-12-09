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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.Channels;
import io.netty.channel.DownstreamMessageEvent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests HTTP tunnel client polling
 */
public class HttpTunnelClientPollHandlerTest {

    private static final String TUNNEL_ID = "1";

    private static final InetSocketAddress SERVER_ADDRESS = createAddress(
            new byte[] { 10, 0, 0, 3 }, 12345);

    private static final InetSocketAddress PROXY_ADDRESS = createAddress(
            new byte[] { 10, 0, 0, 2 }, 8888);

    private static final InetSocketAddress LOCAL_ADDRESS = createAddress(
            new byte[] { 10, 0, 0, 1 }, 54321);

    private FakeSocketChannel channel;

    private FakeChannelSink sink;

    private HttpTunnelClientPollHandler handler;

    private MockChannelStateListener listener;

    private static InetSocketAddress createAddress(byte[] addr, int port) {
        try {
            return new InetSocketAddress(InetAddress.getByAddress(addr), port);
        } catch (UnknownHostException e) {
            throw new RuntimeException("Bad address in test");
        }
    }

    @Before
    public void setUp() throws Exception {
        sink = new FakeChannelSink();

        ChannelPipeline pipeline = Channels.pipeline();
        listener = new MockChannelStateListener();
        listener.serverHostName =
                HttpTunnelMessageUtils.convertToHostString(SERVER_ADDRESS);
        handler = new HttpTunnelClientPollHandler(listener);
        handler.setTunnelId(TUNNEL_ID);
        pipeline.addLast(HttpTunnelClientPollHandler.NAME, handler);

        channel = new FakeSocketChannel(null, null, pipeline, sink);
        channel.remoteAddress = PROXY_ADDRESS;
        channel.localAddress = LOCAL_ADDRESS;
    }

    @Test
    public void testSendsRequestOnConnect() {
        Channels.fireChannelConnected(channel, PROXY_ADDRESS);
        assertEquals(1, sink.events.size());
        HttpRequest request =
                checkIsMessageEventContainingHttpRequest(sink.events.poll());
        assertTrue(HttpTunnelMessageUtils.isServerToClientRequest(request));
        assertTrue(HttpTunnelMessageUtils.checkHost(request, SERVER_ADDRESS));
        assertTrue(listener.fullyEstablished);
    }

    @Test
    public void testSendsReceivedDataSentUpstream() {
        HttpResponse response =
                HttpTunnelMessageUtils.createRecvDataResponse(NettyTestUtils
                        .createData(1234L));
        Channels.fireMessageReceived(channel, response);
        assertEquals(1, listener.messages.size());
        assertEquals(1234L, listener.messages.get(0).readLong());
    }

    @Test
    public void testSendsAnotherRequestAfterResponse() {
        HttpResponse response =
                HttpTunnelMessageUtils.createRecvDataResponse(NettyTestUtils
                        .createData(1234L));
        Channels.fireMessageReceived(channel, response);
        assertEquals(1, sink.events.size());
        checkIsMessageEventContainingHttpRequest(sink.events.poll());
    }

    private HttpRequest checkIsMessageEventContainingHttpRequest(
            ChannelEvent event) {
        assertTrue(event instanceof DownstreamMessageEvent);
        DownstreamMessageEvent messageEvent = (DownstreamMessageEvent) event;
        assertTrue(messageEvent.getMessage() instanceof HttpRequest);
        return (HttpRequest) messageEvent.getMessage();
    }
}
