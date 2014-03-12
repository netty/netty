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
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

class SockJsSession {

    private State state = State.CONNECTING;
    private final String sessionId;
    private final SockJsService service;
    private final List<String> messages = new ArrayList<String>();
    private final AtomicLong timestamp = new AtomicLong();
    private final AtomicBoolean inuse = new AtomicBoolean();
    private ChannelHandlerContext connectionContext;
    private ChannelHandlerContext openContext;

    protected SockJsSession(final String sessionId, final SockJsService service) {
        this.sessionId = sessionId;
        this.service = service;
    }

    /**
     * Returns the ChannelHandlerContext used to initially connect.
     *
     * @return {@code ChannelHandlerContext} the ChannelHandlerContext used establishing a connection.
     */
    public synchronized ChannelHandlerContext connectionContext() {
        return connectionContext;
    }

    /**
     * Sets the ChannelHandlerContext used to initially connect.
     *
     * @param ctx the ChannelHandlerContext used establishing a connection.
     */
    public synchronized void setConnectionContext(final ChannelHandlerContext ctx) {
        connectionContext = ctx;
    }

    /**
     * Returns the ChannelHandlerContext used on an open session.
     *
     * @return {@code ChannelHandlerContext} the ChannelHandlerContext used establishing a connection.
     */
    public synchronized ChannelHandlerContext openContext() {
        return openContext;
    }

    /**
     * Sets the ChannelHandlerContext used to initially connect.
     *
     * @param ctx the ChannelHandlerContext used when the session is open.
     */
    public synchronized void setOpenContext(final ChannelHandlerContext ctx) {
        openContext = ctx;
    }

    public synchronized void setState(State state) {
        this.state = state;
    }

    public synchronized State getState() {
        return state;
    }

    public boolean inuse() {
        return inuse.get();
    }

    public void setInuse() {
        inuse.set(true);
    }

    public void resetInuse() {
        inuse.set(false);
    }

    public SockJsConfig config() {
        return service.config();
    }

    public String sessionId() {
        return sessionId;
    }

    public synchronized void onMessage(final String message) throws Exception {
        service.onMessage(message);
        updateTimestamp();
    }

    public synchronized void onOpen(final SockJsSessionContext session) {
        setState(State.OPEN);
        service.onOpen(session);
        updateTimestamp();
    }

    public synchronized void onClose() {
        setState(State.CLOSED);
        service.onClose();
    }

    public synchronized void addMessage(final String message) {
        messages.add(message);
        updateTimestamp();
    }

    public synchronized void clearMessagees() {
        messages.clear();
    }

    public synchronized String[] getAllMessages() {
        final String[] array = messages.toArray(new String[messages.size()]);
        messages.clear();
        return array;
    }

    public synchronized void addMessages(final String[] messages) {
        this.messages.addAll(Arrays.asList(messages));
    }

    private void updateTimestamp() {
        timestamp.set(System.currentTimeMillis());
    }

    public long timestamp() {
        return timestamp.get();
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "[sessionId=" + sessionId + ", state=" + state + ']';
    }

}
