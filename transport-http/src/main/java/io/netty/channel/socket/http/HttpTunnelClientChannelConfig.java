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

import java.net.SocketAddress;

import io.netty.channel.socket.SocketChannelConfig;

/**
 * Configuration for the client end of an HTTP tunnel. Any socket channel properties set here
 * will be applied uniformly to the underlying send and poll channels, created from the channel
 * factory provided to the {@link HttpTunnelClientChannelFactory}.
 * <p>
 * HTTP tunnel clients have the following additional options:
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Name</th><th>Associated setter method</th>
 * </tr>
 * <tr><td>{@code "proxyAddress"}</td><td>{@link #setProxyAddress(SocketAddress)}</td></tr>
 * <tr><td>{@code "writeBufferHighWaterMark"}</td><td>{@link #setWriteBufferHighWaterMark(int)}</td></tr>
 * <tr><td>{@code "writeBufferLowWaterMark"}</td><td>{@link #setWriteBufferLowWaterMark(int)}</td></tr>
 * </table>
 */
public class HttpTunnelClientChannelConfig extends HttpTunnelChannelConfig {

    static final String PROXY_ADDRESS_OPTION = "proxyAddress";

    private final SocketChannelConfig sendChannelConfig;

    private final SocketChannelConfig pollChannelConfig;

    private volatile SocketAddress proxyAddress;

    HttpTunnelClientChannelConfig(SocketChannelConfig sendChannelConfig,
            SocketChannelConfig pollChannelConfig) {
        this.sendChannelConfig = sendChannelConfig;
        this.pollChannelConfig = pollChannelConfig;
    }

    /* HTTP TUNNEL SPECIFIC CONFIGURATION */
    // TODO Support all options in the old tunnel (see HttpTunnelingSocketChannelConfig)
    //      Mostly SSL, virtual host, and URL prefix
    @Override
    public boolean setOption(String key, Object value) {
        if (PROXY_ADDRESS_OPTION.equals(key)) {
            setProxyAddress((SocketAddress) value);
        } else {
            return super.setOption(key, value);
        }

        return true;
    }

    /**
     * @return the address of the http proxy. If this is null, then no proxy
     * should be used.
     */
    public SocketAddress getProxyAddress() {
        return proxyAddress;
    }

    /**
     * Specify a proxy to be used for the http tunnel. If this is null, then
     * no proxy should be used, otherwise this should be a directly accessible IPv4/IPv6
     * address and port.
     */
    public void setProxyAddress(SocketAddress proxyAddress) {
        this.proxyAddress = proxyAddress;
    }

    /* GENERIC SOCKET CHANNEL CONFIGURATION */

    @Override
    public int getReceiveBufferSize() {
        return pollChannelConfig.getReceiveBufferSize();
    }

    @Override
    public int getSendBufferSize() {
        return pollChannelConfig.getSendBufferSize();
    }

    @Override
    public int getSoLinger() {
        return pollChannelConfig.getSoLinger();
    }

    @Override
    public int getTrafficClass() {
        return pollChannelConfig.getTrafficClass();
    }

    @Override
    public boolean isKeepAlive() {
        return pollChannelConfig.isKeepAlive();
    }

    @Override
    public boolean isReuseAddress() {
        return pollChannelConfig.isReuseAddress();
    }

    @Override
    public boolean isTcpNoDelay() {
        return pollChannelConfig.isTcpNoDelay();
    }

    @Override
    public void setKeepAlive(boolean keepAlive) {
        pollChannelConfig.setKeepAlive(keepAlive);
        sendChannelConfig.setKeepAlive(keepAlive);
    }

    @Override
    public void setPerformancePreferences(int connectionTime, int latency,
            int bandwidth) {
        pollChannelConfig.setPerformancePreferences(connectionTime, latency,
                bandwidth);
        sendChannelConfig.setPerformancePreferences(connectionTime, latency,
                bandwidth);
    }

    @Override
    public void setReceiveBufferSize(int receiveBufferSize) {
        pollChannelConfig.setReceiveBufferSize(receiveBufferSize);
        sendChannelConfig.setReceiveBufferSize(receiveBufferSize);
    }

    @Override
    public void setReuseAddress(boolean reuseAddress) {
        pollChannelConfig.setReuseAddress(reuseAddress);
        sendChannelConfig.setReuseAddress(reuseAddress);
    }

    @Override
    public void setSendBufferSize(int sendBufferSize) {
        pollChannelConfig.setSendBufferSize(sendBufferSize);
        sendChannelConfig.setSendBufferSize(sendBufferSize);
    }

    @Override
    public void setSoLinger(int soLinger) {
        pollChannelConfig.setSoLinger(soLinger);
        sendChannelConfig.setSoLinger(soLinger);
    }

    @Override
    public void setTcpNoDelay(boolean tcpNoDelay) {
        pollChannelConfig.setTcpNoDelay(true);
        sendChannelConfig.setTcpNoDelay(true);
    }

    @Override
    public void setTrafficClass(int trafficClass) {
        pollChannelConfig.setTrafficClass(1);
        sendChannelConfig.setTrafficClass(1);
    }
}
