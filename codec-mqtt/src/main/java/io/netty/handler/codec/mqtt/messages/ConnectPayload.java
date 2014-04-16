/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.handler.codec.mqtt.messages;

public class ConnectPayload {

    private final String clientIdentifier;
    private final String willTopic;
    private final String willMessage;
    private final String userName;
    private final String password;

    public ConnectPayload(
            String clientIdentifier,
            String willTopic,
            String willMessage,
            String userName,
            String password) {
        this.clientIdentifier = clientIdentifier;
        this.willTopic = willTopic;
        this.willMessage = willMessage;
        this.userName = userName;
        this.password = password;
    }

    public String getClientIdentifier() {
        return clientIdentifier;
    }

    public String getWillTopic() {
        return willTopic;
    }

    public String getWillMessage() {
        return willMessage;
    }

    public String getUserName() {
        return userName;
    }

    public String getPassword() {
        return password;
    }
}
