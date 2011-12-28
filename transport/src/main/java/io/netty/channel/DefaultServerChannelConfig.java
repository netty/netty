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

import java.util.Map;
import java.util.Map.Entry;

import io.netty.buffer.ChannelBufferFactory;
import io.netty.buffer.HeapChannelBufferFactory;
import io.netty.channel.socket.ServerSocketChannelConfig;

/**
 * The default {@link ServerSocketChannelConfig} implementation.
 */
public class DefaultServerChannelConfig implements ChannelConfig {

    private volatile ChannelPipelineFactory pipelineFactory;
    private volatile ChannelBufferFactory bufferFactory = HeapChannelBufferFactory.getInstance();

    @Override
    public void setOptions(Map<String, Object> options) {
        for (Entry<String, Object> e: options.entrySet()) {
            setOption(e.getKey(), e.getValue());
        }
    }

    /**
     * Sets an individual option.  You can override this method to support
     * additional configuration parameters.
     */
    @Override
    public boolean setOption(String key, Object value) {
        if (key.equals("pipelineFactory")) {
            setPipelineFactory((ChannelPipelineFactory) value);
        } else if (key.equals("bufferFactory")) {
            setBufferFactory((ChannelBufferFactory) value);
        } else {
            return false;
        }
        return true;
    }

    @Override
    public ChannelPipelineFactory getPipelineFactory() {
        return pipelineFactory;
    }

    @Override
    public void setPipelineFactory(ChannelPipelineFactory pipelineFactory) {
        if (pipelineFactory == null) {
            throw new NullPointerException("pipelineFactory");
        }
        this.pipelineFactory = pipelineFactory;
    }

    @Override
    public ChannelBufferFactory getBufferFactory() {
        return bufferFactory;
    }

    @Override
    public void setBufferFactory(ChannelBufferFactory bufferFactory) {
        if (bufferFactory == null) {
            throw new NullPointerException("bufferFactory");
        }

        this.bufferFactory = bufferFactory;
    }

    @Override
    public int getConnectTimeoutMillis() {
        return 0;
    }

    @Override
    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        // Unused
    }
}
