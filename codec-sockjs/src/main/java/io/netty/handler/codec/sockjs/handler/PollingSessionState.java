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

import java.util.List;
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

    PollingSessionState(final ConcurrentMap<String, SockJsSession> sessions, final SockJsSession session) {
        super(sessions, session);
        this.sessions = sessions;
    }

    @Override
    public void onOpen(final ChannelHandlerContext ctx) {
        super.onOpen(ctx);
        flushMessages(ctx);
    }

    @Override
    public ChannelHandlerContext getSendingContext() {
        final ChannelHandlerContext openContext = getSockJsSession().openContext();
        return openContext == null ? getSockJsSession().connectionContext() : openContext;
    }

    private void flushMessages(final ChannelHandlerContext ctx) {
        final List<String> allMessages = getSockJsSession().getAllMessages();
        if (allMessages.isEmpty()) {
            return;
        }
        final MessageFrame messageFrame = new MessageFrame(allMessages);
        ctx.channel().writeAndFlush(messageFrame).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(final ChannelFuture future) throws Exception {
                if (!future.isSuccess()) {
                    final SockJsSession sockJsSession = getSockJsSession();
                    for (String msg : allMessages) {
                        sockJsSession.addMessage(msg);
                    }
               }
            }
        }).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public boolean isInUse() {
        return getSockJsSession().connectionContext().channel().isActive() || getSockJsSession().inuse();
    }

    @Override
    public void onSockJSServerInitiatedClose() {
        final ChannelHandlerContext context = getSockJsSession().connectionContext();
        if (context != null) { //could be null if the request is aborted, for example due to missing callback.
            if (logger.isDebugEnabled()) {
                logger.debug("Will close session connectionContext {}", getSockJsSession().connectionContext());
            }
            context.close();
        }
        sessions.remove(getSockJsSession().sessionId());
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this);
    }

}
