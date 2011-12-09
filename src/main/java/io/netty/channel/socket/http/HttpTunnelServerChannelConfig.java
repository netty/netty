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

import java.util.Map;
import java.util.Map.Entry;

import io.netty.buffer.ChannelBufferFactory;
import io.netty.channel.ChannelPipelineFactory;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.ServerSocketChannelConfig;

/**
 */
public class HttpTunnelServerChannelConfig implements ServerSocketChannelConfig {

    private ChannelPipelineFactory pipelineFactory;

    private final ServerSocketChannel realChannel;

    private TunnelIdGenerator tunnelIdGenerator =
            new DefaultTunnelIdGenerator();

    public HttpTunnelServerChannelConfig(ServerSocketChannel realChannel) {
        this.realChannel = realChannel;
    }

    private ServerSocketChannelConfig getWrappedConfig() {
        return realChannel.getConfig();
    }

    @Override
    public int getBacklog() {
        return getWrappedConfig().getBacklog();
    }

    @Override
    public int getReceiveBufferSize() {
        return getWrappedConfig().getReceiveBufferSize();
    }

    @Override
    public boolean isReuseAddress() {
        return getWrappedConfig().isReuseAddress();
    }

    @Override
    public void setBacklog(int backlog) {
        getWrappedConfig().setBacklog(backlog);
    }

    @Override
    public void setPerformancePreferences(int connectionTime, int latency,
            int bandwidth) {
        getWrappedConfig().setPerformancePreferences(connectionTime, latency,
                bandwidth);
    }

    @Override
    public void setReceiveBufferSize(int receiveBufferSize) {
        getWrappedConfig().setReceiveBufferSize(receiveBufferSize);
    }

    @Override
    public void setReuseAddress(boolean reuseAddress) {
        getWrappedConfig().setReuseAddress(reuseAddress);
    }

    @Override
    public ChannelBufferFactory getBufferFactory() {
        return getWrappedConfig().getBufferFactory();
    }

    @Override
    public int getConnectTimeoutMillis() {
        return getWrappedConfig().getConnectTimeoutMillis();
    }

    @Override
    public ChannelPipelineFactory getPipelineFactory() {
        return pipelineFactory;
    }

    @Override
    public void setBufferFactory(ChannelBufferFactory bufferFactory) {
        getWrappedConfig().setBufferFactory(bufferFactory);
    }

    @Override
    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        getWrappedConfig().setConnectTimeoutMillis(connectTimeoutMillis);
    }

    @Override
    public boolean setOption(String name, Object value) {
        if (name.equals("pipelineFactory")) {
            setPipelineFactory((ChannelPipelineFactory) value);
            return true;
        } else if (name.equals("tunnelIdGenerator")) {
            setTunnelIdGenerator((TunnelIdGenerator) value);
            return true;
        } else {
            return getWrappedConfig().setOption(name, value);
        }
    }

    @Override
    public void setOptions(Map<String, Object> options) {
        for (Entry<String, Object> e: options.entrySet()) {
            setOption(e.getKey(), e.getValue());
        }
    }

    @Override
    public void setPipelineFactory(ChannelPipelineFactory pipelineFactory) {
        this.pipelineFactory = pipelineFactory;
    }

    public void setTunnelIdGenerator(TunnelIdGenerator tunnelIdGenerator) {
        this.tunnelIdGenerator = tunnelIdGenerator;
    }

    public TunnelIdGenerator getTunnelIdGenerator() {
        return tunnelIdGenerator;
    }
}
