/*
 * Copyright 2023 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http3;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http3.Http3RequestStreamFrame;
import io.netty.handler.codec.http3.Http3ResponseStreamFrame;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.util.AsciiString;

import static io.netty.handler.codec.http.HttpResponseStatus.SWITCHING_PROTOCOLS;

/**
 * WebTransport handler for processing HTTP/3 CONNECT requests
 */
public class WebTransportHandler extends ChannelInboundHandlerAdapter {
    
    private static final AsciiString WEBTRANSPORT_HANDSHAKE_HEADER = AsciiString.cached("sec-webtransport-http3-draft02");
    private static final AsciiString WEBTRANSPORT_PATH_PREFIX = AsciiString.cached("webtransport://");
    
    private final WebTransportObserver observer;
    private WebTransportSession session;
    
    public WebTransportHandler(WebTransportObserver observer) {
        this.observer = observer;
    }
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        System.out.println("WebTransport channel activated");
    }
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Http3RequestStreamFrame) {
            handleHttpRequest(ctx, (Http3RequestStreamFrame) msg);
        } else if (msg instanceof Http3ResponseStreamFrame) {
            handleHttpResponse(ctx, (Http3ResponseStreamFrame) msg);
        } else if (msg instanceof io.netty.buffer.ByteBuf) {
            if (session != null) {
                session.handleDatagram((io.netty.buffer.ByteBuf) msg);
            }
        } else {
            super.channelRead(ctx, msg);
        }
    }
    
    private void handleHttpRequest(ChannelHandlerContext ctx, Http3RequestStreamFrame request) {
        // Check if it's a CONNECT request
        if (request.method() != HttpMethod.CONNECT) {
            ctx.close();
            return;
        }
        
        // Check if path starts with webtransport://
        String path = request.path();
        if (!path.startsWith(WEBTRANSPORT_PATH_PREFIX.toString())) {
            ctx.close();
            return;
        }
        
        // Check WebTransport handshake header
        if (!request.headers().contains(WEBTRANSPORT_HANDSHAKE_HEADER)) {
            ctx.close();
            return;
        }
        
        // Create WebTransport session
        QuicChannel quicChannel = (QuicChannel) ctx.channel();
        session = WebTransportSessionProvider.DEFAULT.createSession(quicChannel, observer);
        
        // Send 200 response
        Http3ResponseStreamFrame response = new DefaultHttp3ResponseStreamFrame(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, new DefaultHttp3Headers());
        response.headers().add(WEBTRANSPORT_HANDSHAKE_HEADER, "1");
        ctx.writeAndFlush(response);
        
        // Notify observer session established
        observer.onSessionEstablished(session);
    }
    
    private void handleHttpResponse(ChannelHandlerContext ctx, Http3ResponseStreamFrame response) {
        // Check if response is successful
        if (response.status() == HttpResponseStatus.OK) {
            // Create WebTransport session
            QuicChannel quicChannel = (QuicChannel) ctx.channel();
            session = WebTransportSessionProvider.DEFAULT.createSession(quicChannel, observer);
            
            // Notify observer session established
            observer.onSessionEstablished(session);
        } else {
            ctx.close();
        }
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (session != null) {
            observer.onError(session, cause);
        }
        ctx.close();
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (session != null) {
            observer.onSessionClosed(session, null);
            session.close();
        }
        super.channelInactive(ctx);
    }
}