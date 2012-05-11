/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel;

import io.netty.channel.socket.SocketChannelConfig;
import io.netty.util.internal.ConversionUtil;

import java.util.Map;
import java.util.Map.Entry;

/**
 * The default {@link SocketChannelConfig} implementation.
 */
public class DefaultChannelConfig implements ChannelConfig {

    private static final int DEFAULT_CONNECT_TIMEOUT = 30000;

    private volatile int connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT;

    @Override
    public void setOptions(Map<String, Object> options) {
        for (Entry<String, Object> e: options.entrySet()) {
            setOption(e.getKey(), e.getValue());
        }
    }

    @Override
    public boolean setOption(String key, Object value) {
        if ("connectTimeoutMillis".equals(key)) {
            setConnectTimeoutMillis(ConversionUtil.toInt(value));
        } else {
            return false;
        }
        return true;
    }

    @Override
    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    @Override
    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        if (connectTimeoutMillis < 0) {
            throw new IllegalArgumentException(String.format(
                    "connectTimeoutMillis: %d (expected: >= 0)", connectTimeoutMillis));
        }
        this.connectTimeoutMillis = connectTimeoutMillis;
    }
}
