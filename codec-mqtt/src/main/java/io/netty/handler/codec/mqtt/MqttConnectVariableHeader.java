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

package io.netty.handler.codec.mqtt;

import io.netty.util.internal.StringUtil;

/**
 * Variable Header for the {@link MqttConnectMessage}
 */
public final class MqttConnectVariableHeader {

    private final String name;
    private final int version;
    private final boolean hasUserName;
    private final boolean hasPassword;
    private final boolean isWillRetain;
    private final int willQos;
    private final boolean isWillFlag;
    private final boolean isCleanSession;
    private final int keepAliveTimeSeconds;
    private final MqttProperties properties;

    public MqttConnectVariableHeader(
            String name,
            int version,
            boolean hasUserName,
            boolean hasPassword,
            boolean isWillRetain,
            int willQos,
            boolean isWillFlag,
            boolean isCleanSession,
            int keepAliveTimeSeconds) {
        this(name,
                version,
                hasUserName,
                hasPassword,
                isWillRetain,
                willQos,
                isWillFlag,
                isCleanSession,
                keepAliveTimeSeconds,
                MqttProperties.NO_PROPERTIES);
    }

    public MqttConnectVariableHeader(
            String name,
            int version,
            boolean hasUserName,
            boolean hasPassword,
            boolean isWillRetain,
            int willQos,
            boolean isWillFlag,
            boolean isCleanSession,
            int keepAliveTimeSeconds,
            MqttProperties properties) {
        this.name = name;
        this.version = version;
        this.hasUserName = hasUserName;
        this.hasPassword = hasPassword;
        this.isWillRetain = isWillRetain;
        this.willQos = willQos;
        this.isWillFlag = isWillFlag;
        this.isCleanSession = isCleanSession;
        this.keepAliveTimeSeconds = keepAliveTimeSeconds;
        this.properties = MqttProperties.withEmptyDefaults(properties);
    }

    public String name() {
        return name;
    }

    public int version() {
        return version;
    }

    public boolean hasUserName() {
        return hasUserName;
    }

    public boolean hasPassword() {
        return hasPassword;
    }

    public boolean isWillRetain() {
        return isWillRetain;
    }

    public int willQos() {
        return willQos;
    }

    public boolean isWillFlag() {
        return isWillFlag;
    }

    public boolean isCleanSession() {
        return isCleanSession;
    }

    public int keepAliveTimeSeconds() {
        return keepAliveTimeSeconds;
    }

    public MqttProperties properties() {
        return properties;
    }

    @Override
    public String toString() {
        return new StringBuilder(StringUtil.simpleClassName(this))
            .append('[')
            .append("name=").append(name)
            .append(", version=").append(version)
            .append(", hasUserName=").append(hasUserName)
            .append(", hasPassword=").append(hasPassword)
            .append(", isWillRetain=").append(isWillRetain)
            .append(", isWillFlag=").append(isWillFlag)
            .append(", isCleanSession=").append(isCleanSession)
            .append(", keepAliveTimeSeconds=").append(keepAliveTimeSeconds)
            .append(']')
            .toString();
    }
}
