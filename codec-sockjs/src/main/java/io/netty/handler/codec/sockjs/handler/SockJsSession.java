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

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.sockjs.SockJsConfig;
import io.netty.handler.codec.sockjs.SockJsSessionContext;
import io.netty.handler.codec.sockjs.SockJsService;
import io.netty.handler.codec.sockjs.handler.SessionState.State;
import io.netty.util.internal.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents the state of a SockJS session.
 *
 * Every session has a timestamp which is updated when the session is used, enabling
 * sessions to be timed out which is a requirement of the SockJS specification.
 *
 * Every session also has a message queue which is used to store messages for session
 * that at that point in time do not have an active receiver for the messages. For example,
 * a polling transport might not currently have a connected polling request and the message
 * would be stored until such a request is recieved.
 *
 * A SockJS session must be able to support concurrent interactions as
 * some transports will have multiple connections accessing the same session. Taking a
 * polling transport as an example again, it can have a long polling request and also a
 * xhr-send request at the same time, both accessing the same session.
 *
 */
final class SockJsSession {

    private final String sessionId;
    private final SockJsService service;
    private final AtomicLong timestamp = new AtomicLong();
    private final AtomicBoolean inuse = new AtomicBoolean();
    private final ConcurrentLinkedQueue<String> messageQueue = new ConcurrentLinkedQueue<String>();
    private final AtomicReference<State> state = new AtomicReference<State>(State.CONNECTING);
    private final AtomicReference<ChannelHandlerContext> connectionCtx = new AtomicReference<ChannelHandlerContext>();
    private final AtomicReference<ChannelHandlerContext> openCtx = new AtomicReference<ChannelHandlerContext>();

    SockJsSession(final String sessionId, final SockJsService service) {
        this.sessionId = sessionId;
        this.service = service;
    }

    /**
     * Returns the ChannelHandlerContext used to initially connect.
     *
     * @return {@code ChannelHandlerContext} the ChannelHandlerContext used establishing a connection.
     */
    public ChannelHandlerContext connectionContext() {
        return connectionCtx.get();
    }

    /**
     * Sets the ChannelHandlerContext used to initially connect.
     *
     * @param ctx the ChannelHandlerContext used establishing a connection.
     */
    public void setConnectionContext(final ChannelHandlerContext ctx) {
        while (true) {
            final ChannelHandlerContext oldCtx = connectionCtx.get();
            if (connectionCtx.compareAndSet(oldCtx, ctx)) {
                return;
            }
        }
    }

    /**
     * Returns the ChannelHandlerContext used on an open session.
     *
     * @return {@code ChannelHandlerContext} the ChannelHandlerContext used establishing a connection.
     */
    public ChannelHandlerContext openContext() {
        return openCtx.get();
    }

    /**
     * Sets the ChannelHandlerContext used to initially connect.
     *
     * @param ctx the ChannelHandlerContext used when the session is open.
     */
    public void setOpenContext(final ChannelHandlerContext ctx) {
        while (true) {
            final ChannelHandlerContext oldCtx = openCtx.get();
            if (openCtx.compareAndSet(oldCtx, ctx)) {
                return;
            }
        }
    }

    /**
     * Sets the {@link State} of this session.
     *
     * @param newState the state to which this session should be set.
     */
    public void setState(State newState) {
        while (true) {
            final State oldState = state.get();
            if (state.compareAndSet(oldState, newState)) {
                return;
            }
        }
    }

    public State getState() {
        return state.get();
    }

    public boolean inuse() {
        return inuse.get();
    }

    public void setInuse(final boolean use) {
        inuse.set(use);
    }

    public SockJsConfig config() {
        return service.config();
    }

    public String sessionId() {
        return sessionId;
    }

    public void onMessage(final String message) throws Exception {
        service.onMessage(message);
        updateTimestamp();
    }

    public void onOpen(final SockJsSessionContext session) {
        setState(State.OPEN);
        service.onOpen(session);
        updateTimestamp();
    }

    public void onClose() {
        setState(State.CLOSED);
        service.onClose();
    }

    public void addMessage(final String message) {
        messageQueue.add(message);
        updateTimestamp();
    }

    /**
     * Returns all messages that have been stored in the session.
     * The messages returned, if any, will be removed from this session.
     *
     * @return {@code List} the messages that have been stored in this session.
     */
    public List<String> getAllMessages() {
        final List<String> all = new ArrayList<String>();
        for (String msg; (msg = messageQueue.poll()) != null;) {
            all.add(msg);
        }
        return all;
    }

    @SuppressWarnings("ManualArrayToCollectionCopy")
    public void addMessages(final String[] messages) {
        for (String msg: messages) {
            messageQueue.add(msg);
        }
    }

    private void updateTimestamp() {
        timestamp.set(System.currentTimeMillis());
    }

    /**
     * Returns the timestamp for when this session was last interacted with.
     * The intended usage of this timestamp if to determine when a session should be discarded/closed.
     *
     * @return {@code long} the timestamp which was the last time this session was used.
     */
    public long timestamp() {
        return timestamp.get();
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "[sessionId=" + sessionId + ", state=" + state + ']';
    }

}
