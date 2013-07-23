/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.sockjs.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.sockjs.Config;
import io.netty.handler.codec.sockjs.SessionContext;
import io.netty.handler.codec.sockjs.SockJSService;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

class SockJSSession {

    enum States { CONNECTING, OPEN, CLOSED, INTERRUPTED }

    private States state = States.CONNECTING;
    private final String sessionId;
    private final SockJSService service;
    private final LinkedList<String> messages = new LinkedList<String>();
    private final AtomicLong timestamp = new AtomicLong();
    private final AtomicBoolean inuse = new AtomicBoolean();
    private ChannelHandlerContext ctx;

    public SockJSSession(final String sessionId, final SockJSService service) {
        this.sessionId = sessionId;
        this.service = service;
    }

    public synchronized ChannelHandlerContext context() {
        return ctx;
    }

    public synchronized void setContext(final ChannelHandlerContext ctx) {
        if (this.ctx == null) {
            this.ctx = ctx;
        }
    }

    public synchronized void setState(States state) {
        this.state = state;
    }

    public synchronized States getState() {
        return state;
    }

    /**
     * Returns true if this session is currently in use.
     * @return
     */
    public boolean inuse() {
        return inuse.get();
    }

    public void setInuse() {
        inuse.set(true);
    }

    public void resetInuse() {
        inuse.set(false);
    }

    public Config config() {
        return service.config();
    }

    public String sessionId() {
        return sessionId;
    }

    public synchronized void onMessage(final String message) throws Exception {
        service.onMessage(message);
        updateTimestamp();
    }

    public synchronized void onOpen(final SessionContext session) {
        setState(States.OPEN);
        service.onOpen(session);
        updateTimestamp();
    }

    public synchronized void onClose() {
        setState(States.CLOSED);
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
        final String[] array = messages.toArray(new String[]{});
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
        return "SockJSSession[sessionId=" + sessionId + ", state=" + state + "]";
    }

}
