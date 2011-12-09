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
import io.netty.buffer.HeapChannelBufferFactory;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPipelineFactory;
import io.netty.channel.Channels;
import io.netty.channel.socket.SocketChannelConfig;
import io.netty.util.internal.ConversionUtil;

/**
 * A face channel config class for use in testing
 */
public class FakeChannelConfig implements SocketChannelConfig {

    private int receiveBufferSize = 1024;

    private int sendBufferSize = 1024;

    private int soLinger = 500;

    private int trafficClass = 0;

    private boolean keepAlive = true;

    private boolean reuseAddress = true;

    private boolean tcpNoDelay = false;

    private ChannelBufferFactory bufferFactory = new HeapChannelBufferFactory();

    private int connectTimeout = 5000;

    private ChannelPipelineFactory pipelineFactory =
            new ChannelPipelineFactory() {
                @Override
                public ChannelPipeline getPipeline() throws Exception {
                    return Channels.pipeline();
                }
            };

    private int writeTimeout = 3000;

    @Override
    public int getReceiveBufferSize() {
        return receiveBufferSize;
    }

    @Override
    public void setReceiveBufferSize(int receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
    }

    @Override
    public int getSendBufferSize() {
        return sendBufferSize;
    }

    @Override
    public void setSendBufferSize(int sendBufferSize) {
        this.sendBufferSize = sendBufferSize;
    }

    @Override
    public int getSoLinger() {
        return soLinger;
    }

    @Override
    public void setSoLinger(int soLinger) {
        this.soLinger = soLinger;
    }

    @Override
    public int getTrafficClass() {
        return trafficClass;
    }

    @Override
    public void setTrafficClass(int trafficClass) {
        this.trafficClass = trafficClass;
    }

    @Override
    public boolean isKeepAlive() {
        return keepAlive;
    }

    @Override
    public void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
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
    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    @Override
    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    @Override
    public void setPerformancePreferences(int connectionTime, int latency,
            int bandwidth) {
        // do nothing
    }

    @Override
    public ChannelBufferFactory getBufferFactory() {
        return bufferFactory;
    }

    @Override
    public void setBufferFactory(ChannelBufferFactory bufferFactory) {
        this.bufferFactory = bufferFactory;
    }

    @Override
    public int getConnectTimeoutMillis() {
        return connectTimeout;
    }

    @Override
    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        connectTimeout = connectTimeoutMillis;
    }

    @Override
    public ChannelPipelineFactory getPipelineFactory() {
        return pipelineFactory;
    }

    @Override
    public void setPipelineFactory(ChannelPipelineFactory pipelineFactory) {
        this.pipelineFactory = pipelineFactory;
    }

    public int getWriteTimeoutMillis() {
        return writeTimeout;
    }

    public void setWriteTimeoutMillis(int writeTimeoutMillis) {
        writeTimeout = writeTimeoutMillis;
    }

    @Override
    public boolean setOption(String key, Object value) {
        if (key.equals("pipelineFactory")) {
            setPipelineFactory((ChannelPipelineFactory) value);
        } else if (key.equals("connectTimeoutMillis")) {
            setConnectTimeoutMillis(ConversionUtil.toInt(value));
        } else if (key.equals("bufferFactory")) {
            setBufferFactory((ChannelBufferFactory) value);
        } else if (key.equals("receiveBufferSize")) {
            setReceiveBufferSize(ConversionUtil.toInt(value));
        } else if (key.equals("sendBufferSize")) {
            setSendBufferSize(ConversionUtil.toInt(value));
        } else if (key.equals("tcpNoDelay")) {
            setTcpNoDelay(ConversionUtil.toBoolean(value));
        } else if (key.equals("keepAlive")) {
            setKeepAlive(ConversionUtil.toBoolean(value));
        } else if (key.equals("reuseAddress")) {
            setReuseAddress(ConversionUtil.toBoolean(value));
        } else if (key.equals("soLinger")) {
            setSoLinger(ConversionUtil.toInt(value));
        } else if (key.equals("trafficClass")) {
            setTrafficClass(ConversionUtil.toInt(value));
        } else {
            return false;
        }
        return true;
    }

    @Override
    public void setOptions(Map<String, Object> options) {
        for (Entry<String, Object> e: options.entrySet()) {
            setOption(e.getKey(), e.getValue());
        }
    }

}
