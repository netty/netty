/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel.socket.httptunnel;

import java.net.SocketAddress;

import org.jboss.netty.channel.DefaultChannelConfig;
import org.jboss.netty.channel.socket.SocketChannelConfig;

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
 * </tr><tr>
 * <td>{@code "proxyAddress"}</td><td>{@link #setProxyAddress(SocketAddress)}</td>
 * </tr>
 * </table>
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @version $Rev$, $Date$
 */
public class HttpTunnelClientChannelConfig extends DefaultChannelConfig implements SocketChannelConfig {

    public static final String PROXY_ADDRESS_OPTION = "proxyAddress";

    private final SocketChannelConfig sendChannelConfig;
    private final SocketChannelConfig pollChannelConfig;

    private SocketAddress proxyAddress;

    public HttpTunnelClientChannelConfig(SocketChannelConfig sendChannelConfig, SocketChannelConfig pollChannelConfig) {
        this.sendChannelConfig = sendChannelConfig;
        this.pollChannelConfig = pollChannelConfig;
    }

    /* HTTP TUNNEL SPECIFIC CONFIGURATION */

    @Override
    public boolean setOption(String key, Object value) {
        if (super.setOption(key, value)) {
            return true;
        }

        if (PROXY_ADDRESS_OPTION.equals(key)) {
            setProxyAddress((SocketAddress) value);
        } else {
            return false;
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

    public int getReceiveBufferSize() {
        return pollChannelConfig.getReceiveBufferSize();
    }

    public int getSendBufferSize() {
        return pollChannelConfig.getSendBufferSize();
    }

    public int getSoLinger() {
        return pollChannelConfig.getSoLinger();
    }

    public int getTrafficClass() {
        return pollChannelConfig.getTrafficClass();
    }

    public boolean isKeepAlive() {
        return pollChannelConfig.isKeepAlive();
    }

    public boolean isReuseAddress() {
        return pollChannelConfig.isReuseAddress();
    }

    public boolean isTcpNoDelay() {
        return pollChannelConfig.isTcpNoDelay();
    }

    public void setKeepAlive(boolean keepAlive) {
        pollChannelConfig.setKeepAlive(keepAlive);
        sendChannelConfig.setKeepAlive(keepAlive);
    }

    public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
        pollChannelConfig.setPerformancePreferences(connectionTime, latency, bandwidth);
        sendChannelConfig.setPerformancePreferences(connectionTime, latency, bandwidth);
    }

    public void setReceiveBufferSize(int receiveBufferSize) {
        pollChannelConfig.setReceiveBufferSize(receiveBufferSize);
        sendChannelConfig.setReceiveBufferSize(receiveBufferSize);
    }

    public void setReuseAddress(boolean reuseAddress) {
        pollChannelConfig.setReuseAddress(reuseAddress);
        sendChannelConfig.setReuseAddress(reuseAddress);
    }

    public void setSendBufferSize(int sendBufferSize) {
        pollChannelConfig.setSendBufferSize(sendBufferSize);
        sendChannelConfig.setSendBufferSize(sendBufferSize);
    }

    public void setSoLinger(int soLinger) {
        pollChannelConfig.setSoLinger(soLinger);
        sendChannelConfig.setSoLinger(soLinger);
    }

    public void setTcpNoDelay(boolean tcpNoDelay) {
        pollChannelConfig.setTcpNoDelay(true);
        sendChannelConfig.setTcpNoDelay(true);
    }

    public void setTrafficClass(int trafficClass) {
        pollChannelConfig.setTrafficClass(1);
        sendChannelConfig.setTrafficClass(1);
    }
}
