/*
 * Copyright 2012 The Netty Project
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

import io.netty.buffer.MessageBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.channel.ChannelOutboundMessageHandlerAdapter;
import io.netty.channel.embedded.EmbeddedMessageChannel;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseEncoder;
import org.junit.Test;

import static io.netty.handler.codec.http.HttpHeaders.Values.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;
import static org.junit.Assert.*;

public class WebSocketServerProtocolHandlerTest {

    @Test
    public void testHttpUpgradeRequest() throws Exception {
        EmbeddedMessageChannel ch = createChannel(new MockOutboundHandler());
        ChannelHandlerContext handshakerCtx = ch.pipeline().context(WebSocketServerProtocolHandshakeHandler.class);
        
        writeUpgradeRequest(ch);
        
        assertEquals(SWITCHING_PROTOCOLS, ((HttpResponse) ch.outboundMessageBuffer().poll()).getStatus());
        assertNotNull(WebSocketServerProtocolHandler.getHandshaker(handshakerCtx));
    }
    
    @Test 
    public void testSubsequentHttpRequestsAfterUpgradeShouldReturn403() throws Exception {
        EmbeddedMessageChannel ch = createChannel(new MockOutboundHandler());
        
        writeUpgradeRequest(ch);
        assertEquals(SWITCHING_PROTOCOLS, ((HttpResponse) ch.outboundMessageBuffer().poll()).getStatus());
        
        ch.writeInbound(new DefaultHttpRequest(HTTP_1_1, HttpMethod.GET, "/test"));
        assertEquals(FORBIDDEN, ((HttpResponse) ch.outboundMessageBuffer().poll()).getStatus());
    }
    
    @Test
    public void testHttpUpgradeRequestInvalidUpgradeHeader() {
        EmbeddedMessageChannel ch = createChannel();
        HttpRequest httpRequest = new WebSocketRequestBuilder().httpVersion(HTTP_1_1)
                .method(HttpMethod.GET)
                .uri("/test")
                .connection("Upgrade")
                .version00()
                .upgrade("BogusSocket")
                .build();
        
        ch.writeInbound(httpRequest);
        
        HttpResponse response = getHttpResponse(ch);
        assertEquals(BAD_REQUEST, response.getStatus());
        assertEquals("not a WebSocket handshake request: missing upgrade", getResponseMessage(response));
        
    }
    
    @Test
    public void testHttpUpgradeRequestMissingWSKeyHeader() {
        EmbeddedMessageChannel ch = createChannel();
        HttpRequest httpRequest = new WebSocketRequestBuilder().httpVersion(HTTP_1_1)
                .method(HttpMethod.GET)
                .uri("/test")
                .key(null)
                .connection("Upgrade")
                .upgrade(WEBSOCKET.toLowerCase())
                .version13()
                .build();
        
        ch.writeInbound(httpRequest);
        
        HttpResponse response = getHttpResponse(ch);
        assertEquals(BAD_REQUEST, response.getStatus());
        assertEquals("not a WebSocket request: missing key", getResponseMessage(response));
    }
    
    @Test
    public void testHandleTextFrame() {
        CustomTextFrameHandler customTextFrameHandler = new CustomTextFrameHandler();
        EmbeddedMessageChannel ch = createChannel(customTextFrameHandler);
        writeUpgradeRequest(ch);
        // Removing the HttpRequestDecoder as we are writing a TextWebSocketFrame so decoding is not neccessary.
        ch.pipeline().remove(HttpRequestDecoder.class);
        
        ch.writeInbound(new TextWebSocketFrame("payload"));
        
        assertEquals("processed: payload", customTextFrameHandler.getContent());
    }
    
    private static EmbeddedMessageChannel createChannel() {
        return createChannel(null);
    }
    
    private static EmbeddedMessageChannel createChannel(ChannelHandler handler) {
        return new EmbeddedMessageChannel(
                new WebSocketServerProtocolHandler("/test", null, false),
                new HttpRequestDecoder(),
                new HttpResponseEncoder(),
                new MockOutboundHandler(),
                handler);
    }
    
    private static void writeUpgradeRequest(EmbeddedMessageChannel ch) {
        ch.writeInbound(WebSocketRequestBuilder.sucessful());
    }

    private static String getResponseMessage(HttpResponse response) {
        return new String(response.getContent().array());
    }

    private static HttpResponse getHttpResponse(EmbeddedMessageChannel ch) {
        MessageBuf<Object> outbound = ch.pipeline().context(MockOutboundHandler.class).outboundMessageBuffer();
        return (HttpResponse) outbound.poll();
    }

    private static class MockOutboundHandler extends ChannelOutboundMessageHandlerAdapter<Object> {
        @Override
        public void flush(ChannelHandlerContext ctx, ChannelFuture future) throws Exception {
            //NoOp
        }
    }
    
    private static class CustomTextFrameHandler extends ChannelInboundMessageHandlerAdapter<TextWebSocketFrame> {
        private String content;

        @Override
        public void messageReceived(ChannelHandlerContext ctx, TextWebSocketFrame msg) throws Exception {
            content = "processed: " + msg.getText();
        }
        
        public String getContent() {
            return content;
        }

    }
    
}
