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

/**
 * Configuration the server end of an http tunnel.
 * 
 * These properties largely have no effect in the current implementation, and exist
 * for API compatibility with TCP channels. With the exception of high / low water
 * marks, any changes in the values will not be honoured.
 */
public class HttpTunnelAcceptedChannelConfig extends HttpTunnelChannelConfig {

    private static final int SO_LINGER_DISABLED = -1;

    private static final int FAKE_SEND_BUFFER_SIZE = 16 * 1024;

    private static final int FAKE_RECEIVE_BUFFER_SIZE = 16 * 1024;

    // based on the values in RFC 791
    private static final int DEFAULT_TRAFFIC_CLASS = 0;

    @Override
    public boolean isTcpNoDelay() {
        return true;
    }

    @Override
    public void setTcpNoDelay(boolean tcpNoDelay) {
        // we do not allow the value to be changed, as it will not be honoured
    }

    @Override
    public int getSoLinger() {
        return SO_LINGER_DISABLED;
    }

    @Override
    public void setSoLinger(int soLinger) {
        // we do not allow the value to be changed, as it will not be honoured
    }

    @Override
    public int getSendBufferSize() {
        return FAKE_SEND_BUFFER_SIZE;
    }

    @Override
    public void setSendBufferSize(int sendBufferSize) {
        // we do not allow the value to be changed, as it will not be honoured
    }

    @Override
    public int getReceiveBufferSize() {
        return FAKE_RECEIVE_BUFFER_SIZE;
    }

    @Override
    public void setReceiveBufferSize(int receiveBufferSize) {
        // we do not allow the value to be changed, as it will not be honoured
    }

    @Override
    public boolean isKeepAlive() {
        return true;
    }

    @Override
    public void setKeepAlive(boolean keepAlive) {
        // we do not allow the value to be changed, as it will not be honoured
    }

    @Override
    public int getTrafficClass() {
        return DEFAULT_TRAFFIC_CLASS;
    }

    @Override
    public void setTrafficClass(int trafficClass) {
        // we do not allow the value to be changed, as it will not be honoured
    }

    @Override
    public boolean isReuseAddress() {
        return false;
    }

    @Override
    public void setReuseAddress(boolean reuseAddress) {
        // we do not allow the value to be changed, as it will not be honoured
    }

    @Override
    public void setPerformancePreferences(int connectionTime, int latency,
            int bandwidth) {
        // we do not allow the value to be changed, as it will not be honoured
    }
}
