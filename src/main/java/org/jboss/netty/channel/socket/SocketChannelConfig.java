/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.channel.socket;

import java.net.Socket;

import org.jboss.netty.channel.ChannelConfig;

/**
 * A {@link ChannelConfig} for a {@link SocketChannel}.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
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
