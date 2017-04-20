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
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

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

    private static final AtomicReferenceFieldUpdater<SockJsSession, State> STATE_UPDATER =
            newAtomicFieldUpdater(SockJsSession.class, State.class, "state");
    private static final AtomicReferenceFieldUpdater<SockJsSession, ChannelHandlerContext> CONN_CTX_UPDATER =
            newAtomicFieldUpdater(SockJsSession.class, ChannelHandlerContext.class, "connectionCtx");
    private static final AtomicReferenceFieldUpdater<SockJsSession, ChannelHandlerContext> OPEN_CTX_UPDATER =
            newAtomicFieldUpdater(SockJsSession.class, ChannelHandlerContext.class, "openCtx");
    private static final AtomicLongFieldUpdater<SockJsSession> TIMESTAMP_UPDATER =
            newAtomicLongUpdater(SockJsSession.class, "timestamp");
    private final String sessionId;
    private final SockJsService service;
    private final Queue<String> messageQueue = new ConcurrentLinkedQueue<String>();
    @SuppressWarnings("UnusedDeclaration")
    private volatile State state = State.CONNECTING;
    @SuppressWarnings("UnusedDeclaration")
    private volatile ChannelHandlerContext connectionCtx;
    @SuppressWarnings("UnusedDeclaration")
    private volatile ChannelHandlerContext openCtx;
    @SuppressWarnings("UnusedDeclaration")
    private volatile long timestamp;
    private volatile boolean inuse;

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
        return connectionCtx;
    }

    /**
     * Sets the {@link ChannelHandlerContext} used to initially connect.
     *
     * @param newCtx the ChannelHandlerContext used establishing a connection.
     */
    public void setConnectionContext(final ChannelHandlerContext newCtx) {
        for (;;) {
            if (CONN_CTX_UPDATER.compareAndSet(this, connectionCtx, newCtx)) {
                return;
            }
        }
    }

    /**
     * Returns the {@link ChannelHandlerContext} used on an open session.
     *
     * @return {@code ChannelHandlerContext} the ChannelHandlerContext used establishing a connection.
     */
    public ChannelHandlerContext openContext() {
        return openCtx;
    }

    /**
     * Sets the {@link ChannelHandlerContext} used to initially connect.
     *
     * @param newCtx the ChannelHandlerContext used when the session is open.
     */
    public void setOpenContext(final ChannelHandlerContext newCtx) {
        for (;;) {
            if (OPEN_CTX_UPDATER.compareAndSet(this, openCtx, newCtx)) {
                return;
            }
        }
    }

    /**
     * Sets the {@link State} of this session.
     *
     * @param newState the state to which this session should be set.
     */
    public void setState(final State newState) {
        for (;;) {
            if (STATE_UPDATER.compareAndSet(this, state, newState)) {
                return;
            }
        }
    }

    public State getState() {
        return state;
    }

    public boolean inuse() {
        return inuse;
    }

    public void setInuse(final boolean use) {
        inuse = use;
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
        if (STATE_UPDATER.get(this) == State.CONNECTING || STATE_UPDATER.get(this) == State.CLOSED) {
            return;
        }
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
    public void addMessages(final String... messages) {
        for (String msg: messages) {
            messageQueue.add(msg);
        }
    }

    private void updateTimestamp() {
        TIMESTAMP_UPDATER.set(this, System.nanoTime() / 1000000);
    }

    /**
     * Returns the timestamp for when this session was last interacted with.
     * The intended usage of this timestamp if to determine when a session should be discarded/closed.
     *
     * @return {@code long} the timestamp which was the last time this session was used.
     */
    public long timestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "[sessionId=" + sessionId + ", state=" + state + ']';
    }

    private static <T, V> AtomicReferenceFieldUpdater<T, V> newAtomicFieldUpdater(final Class<T> type,
                                                                                  final Class<V> valueType,
                                                                                  final String fieldName) {
        final AtomicReferenceFieldUpdater<T, V> u = PlatformDependent.newAtomicReferenceFieldUpdater(type, fieldName);
        return u != null ? AtomicReferenceFieldUpdater.newUpdater(type, valueType, fieldName) : u;
    }

    private static <T> AtomicLongFieldUpdater<T> newAtomicLongUpdater(final Class<T> type,
                                                                       final String fieldName) {
        final AtomicLongFieldUpdater<T> l = PlatformDependent.newAtomicLongFieldUpdater(type, fieldName);
        return l != null ? AtomicLongFieldUpdater.newUpdater(type, fieldName) : l;
    }

}
