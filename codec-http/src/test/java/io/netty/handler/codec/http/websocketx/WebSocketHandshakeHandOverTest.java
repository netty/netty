/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http.websocketx;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler.ClientHandshakeStateEvent;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler.ServerHandshakeStateEvent;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.*;

public class WebSocketHandshakeHandOverTest {

    private boolean serverReceivedHandshake;
    private boolean clientReceivedHandshake;
    private boolean clientReceivedMessage;

    @Before
    public void setUp() {
        serverReceivedHandshake = false;
        clientReceivedHandshake = false;
        clientReceivedMessage = false;
    }

    @Test
    public void testHandover() throws Exception {
        EmbeddedChannel serverChannel = createServerChannel(new SimpleChannelInboundHandler<Object>() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                if (evt == ServerHandshakeStateEvent.HANDSHAKE_COMPLETE) {
                    serverReceivedHandshake = true;
                    // immediatly send a message to the client on connect
                    ctx.writeAndFlush(new TextWebSocketFrame("abc"));
                }
            }
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            }
        });

        EmbeddedChannel clientChannel = createClientChannel(new SimpleChannelInboundHandler<Object>() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                if (evt == ClientHandshakeStateEvent.HANDSHAKE_COMPLETE) {
                    clientReceivedHandshake = true;
                }
            }
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (msg instanceof TextWebSocketFrame) {
                    clientReceivedMessage = true;
                }
            }
        });

        // Transfer the handshake from the client to the server
        transferAllDataWithMerge(clientChannel, serverChannel);
        assertTrue(serverReceivedHandshake);

        // Transfer the handshake response and the websocket message to the client
        transferAllDataWithMerge(serverChannel, clientChannel);
        assertTrue(clientReceivedHandshake);
        assertTrue(clientReceivedMessage);
    }

    /**
     * Transfers all pending data from the source channel into the destination channel.<br>
     * Merges all data into a single buffer before transmission into the destination.
     * @param srcChannel The source channel
     * @param dstChannel The destination channel
     */
    private static void transferAllDataWithMerge(EmbeddedChannel srcChannel, EmbeddedChannel dstChannel)  {
        ByteBuf mergedBuffer = null;
        for (;;) {
            Object srcData = srcChannel.readOutbound();

            if (srcData != null) {
                assertTrue(srcData instanceof ByteBuf);
                ByteBuf srcBuf = (ByteBuf) srcData;
                try {
                    if (mergedBuffer == null) {
                        mergedBuffer = Unpooled.buffer();
                    }
                    mergedBuffer.writeBytes(srcBuf);
                } finally {
                    srcBuf.release();
                }
            } else {
                break;
            }
        }

        if (mergedBuffer != null) {
            dstChannel.writeInbound(mergedBuffer);
        }
    }

    private static EmbeddedChannel createClientChannel(ChannelHandler handler) throws Exception {
        return new EmbeddedChannel(
                new HttpClientCodec(),
                new HttpObjectAggregator(8192),
                new WebSocketClientProtocolHandler(new URI("ws://localhost:1234/test"),
                                                   WebSocketVersion.V13, null,
                                                   false, null, 65536),
                handler);
    }

    private static EmbeddedChannel createServerChannel(ChannelHandler handler) {
        return new EmbeddedChannel(
                new HttpServerCodec(),
                new HttpObjectAggregator(8192),
                new WebSocketServerProtocolHandler("/test", null, false),
                handler);
    }
}
