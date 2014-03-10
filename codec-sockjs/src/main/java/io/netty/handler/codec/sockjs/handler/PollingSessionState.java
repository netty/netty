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

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.sockjs.protocol.MessageFrame;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.concurrent.ConcurrentMap;

/**
 * A polling state does not have a persistent connection to the client, instead a client
 * will connect, poll, to request data.
 *
 * The first poll request will open the session and the ChannelHandlerContext for
 * that request will be stored along with the new session. Subsequent request will
 * use the same sessionId and hence use the same session.
 *
 * A polling request will flush any queued up messages in the session and write them
 * out to the current channel. It cannot use the original channel for the session as it
 * most likely will have been closed. Instead it will use current channel effectively
 * writing out the queued up messages to the pipeline to be handled, and eventually returned
 * in the response.
 *
 */
class PollingSessionState extends AbstractTimersSessionState {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(PollingSessionState.class);
    private final ConcurrentMap<String, SockJsSession> sessions;

    protected PollingSessionState(final ConcurrentMap<String, SockJsSession> sessions) {
        super(sessions);
        this.sessions = sessions;
    }

    @Override
    public void onOpen(final SockJsSession session, final ChannelHandlerContext ctx) {
        flushMessages(ctx, session);
    }

    private static void flushMessages(final ChannelHandlerContext ctx, final SockJsSession session) {
        final String[] allMessages = session.getAllMessages();
        if (allMessages.length == 0) {
            return;
        }
        final MessageFrame messageFrame = new MessageFrame(allMessages);
        ctx.channel().writeAndFlush(messageFrame).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(final ChannelFuture future) throws Exception {
                if (!future.isSuccess()) {
                    session.addMessages(allMessages);
                }
            }
        });
    }

    @Override
    public boolean isInUse(final SockJsSession session) {
        return session.context().channel().isActive() || session.inuse();
    }

    @Override
    public void onSockJSServerInitiatedClose(final SockJsSession session) {
        final ChannelHandlerContext context = session.context();
        if (context != null) { //could be null if the request is aborted, for example due to missing callback.
            if (logger.isDebugEnabled()) {
                logger.debug("Will close session context {}", session.context());
            }
            context.close();
        }
        sessions.remove(session.sessionId());
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this);
    }

    @Override
    public void onClose() {
    }

}
