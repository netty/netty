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

package io.netty.channel.socket.http;

import io.netty.buffer.ChannelBufferFactory;
import io.netty.buffer.HeapChannelBufferFactory;
import io.netty.channel.ChannelPipelineFactory;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.socket.ServerSocketChannelConfig;

/**
 * A fake server socket channel config class for use in testing
 */
public class FakeServerSocketChannelConfig extends DefaultChannelConfig
        implements ServerSocketChannelConfig {

    public int backlog = 5;

    public int receiveBufferSize = 1024;

    public boolean reuseAddress = false;

    public int connectionTimeout = 5000;

    public ChannelPipelineFactory pipelineFactory;

    public int writeTimeout = 5000;

    public ChannelBufferFactory bufferFactory = new HeapChannelBufferFactory();

    @Override
    public int getBacklog() {
        return backlog;
    }

    @Override
    public void setBacklog(int backlog) {
        this.backlog = backlog;
    }

    @Override
    public int getReceiveBufferSize() {
        return receiveBufferSize;
    }

    @Override
    public void setReceiveBufferSize(int receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
    }

    @Override
    public boolean isReuseAddress() {
        return reuseAddress;
    }

    @Override
    public void setReuseAddress(boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
    }

    @Override
    public void setPerformancePreferences(int connectionTime, int latency,
            int bandwidth) {
        // ignore
    }
}
