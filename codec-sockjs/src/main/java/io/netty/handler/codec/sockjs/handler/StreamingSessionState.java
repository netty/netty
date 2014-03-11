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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.sockjs.protocol.MessageFrame;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.concurrent.ConcurrentMap;

/**
 * A streaming session state handles session interactions which has a persistent
 * connection to the client.
 *
 * This connection/channel is created when the first request from the client is made. Upon
 * opening the session any messages will be flushed. This could happen if the client connection
 * is dropped but the session still active and the SockJS service has generated messages but
 * has no where to send them. In this case the messages will be queue up and it is these
 * message that get flushed.
 */
class StreamingSessionState extends AbstractTimersSessionState {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(StreamingSessionState.class);

    StreamingSessionState(final ConcurrentMap<String, SockJsSession> sessions) {
        super(sessions);
    }

    @Override
    public void onOpen(final SockJsSession session, final ChannelHandlerContext ctx) {
        flushMessages(ctx, session);
    }

    @Override
    public ChannelHandlerContext getSendingContext(SockJsSession session) {
        return session.connectionContext();
    }

    private static void flushMessages(final ChannelHandlerContext ignored, final SockJsSession session) {
        final Channel channel = session.connectionContext().channel();
        if (channel.isActive() && channel.isRegistered()) {
            final String[] allMessages = session.getAllMessages();
            if (allMessages.length == 0) {
                return;
            }

            final MessageFrame messageFrame = new MessageFrame(allMessages);
            logger.debug("flushing [{}]", messageFrame);
            channel.writeAndFlush(messageFrame).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(final ChannelFuture future) throws Exception {
                    if (!future.isSuccess()) {
                        session.addMessages(allMessages);
                    }
                }
            });
        }
    }

    @Override
    public void onSockJSServerInitiatedClose(final SockJsSession session) {
        final ChannelHandlerContext context = session.connectionContext();
        if (context != null) { //could be null if the request is aborted, for example due to missing callback.
            logger.debug("Will close session connectionContext " + session.connectionContext());
            context.close();
        }
    }

    @Override
    public boolean isInUse(final SockJsSession session) {
        return session.connectionContext().channel().isActive();
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this);
    }

    @Override
    public void onClose() {
    }

}
