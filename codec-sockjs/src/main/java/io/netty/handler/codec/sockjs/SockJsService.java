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
package io.netty.handler.codec.sockjs;

/**
 * Represents the server side business application server in SockJS.
 */
public interface SockJsService {

    /**
     * The {@link SockJsConfig} for this service
     *
     * @return {@link SockJsConfig} this services configuration.
     */
    SockJsConfig config();

    /**
     * Will be called when a new session is opened.
     *
     * @param session the {@link SockJsSessionContext} which can be stored and used for sending/closing.
     */
    void onOpen(SockJsSessionContext session);

    /**
     * Will be called when a message is sent to the service.
     *
     * @param message the message sent from a client.
     * @throws Exception
     */
    void onMessage(String message) throws Exception;

    /**
     * Will be called when the session is closed.
     */
    void onClose();

}
