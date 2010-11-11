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
package org.jboss.netty.channel.socket;

import java.net.Socket;

import org.jboss.netty.channel.ChannelConfig;

/**
 * A {@link ChannelConfig} for a {@link SocketChannel}.
 *
 * <h3>Available options</h3>
 *
 * In addition to the options provided by {@link ChannelConfig},
 * {@link SocketChannelConfig} allows the following options in the option map:
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Name</th><th>Associated setter method</th>
 * </tr><tr>
 * <td>{@code "keepAlive"}</td><td>{@link #setKeepAlive(boolean)}</td>
 * </tr><tr>
 * <td>{@code "reuseAddress"}</td><td>{@link #setReuseAddress(boolean)}</td>
 * </tr><tr>
 * <td>{@code "soLinger"}</td><td>{@link #setSoLinger(int)}</td>
 * </tr><tr>
 * <td>{@code "tcpNoDelay"}</td><td>{@link #setTcpNoDelay(boolean)}</td>
 * </tr><tr>
 * <td>{@code "receiveBufferSize"}</td><td>{@link #setReceiveBufferSize(int)}</td>
 * </tr><tr>
 * <td>{@code "sendBufferSize"}</td><td>{@link #setSendBufferSize(int)}</td>
 * </tr><tr>
 * <td>{@code "trafficClass"}</td><td>{@link #setTrafficClass(int)}</td>
 * </tr>
 * </table>
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public interface SocketChannelConfig extends ChannelConfig {

    /**
     * Gets the <a href="http://java.sun.com/javase/6/docs/technotes/guides/net/socketOpt.html">{@code SO_TCPNODELAY}</a> option.
     */
    boolean isTcpNoDelay();

    /**
     * Sets the <a href="http://java.sun.com/javase/6/docs/technotes/guides/net/socketOpt.html">{@code SO_TCPNODELAY}</a> option.
     */
    void setTcpNoDelay(boolean tcpNoDelay);

    /**
     * Gets the <a href="http://java.sun.com/javase/6/docs/technotes/guides/net/socketOpt.html">{@code SO_LINGER}</a> option.
     */
    int getSoLinger();

    /**
     * Sets the <a href="http://java.sun.com/javase/6/docs/technotes/guides/net/socketOpt.html">{@code SO_LINGER}</a> option.
     */
    void setSoLinger(int soLinger);

    /**
     * Gets the <a href="http://java.sun.com/javase/6/docs/technotes/guides/net/socketOpt.html">{@code SO_SNDBUF}</a> option.
     */
    int getSendBufferSize();

    /**
     * Sets the <a href="http://java.sun.com/javase/6/docs/technotes/guides/net/socketOpt.html">{@code SO_SNDBUF}</a> option.
     */
    void setSendBufferSize(int sendBufferSize);

    /**
     * Gets the <a href="http://java.sun.com/javase/6/docs/technotes/guides/net/socketOpt.html">{@code SO_RCVBUF}</a> option.
     */
    int getReceiveBufferSize();

    /**
     * Gets the <a href="http://java.sun.com/javase/6/docs/technotes/guides/net/socketOpt.html">{@code SO_RCVBUF}</a> option.
     */
    void setReceiveBufferSize(int receiveBufferSize);

    /**
     * Gets the <a href="http://java.sun.com/javase/6/docs/technotes/guides/net/socketOpt.html">{@code SO_KEEPALIVE}</a> option.
     */
    boolean isKeepAlive();

    /**
     * Sets the <a href="http://java.sun.com/javase/6/docs/technotes/guides/net/socketOpt.html">{@code SO_KEEPALIVE}</a> option.
     */
    void setKeepAlive(boolean keepAlive);

    /**
     * Gets the traffic class.
     */
    int getTrafficClass();

    /**
     * Sets the traffic class as specified in {@link Socket#setTrafficClass(int)}.
     */
    void setTrafficClass(int trafficClass);

    /**
     * Gets the <a href="http://java.sun.com/javase/6/docs/technotes/guides/net/socketOpt.html">{@code SO_REUSEADDR}</a> option.
     */
    boolean isReuseAddress();

    /**
     * Sets the <a href="http://java.sun.com/javase/6/docs/technotes/guides/net/socketOpt.html">{@code SO_REUSEADDR}</a> option.
     */
    void setReuseAddress(boolean reuseAddress);

    /**
     * Sets the performance preferences as specified in
     * {@link Socket#setPerformancePreferences(int, int, int)}.
     */
    void setPerformancePreferences(
            int connectionTime, int latency, int bandwidth);
}
