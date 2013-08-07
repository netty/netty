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
package io.netty.handler.codec.sockjs.handlers;

import io.netty.handler.codec.sockjs.Config;
import io.netty.handler.codec.sockjs.SessionContext;
import io.netty.handler.codec.sockjs.SockJSService;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

public class StubSockJSService implements SockJSService {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(StubSockJSService.class);

    private SessionContext session;
    private final Config config;

    public StubSockJSService(final Config config) {
        this.config = config;
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public void onOpen(SessionContext session) {
        logger.info("onOpen : " + session);
        this.session = session;
    }

    @Override
    public void onMessage(String message) throws Exception {
        logger.info("onMessage : " + message);
    }

    @Override
    public void onClose() {
        logger.info("onClose");
    }

    public void sendMessage(final String message) {
        logger.info("sendMessage : " + message);
        session.send(message);
    }

}
