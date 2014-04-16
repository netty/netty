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

public class ConnectVariableHeader {

    private final String name;
    private final int version;
    private final boolean hasUserName;
    private final boolean hasPassword;
    private final boolean willRetain;
    private final int willQos;
    private final boolean willFlag;
    private final boolean cleanSession;
    private final int keepAliveTimeSeconds;

    public ConnectVariableHeader(
            String name,
            int version,
            boolean hasUserName,
            boolean hasPassword,
            boolean willRetain,
            int willQos,
            boolean willFlag,
            boolean cleanSession,
            int keepAliveTimeSeconds) {
        this.name = name;
        this.version = version;
        this.hasUserName = hasUserName;
        this.hasPassword = hasPassword;
        this.willRetain = willRetain;
        this.willQos = willQos;
        this.willFlag = willFlag;
        this.cleanSession = cleanSession;
        this.keepAliveTimeSeconds = keepAliveTimeSeconds;
    }

    public String getName() {
        return name;
    }

    public int getVersion() {
        return version;
    }

    public boolean hasUserName() {
        return hasUserName;
    }

    public boolean hasPassword() {
        return hasPassword;
    }

    public boolean isWillRetain() {
        return willRetain;
    }

    public int getWillQos() {
        return willQos;
    }

    public boolean isWillFlag() {
        return willFlag;
    }

    public boolean isCleanSession() {
        return cleanSession;
    }

    public int getKeepAliveTimeSeconds() {
        return keepAliveTimeSeconds;
    }
}
