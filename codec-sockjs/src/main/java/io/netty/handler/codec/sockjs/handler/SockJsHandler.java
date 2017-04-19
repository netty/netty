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
package io.netty.handler.codec.sockjs.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AsciiString;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.sockjs.SockJsServiceFactory;
import io.netty.handler.codec.sockjs.SockJsConfig;
import io.netty.handler.codec.sockjs.handler.SockJsPaths.SockJsPath;
import io.netty.handler.codec.sockjs.transport.EventSourceTransport;
import io.netty.handler.codec.sockjs.transport.HtmlFileTransportInbound;
import io.netty.handler.codec.sockjs.transport.HtmlFileTransportOutbound;
import io.netty.handler.codec.sockjs.transport.JsonpPollingTransportInbound;
import io.netty.handler.codec.sockjs.transport.JsonpPollingTransportOutbound;
import io.netty.handler.codec.sockjs.transport.JsonpSendTransport;
import io.netty.handler.codec.sockjs.transport.RawWebSocketTransport;
import io.netty.handler.codec.sockjs.transport.TransportType;
import io.netty.handler.codec.sockjs.transport.WebSocketTransport;
import io.netty.handler.codec.sockjs.transport.XhrPollingTransport;
import io.netty.handler.codec.sockjs.transport.XhrSendTransport;
import io.netty.handler.codec.sockjs.transport.XhrStreamingTransport;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Values.KEEP_ALIVE;
import static io.netty.handler.codec.sockjs.transport.HttpResponseBuilder.CONTENT_TYPE_PLAIN;
import static io.netty.handler.codec.sockjs.transport.HttpResponseBuilder.responseFor;
import static io.netty.handler.codec.sockjs.util.Arguments.checkNotNullAndNotEmpty;
import static java.util.UUID.randomUUID;

/**
 * This handler is the main entry point for SockJS HTTP Request.
 *
 * It is responsible for inspecting the request uri and adding ChannelHandlers for
 * different transport protocols that SockJS support. Once this has been done this
 * handler will be removed from the channel pipeline.
 */
public class SockJsHandler extends ChannelInboundHandlerAdapter {

    private final Map<String, SockJsServiceFactory> factories = new LinkedHashMap<String, SockJsServiceFactory>();
    private static final ConcurrentMap<String, SockJsSession> sessions = new ConcurrentHashMap<String, SockJsSession>();
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SockJsHandler.class);

    /**
     * Sole constructor which takes one or more {@code SockJSServiceFactory}. These factories will
     * later be used by the server to create the SockJS services that will be exposed by this server
     *
     * @param factories one or more {@link SockJsServiceFactory}s.
     */
    public SockJsHandler(final SockJsServiceFactory... factories) {
        checkNotNullAndNotEmpty(factories, "factories");
        for (SockJsServiceFactory factory : factories) {
            this.factories.put(factory.config().prefix(), factory);
        }
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            final FullHttpRequest request = (FullHttpRequest) msg;
            final String path = new QueryStringDecoder(request.uri()).path();
            for (SockJsServiceFactory factory : factories.values()) {
                if (path.startsWith(factory.config().prefix())) {
                    handleService(factory, request, ctx);
                    return;
                }
            }
            writeNotFoundResponse(request, ctx);
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    private static void handleService(final SockJsServiceFactory factory,
                                      final FullHttpRequest request,
                                      final ChannelHandlerContext ctx) throws Exception {
        if (logger.isDebugEnabled()) {
            logger.debug("RequestUri : [{}]", request.uri());
        }
        final SockJsConfig sockJsConfig = factory.config();
        final String path = pathWithoutPrefix(sockJsConfig.prefix(), request);
        if (Greeting.matches(path)) {
            writeResponse(ctx.channel(), request, Greeting.response(request));
            return;
        }
        if (Info.matches(path)) {
            writeResponse(ctx.channel(), request, Info.response(sockJsConfig, request));
            return;
        }
        if (Iframe.matches(path)) {
            writeResponse(ctx.channel(), request, Iframe.response(sockJsConfig, request));
            return;
        }
        if (RawWebSocketTransport.matches(path)) {
            addTransportHandler(new RawWebSocketTransport(sockJsConfig, factory.newService()), ctx);
            ctx.fireChannelRead(request);
            return;
        }
        final SockJsPath sockJsPath = SockJsPaths.parse(path);
        if (sockJsPath.isValid()) {
            handleSession(factory, sockJsConfig, request, ctx, sockJsPath.transport(), sockJsPath.sessionId());
            return;
        }
        writeNotFoundResponse(request, ctx);
    }

    private static void handleSession(final SockJsServiceFactory factory,
                                      final SockJsConfig sockJsConfig,
                                      final FullHttpRequest request,
                                      final ChannelHandlerContext ctx,
                                      final TransportType transportType,
                                      final String sessionId) throws Exception {
        switch (transportType) {
        case XHR:
            addTransportHandler(new XhrPollingTransport(sockJsConfig, request), ctx);
            addSessionHandler(new PollingSessionState(sessions, session(factory, sessionId)), ctx);
            break;
        case JSONP:
            addTransportHandler(new JsonpPollingTransportInbound(), ctx);
            addTransportHandler(new JsonpPollingTransportOutbound(sockJsConfig, request), ctx);
            addSessionHandler(new PollingSessionState(sessions, session(factory, sessionId)), ctx);
            break;
        case XHR_SEND:
            checkSessionExists(sessionId, request);
            addTransportHandler(new XhrSendTransport(sockJsConfig), ctx);
            addSessionHandler(new SendingSessionState(sessions, sessions.get(sessionId)), ctx);
            break;
        case XHR_STREAMING:
            addTransportHandler(new XhrStreamingTransport(sockJsConfig, request), ctx);
            addSessionHandler(new StreamingSessionState(sessions, session(factory, sessionId)), ctx);
            break;
        case EVENTSOURCE:
            addTransportHandler(new EventSourceTransport(sockJsConfig, request), ctx);
            addSessionHandler(new StreamingSessionState(sessions, session(factory, sessionId)), ctx);
            break;
        case HTMLFILE:
            addTransportHandler(new HtmlFileTransportInbound(), ctx);
            addTransportHandler(new HtmlFileTransportOutbound(sockJsConfig, request), ctx);
            addSessionHandler(new StreamingSessionState(sessions, session(factory, sessionId)), ctx);
            break;
        case JSONP_SEND:
            checkSessionExists(sessionId, request);
            addTransportHandler(new JsonpSendTransport(sockJsConfig), ctx);
            addSessionHandler(new SendingSessionState(sessions, sessions.get(sessionId)), ctx);
            break;
        case WEBSOCKET:
            addTransportHandler(new WebSocketTransport(sockJsConfig), ctx);
            addSessionHandler(new WebSocketSessionState(newSession(factory)), ctx);
            break;
        }
        ctx.fireChannelRead(request);
    }

    private static void addTransportHandler(final ChannelHandler transportHandler, final ChannelHandlerContext ctx) {
        ctx.pipeline().addLast(transportHandler);
    }

    private static void addSessionHandler(final SessionState sessionState, final ChannelHandlerContext ctx) {
        ctx.pipeline().addLast(new SessionHandler(sessionState));
    }

    private static void checkSessionExists(final String sessionId, final HttpRequest request)
            throws SessionNotFoundException {
        if (!sessions.containsKey(sessionId)) {
            throw new SessionNotFoundException(sessionId, request);
        }
    }

    private static SockJsSession session(final SockJsServiceFactory factory, final String sessionId) {
        SockJsSession session = sessions.get(sessionId);
        if (session == null) {
            final SockJsSession newSession = new SockJsSession(sessionId, factory.newService());
            session = sessions.putIfAbsent(sessionId, newSession);
            if (session == null) {
                session = newSession;
            }
            logger.debug("Created new session [{}]", sessionId);
        } else {
            logger.debug("Using existing session [{}]", sessionId);
        }
        return session;
    }

    private static SockJsSession newSession(final SockJsServiceFactory factory) {
        return new SockJsSession(randomUUID().toString(), factory.newService());
    }

    private static void writeNotFoundResponse(final HttpRequest request, final ChannelHandlerContext ctx) {
        writeResponse(ctx.channel(), request, responseFor(request)
                .notFound()
                .content("Not found").contentType(CONTENT_TYPE_PLAIN)
                .buildFullResponse());
        ReferenceCountUtil.release(request);
    }

    private static void writeResponse(final Channel channel,
                                      final HttpRequest request,
                                      final HttpResponse response) {
        boolean hasKeepAliveHeader = AsciiString.contentEqualsIgnoreCase(KEEP_ALIVE, request.headers().get(CONNECTION));
        if (!request.protocolVersion().isKeepAliveDefault() && hasKeepAliveHeader) {
            response.headers().set(CONNECTION, KEEP_ALIVE);
        }
        final ChannelFuture wf = channel.writeAndFlush(response);
        if (!hasKeepAliveHeader) {
            wf.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof SessionNotFoundException) {
            final SessionNotFoundException se = (SessionNotFoundException) cause;
            logger.debug("Could not find session [{}]", se.sessionId());
            writeNotFoundResponse(se.httpRequest(), ctx);
        } else {
            logger.error("exception caught:", cause);
            ctx.fireExceptionCaught(cause);
        }
    }

    private static String pathWithoutPrefix(final String prefix, final HttpRequest request) {
        return new QueryStringDecoder(request.uri().replaceFirst(prefix, "")).path();
    }

    private static final class SessionNotFoundException extends Exception {
        private static final long serialVersionUID = 1101611486620901143L;
        private final String sessionId;
        private final HttpRequest request;

        private SessionNotFoundException(final String sessionId, final HttpRequest request) {
            this.sessionId = sessionId;
            this.request = request;
        }

        public String sessionId() {
            return sessionId;
        }

        public HttpRequest httpRequest() {
            return request;
        }
    }

}
