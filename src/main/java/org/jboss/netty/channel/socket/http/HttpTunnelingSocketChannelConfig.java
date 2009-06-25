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
package org.jboss.netty.channel.socket.http;

import java.util.Map;

import javax.net.ssl.SSLContext;

import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.channel.ChannelConfig;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.socket.SocketChannel;
import org.jboss.netty.channel.socket.SocketChannelConfig;

/**
 * The {@link ChannelConfig} of a client-side HTTP tunneling
 * {@link SocketChannel}.  A {@link SocketChannel} created by
 * {@link HttpTunnelingClientSocketChannelFactory} will return an instance of
 * this configuration type for {@link SocketChannel#getConfig()}.
 *
 * <h3>Available options</h3>
 *
 * In addition to the options provided by {@link SocketChannelConfig},
 * {@link HttpTunnelingSocketChannelConfig} allows the following options in
 * the option map:
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Name</th><th>Associated setter method</th>
 * </tr><tr>
 * <td>{@code "sslContext"}</td><td>{@link #setSslContext(SSLContext)}</td>
 * </tr>
 * </table>
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public final class HttpTunnelingSocketChannelConfig implements SocketChannelConfig {

    private final HttpTunnelingClientSocketChannel channel;
    private volatile SSLContext sslContext;

    /**
     * Creates a new instance.
     */
    HttpTunnelingSocketChannelConfig(HttpTunnelingClientSocketChannel channel) {
        this.channel = channel;
    }

    /**
     * Returns the {@link SSLContext} which is used to establish an HTTPS
     * connection.  If {@code null}, a plain-text HTTP connection is established.
     */
    public SSLContext getSslContext() {
        return sslContext;
    }

    /**
     * Sets the {@link SSLContext} which is used to establish an HTTPS connection.
     * If {@code null}, a plain-text HTTP connection is established.
     */
    public void setSslContext(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    public void setOptions(Map<String, Object> options) {
        channel.channel.getConfig().setOptions(options);
        SSLContext sslContext = (SSLContext) options.get("sslContext");
        if (sslContext != null) {
            setSslContext(sslContext);
        }
    }

    public boolean setOption(String key, Object value) {
        if (channel.channel.getConfig().setOption(key, value)) {
            return true;
        }

        if (key.equals("sslContext")) {
            setSslContext((SSLContext) value);
        } else {
            return false;
        }

        return true;
    }

    public int getReceiveBufferSize() {
        return channel.channel.getConfig().getReceiveBufferSize();
    }

    public int getSendBufferSize() {
        return channel.channel.getConfig().getSendBufferSize();
    }

    public int getSoLinger() {
        return channel.channel.getConfig().getSoLinger();
    }

    public int getTrafficClass() {
        return channel.channel.getConfig().getTrafficClass();
    }

    public boolean isKeepAlive() {
        return channel.channel.getConfig().isKeepAlive();
    }

    public boolean isReuseAddress() {
        return channel.channel.getConfig().isReuseAddress();
    }

    public boolean isTcpNoDelay() {
        return channel.channel.getConfig().isTcpNoDelay();
    }

    public void setKeepAlive(boolean keepAlive) {
        channel.channel.getConfig().setKeepAlive(keepAlive);
    }

    public void setPerformancePreferences(
          int connectionTime, int latency, int bandwidth) {
        channel.channel.getConfig().setPerformancePreferences(connectionTime, latency, bandwidth);
    }

    public void setReceiveBufferSize(int receiveBufferSize) {
        channel.channel.getConfig().setReceiveBufferSize(receiveBufferSize);
    }

    public void setReuseAddress(boolean reuseAddress) {
        channel.channel.getConfig().setReuseAddress(reuseAddress);
    }

    public void setSendBufferSize(int sendBufferSize) {
        channel.channel.getConfig().setSendBufferSize(sendBufferSize);

    }

    public void setSoLinger(int soLinger) {
        channel.channel.getConfig().setSoLinger(soLinger);
    }

    public void setTcpNoDelay(boolean tcpNoDelay) {
        channel.channel.getConfig().setTcpNoDelay(tcpNoDelay);
    }

    public void setTrafficClass(int trafficClass) {
        channel.channel.getConfig().setTrafficClass(trafficClass);
    }

    public ChannelBufferFactory getBufferFactory() {
        return channel.channel.getConfig().getBufferFactory();
    }

    public int getConnectTimeoutMillis() {
        return channel.channel.getConfig().getConnectTimeoutMillis();
    }

    public ChannelPipelineFactory getPipelineFactory() {
        return channel.channel.getConfig().getPipelineFactory();
    }

    @Deprecated
    public int getWriteTimeoutMillis() {
        return channel.channel.getConfig().getWriteTimeoutMillis();
    }

    public void setBufferFactory(ChannelBufferFactory bufferFactory) {
        channel.channel.getConfig().setBufferFactory(bufferFactory);
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        channel.channel.getConfig().setConnectTimeoutMillis(connectTimeoutMillis);
    }

    public void setPipelineFactory(ChannelPipelineFactory pipelineFactory) {
        channel.channel.getConfig().setPipelineFactory(pipelineFactory);
    }

    @Deprecated
    public void setWriteTimeoutMillis(int writeTimeoutMillis) {
        channel.channel.getConfig().setWriteTimeoutMillis(writeTimeoutMillis);
    }
}