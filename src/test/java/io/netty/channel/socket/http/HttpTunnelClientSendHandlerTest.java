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
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelState;
import io.netty.channel.Channels;
import io.netty.channel.DownstreamMessageEvent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests HTTP tunnel client sending
 */
public class HttpTunnelClientSendHandlerTest {

    private static final InetSocketAddress SERVER_ADDRESS = createAddress(
            new byte[] { 10, 0, 0, 3 }, 12345);

    private static final InetSocketAddress PROXY_ADDRESS = createAddress(
            new byte[] { 10, 0, 0, 2 }, 8888);

    private static final InetSocketAddress LOCAL_ADDRESS = createAddress(
            new byte[] { 10, 0, 0, 1 }, 54321);

    private FakeSocketChannel channel;

    private FakeChannelSink sink;

    private HttpTunnelClientSendHandler handler;

    private MockChannelStateListener listener;

    @Before
    public void setUp() {
        sink = new FakeChannelSink();
        ChannelPipeline pipeline = Channels.pipeline();
        listener = new MockChannelStateListener();
        listener.serverHostName =
                HttpTunnelMessageUtils.convertToHostString(SERVER_ADDRESS);
        handler = new HttpTunnelClientSendHandler(listener);
        pipeline.addLast(HttpTunnelClientSendHandler.NAME, handler);
        channel = new FakeSocketChannel(null, null, pipeline, sink);
        channel.remoteAddress = PROXY_ADDRESS;
        channel.localAddress = LOCAL_ADDRESS;
    }

    private static InetSocketAddress createAddress(byte[] addr, int port) {
        try {
            return new InetSocketAddress(InetAddress.getByAddress(addr), port);
        } catch (UnknownHostException e) {
            throw new RuntimeException("Bad address in test");
        }
    }

    @Test
    public void testSendsTunnelOpen() throws Exception {
        Channels.fireChannelConnected(channel, PROXY_ADDRESS);
        assertEquals(1, sink.events.size());
        HttpRequest request =
                NettyTestUtils.checkIsDownstreamMessageEvent(
                        sink.events.poll(), HttpRequest.class);
        assertTrue(HttpTunnelMessageUtils.isOpenTunnelRequest(request));
        assertTrue(HttpTunnelMessageUtils.checkHost(request, SERVER_ADDRESS));
    }

    @Test
    public void testStoresTunnelId() throws Exception {
        emulateConnect();
        Channels.fireMessageReceived(channel,
                HttpTunnelMessageUtils.createTunnelOpenResponse("newTunnel"));
        assertEquals("newTunnel", handler.getTunnelId());
        assertEquals("newTunnel", listener.tunnelId);
    }

    @Test
    public void testSendData() {
        emulateConnectAndOpen();
        channel.write(NettyTestUtils.createData(1234L));
        assertEquals(1, sink.events.size());
        ChannelEvent sentEvent = sink.events.poll();
        checkIsSendDataRequestWithData(sentEvent,
                NettyTestUtils.createData(1234L));
    }

    @Test
    public void testWillNotSendDataUntilTunnelIdSet() {
        emulateConnect();
        channel.write(NettyTestUtils.createData(1234L));

        assertEquals(0, sink.events.size());

        Channels.fireChannelConnected(channel, PROXY_ADDRESS);
        assertEquals(1, sink.events.size());
    }

    @Test
    public void testOnlyOneRequestAtATime() {
        emulateConnectAndOpen();

        channel.write(NettyTestUtils.createData(1234L));
        assertEquals(1, sink.events.size());
        checkIsSendDataRequestWithData(sink.events.poll(),
                NettyTestUtils.createData(1234L));

        channel.write(NettyTestUtils.createData(5678L));
        assertEquals(0, sink.events.size());

        Channels.fireMessageReceived(channel,
                HttpTunnelMessageUtils.createSendDataResponse());
        assertEquals(1, sink.events.size());
        checkIsSendDataRequestWithData(sink.events.poll(),
                NettyTestUtils.createData(5678L));
    }

    @Test
    public void testDisconnect() {
        emulateConnectAndOpen();

        channel.write(NettyTestUtils.createData(1234L));
        assertEquals(1, sink.events.size());
        checkIsSendDataRequestWithData(sink.events.poll(),
                NettyTestUtils.createData(1234L));

        channel.disconnect();
        Channels.fireMessageReceived(channel,
                HttpTunnelMessageUtils.createSendDataResponse());
        assertEquals(1, sink.events.size());

        HttpRequest request =
                NettyTestUtils.checkIsDownstreamMessageEvent(
                        sink.events.poll(), HttpRequest.class);
        assertTrue(HttpTunnelMessageUtils.isCloseTunnelRequest(request));
        assertEquals("newTunnel",
                HttpTunnelMessageUtils.extractTunnelId(request));
        Channels.fireMessageReceived(channel,
                HttpTunnelMessageUtils.createTunnelCloseResponse());
        assertEquals(1, sink.events.size());
        NettyTestUtils.checkIsStateEvent(sink.events.poll(),
                ChannelState.CONNECTED, null);
    }

    @Test
    public void testClose() {
        emulateConnectAndOpen();

        channel.close();
        assertEquals(1, sink.events.size());
        HttpRequest request =
                NettyTestUtils.checkIsDownstreamMessageEvent(
                        sink.events.poll(), HttpRequest.class);
        assertTrue(HttpTunnelMessageUtils.isCloseTunnelRequest(request));
        assertEquals("newTunnel",
                HttpTunnelMessageUtils.extractTunnelId(request));
        Channels.fireMessageReceived(channel,
                HttpTunnelMessageUtils.createTunnelCloseResponse());
        assertEquals(1, sink.events.size());
        NettyTestUtils.checkIsStateEvent(sink.events.poll(), ChannelState.OPEN,
                false);
    }

    @Test
    public void testWritesAfterCloseAreRejected() {
        emulateConnectAndOpen();

        channel.close();
        assertFalse(channel.write(NettyTestUtils.createData(1234L)).isSuccess());
    }

    private void checkIsSendDataRequestWithData(ChannelEvent event,
            ChannelBuffer data) {
        assertTrue(event instanceof DownstreamMessageEvent);
        DownstreamMessageEvent messageEvent = (DownstreamMessageEvent) event;
        assertTrue(messageEvent.getMessage() instanceof HttpRequest);
        HttpRequest request = (HttpRequest) messageEvent.getMessage();
        assertTrue(HttpTunnelMessageUtils.isSendDataRequest(request));
        assertEquals(data.readableBytes(),
                HttpHeaders.getContentLength(request));

        ChannelBuffer content = request.getContent();
        NettyTestUtils.assertEquals(data, content);
    }

    private void emulateConnect() {
        channel.emulateConnected(LOCAL_ADDRESS, PROXY_ADDRESS, null);
        sink.events.clear();
    }

    private void emulateConnectAndOpen() {
        emulateConnect();
        Channels.fireMessageReceived(channel,
                HttpTunnelMessageUtils.createTunnelOpenResponse("newTunnel"));

        sink.events.clear();
    }
}
