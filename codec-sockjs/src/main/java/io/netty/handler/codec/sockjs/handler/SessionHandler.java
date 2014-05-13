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
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.handler.codec.sockjs.SockJsSessionContext;
import io.netty.handler.codec.sockjs.handler.SessionState.State;
import io.netty.handler.codec.sockjs.protocol.CloseFrame;
import io.netty.handler.codec.sockjs.protocol.MessageFrame;
import io.netty.handler.codec.sockjs.protocol.OpenFrame;
import io.netty.handler.codec.sockjs.util.ArgumentUtil;

/**
 * A ChannelHandler that manages SockJS sessions.
 *
 * For every connection received a new SessionHandler will be created and added to
 * the pipeline.
 *
 * Depending on the type of connection (polling, streaming, send, or websocket)
 * the type of {@link SessionState} that this session handles will differ.
 *
 */
public class SessionHandler extends ChannelHandlerAdapter {

    public enum Event { CLOSE_SESSION, HANDLE_SESSION }

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SessionHandler.class);

    private final SessionState sessionState;

    public SessionHandler(final SessionState sessionState) {
        ArgumentUtil.checkNotNull(sessionState, "sessionState");
        this.sessionState = sessionState;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            if (logger.isDebugEnabled()) {
                logger.debug("Handle session : {}", sessionState);
            }
            ReferenceCountUtil.release(msg);
            handleSession(ctx);
        } else if (msg instanceof String) {
            handleMessage((String) msg);
        } else {
            ctx.fireChannelRead(ReferenceCountUtil.retain(msg));
        }
    }

    private void handleSession(final ChannelHandlerContext ctx) throws Exception {
        switch (sessionState.getState()) {
        case CONNECTING:
            sessionConnecting(ctx);
            break;
        case OPEN:
            sessionOpen(ctx);
            break;
        case INTERRUPTED:
            sessionInterrupted(ctx);
            break;
        case CLOSED:
            sessionClosed(ctx);
            break;
        }
    }

    private void sessionConnecting(final ChannelHandlerContext ctx) {
        logger.debug("State.CONNECTING sending open frame");
        writeOpenFrame(ctx);
        sessionState.onConnect(ctx, new DefaultSockJsSessionContext());
    }

    private void sessionOpen(ChannelHandlerContext ctx) {
        if (sessionState.isInUse()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Another connection still in open for {}", sessionState);
            }
            writeCloseFrame(ctx, 2010, "Another connection still open");
            sessionState.setState(State.INTERRUPTED);
        } else {
            sessionState.onOpen(ctx);
        }
    }

    private static void sessionInterrupted(ChannelHandlerContext ctx) {
        writeCloseFrame(ctx, 1002, "Connection interrupted");
    }

    private void sessionClosed(ChannelHandlerContext ctx) {
        writeCloseFrame(ctx, 3000, "Go away!");
        sessionState.onClose();
    }

    private void handleMessage(final String message) throws Exception {
        sessionState.onMessage(message);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        sessionState.resetInuse();
        ctx.fireChannelInactive();
    }

    private static boolean isWritable(final Channel channel) {
        return channel.isActive() && channel.isRegistered();
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object event) throws Exception {
        if (event == Event.CLOSE_SESSION) {
            sessionState.onClose();
            sessionState.onSockJSServerInitiatedClose();
        } else if (event == Event.HANDLE_SESSION) {
            handleSession(ctx);
        }
    }

    private static void writeCloseFrame(final ChannelHandlerContext ctx, final int code, final String message) {
        ctx.channel().writeAndFlush(new CloseFrame(code, message));
    }

    private static void writeOpenFrame(final ChannelHandlerContext ctx) {
        ctx.channel().writeAndFlush(new OpenFrame());
    }

    public class DefaultSockJsSessionContext implements SockJsSessionContext {

        @Override
        public void send(String message) {
            final Channel channel = sessionState.getSendingContext().channel();
            if (isWritable(channel)) {
                channel.writeAndFlush(new MessageFrame(message));
            } else {
                sessionState.storeMessage(message);
            }
        }

        @Override
        public void close() {
            sessionState.onClose();
            final Channel channel = sessionState.getSendingContext().channel();
            if (isWritable(channel)) {
                final CloseFrame closeFrame = new CloseFrame(3000, "Go away!");
                if (logger.isDebugEnabled()) {
                    logger.debug("Writing {}", closeFrame);
                }
                channel.writeAndFlush(closeFrame).addListener(ChannelFutureListener.CLOSE);
            }
        }

        @Override
        public ChannelHandlerContext getContext() {
            return sessionState.getSendingContext();
        }
    }

}
