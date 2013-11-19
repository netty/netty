/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.sockjs.transport;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.ORIGIN;
import static io.netty.handler.codec.http.HttpHeaders.Names.SEC_WEBSOCKET_KEY1;
import static io.netty.handler.codec.http.HttpHeaders.Names.SEC_WEBSOCKET_KEY2;
import static io.netty.handler.codec.http.HttpHeaders.Names.SEC_WEBSOCKET_LOCATION;
import static io.netty.handler.codec.http.HttpHeaders.Names.SEC_WEBSOCKET_ORIGIN;
import static io.netty.handler.codec.http.HttpHeaders.Names.SEC_WEBSOCKET_PROTOCOL;
import static io.netty.handler.codec.http.HttpHeaders.Names.WEBSOCKET_LOCATION;
import static io.netty.handler.codec.http.HttpHeaders.Names.WEBSOCKET_ORIGIN;
import static io.netty.handler.codec.http.HttpHeaders.Names.WEBSOCKET_PROTOCOL;
import static io.netty.handler.codec.http.HttpHeaders.Values.WEBSOCKET;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpHeaders.Values;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpObjectDecoder;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker00;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

/**
 * An extension of {@link WebSocketServerHandshaker00} which handles Hixie76
 * upgrade requests that plays nicely with HAProxy.
 *
 * When HAProxy sends an WebSocket Hixie 76 upgrade reqeust will first send the request headers,
 * after receiveing the response, it will then send the nouce.
 *
 * This class will extract the 'SEC_WEBSOCKET_KEY1' and 'SEC_WEBSOCKET_KEY2' from the first request
 * and later use it for the actual handshake.
 *
 * Note that currently this does not work as desired with unless the HTTP request header 'Content-Lenght'
 * has been set. Netty's {@link HttpObjectDecoder} uses
 * {@link HttpHeaders#getContentLength(HttpMessage, long)} which will set the lenght
 * of Hixie 76 request to '8'. But there will be no body in the first request and therefor the message will be
 * dropped.
 */
public class WebSocketHAProxyHandshaker extends WebSocketServerHandshaker00 {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(WebSocketHAProxyHandshaker.class);
    private static final ThreadLocal<MessageDigest> MESSAGE_DIGEST = new ThreadLocal<MessageDigest>() {
        @Override
        protected MessageDigest initialValue() {
            try {
                return MessageDigest.getInstance("MD5");
            } catch (final NoSuchAlgorithmException e) {
                throw new IllegalStateException("Could not create a new MD5 instance", e);
            }
        }
    };

    private static final Pattern BEGINNING_DIGIT = Pattern.compile("[^0-9]");
    private static final Pattern BEGINNING_SPACE = Pattern.compile("[^ ]");
    private String key1;
    private String key2;

    public WebSocketHAProxyHandshaker(final String webSocketURL, final String subprotocols,
            final int maxFramePayloadLength) {
        super(webSocketURL, subprotocols, maxFramePayloadLength);
    }

    @Override
    protected FullHttpResponse newHandshakeResponse(FullHttpRequest req, HttpHeaders headers) {

        // Serve the WebSocket handshake request.
        if (!Values.UPGRADE.equalsIgnoreCase(req.headers().get(CONNECTION))
                || !WEBSOCKET.equalsIgnoreCase(req.headers().get(Names.UPGRADE))) {
            throw new WebSocketHandshakeException("not a WebSocket handshake request: missing upgrade");
        }

        // Hixie 75 does not contain these headers while Hixie 76 does
        boolean isHixie76 = req.headers().contains(SEC_WEBSOCKET_KEY1) && req.headers().contains(SEC_WEBSOCKET_KEY2);

        // Create the WebSocket handshake response.
        FullHttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, new HttpResponseStatus(101,
                isHixie76 ? "WebSocket Protocol Handshake" : "Web Socket Protocol Handshake"));
        if (headers != null) {
            res.headers().add(headers);
        }

        res.headers().add(Names.UPGRADE, WEBSOCKET);
        res.headers().add(CONNECTION, Values.UPGRADE);

        // Fill in the headers and contents depending on handshake getMethod.
        if (isHixie76) {
            // New handshake getMethod with a challenge:
            res.headers().add(SEC_WEBSOCKET_ORIGIN, req.headers().get(ORIGIN));
            res.headers().add(SEC_WEBSOCKET_LOCATION, uri());
            String subprotocols = req.headers().get(SEC_WEBSOCKET_PROTOCOL);
            if (subprotocols != null) {
                String selectedSubprotocol = selectSubprotocol(subprotocols);
                if (selectedSubprotocol == null) {
                    throw new WebSocketHandshakeException("Requested subprotocol(s) not supported: " + subprotocols);
                } else {
                    res.headers().add(SEC_WEBSOCKET_PROTOCOL, selectedSubprotocol);
                }
            }

            // Calculate the answer of the challenge.
            key1 = req.headers().get(SEC_WEBSOCKET_KEY1);
            key2 = req.headers().get(SEC_WEBSOCKET_KEY2);
        } else {
            // Old Hixie 75 handshake getMethod with no challenge:
            res.headers().add(WEBSOCKET_ORIGIN, req.headers().get(ORIGIN));
            res.headers().add(WEBSOCKET_LOCATION, uri());
            String protocol = req.headers().get(WEBSOCKET_PROTOCOL);
            if (protocol != null) {
                res.headers().add(WEBSOCKET_PROTOCOL, selectSubprotocol(protocol));
            }
        }
        return res;
    }

    protected ByteBuf calculateLastKey(final ByteBuf content) {
        final int a = (int) (Long.parseLong(BEGINNING_DIGIT.matcher(key1).replaceAll("")) / BEGINNING_SPACE
                .matcher(key1).replaceAll("").length());
        final int b = (int) (Long.parseLong(BEGINNING_DIGIT.matcher(key2).replaceAll("")) / BEGINNING_SPACE
                .matcher(key2).replaceAll("").length());
        final long c = content.readLong();
        final ByteBuf input = Unpooled.buffer(16);
        input.writeInt(a);
        input.writeInt(b);
        input.writeLong(c);
        final ByteBuf key = Unpooled.buffer().writeBytes(md5(input.array()));
        return key;
    }

    @Override
    public ChannelFuture handshake(Channel channel, FullHttpRequest req) {
        final FullHttpResponse response = newHandshakeResponse(req, null);
        return channel.writeAndFlush(response).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    ChannelPipeline p = future.channel().pipeline();
                    if (p.get(HttpObjectAggregator.class) != null) {
                        p.remove(HttpObjectAggregator.class);
                    }
                    ChannelHandlerContext ctx = p.context(HttpRequestDecoder.class);
                    if (ctx == null) {
                        // this means the user use a HttpServerCodec
                        ctx = p.context(HttpServerCodec.class);
                        if (ctx == null) {
                            throw new IllegalStateException("No HttpDecoder and no HttpServerCodec in the pipeline");
                        }
                        p.addBefore(ctx.name(), "wsencoder", newWebsocketDecoder());
                        p.replace(ctx.name(), "wsdecoder", newWebSocketEncoder());
                    } else {
                        p.remove(HttpRequestDecoder.class);
                        p.remove(HttpResponseEncoder.class);
                    }
                } else {
                    logger.info("Write failed: ", future.cause());
                }
            }
        });
    }

    public void addWsCodec(final ChannelFuture future) {
        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    ChannelPipeline p = future.channel().pipeline();
                    p.addFirst(newWebsocketDecoder());
                    p.addFirst(newWebSocketEncoder());
                } else {
                    logger.info("Write failed: ", future.cause());
                }
            }
        });
    }

    private static byte[] md5(final byte[] data) {
        final MessageDigest md = MESSAGE_DIGEST.get();
        return md.digest(data);
    }

    /**
     * A HAProxy request is a WebSocket Upgrade request with out a 'Sec-WebSocket-Version' and
     * does not have the final handshake key in the body of the request.
     */
    public static boolean isHAProxyReqeust(final FullHttpRequest request) {
        final String version = request.headers().get(Names.SEC_WEBSOCKET_VERSION);
        return version == null && request.content().readableBytes() == 0;
    }

}
