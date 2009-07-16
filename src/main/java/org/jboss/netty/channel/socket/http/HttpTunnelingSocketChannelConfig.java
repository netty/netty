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
import java.util.Map.Entry;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;

import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.channel.ChannelConfig;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.socket.SocketChannel;
import org.jboss.netty.channel.socket.SocketChannelConfig;
import org.jboss.netty.util.internal.ConversionUtil;

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
 * </tr><tr>
 * <td>{@code "enabledSslCiperSuites"}</td><td>{@link #setEnabledSslCipherSuites(String[])}</td>
 * </tr><tr>
 * <td>{@code "enabledSslProtocols"}</td><td>{@link #setEnabledSslProtocols(String[])}</td>
 * </tr><tr>
 * <td>{@code "enableSslSessionCreation"}</td><td>{@link #setEnableSslSessionCreation(boolean)}</td>
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
    private volatile String serverName;
    private volatile String serverPath = "/netty-tunnel";
    private volatile SSLContext sslContext;
    private volatile String[] enabledSslCipherSuites;
    private volatile String[] enabledSslProtocols;
    private volatile boolean enableSslSessionCreation = true;

    /**
     * Creates a new instance.
     */
    HttpTunnelingSocketChannelConfig(HttpTunnelingClientSocketChannel channel) {
        this.channel = channel;
    }

    /**
     * Returns the host name of the HTTP server.  If {@code null}, the
     * {@code "Host"} header is not sent by the HTTP tunneling client.
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * Sets the host name of the HTTP server.  If {@code null}, the
     * {@code "Host"} header is not sent by the HTTP tunneling client.
     */
    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    /**
     * Returns the path where the {@link HttpTunnelingServlet} is mapped to.
     * The default value is {@code "/netty-tunnel"}.
     */
    public String getServerPath() {
        return serverPath;
    }

    /**
     * Sets the path where the {@link HttpTunnelingServlet} is mapped to.
     * The default value is {@code "/netty-tunnel"}.
     */
    public void setServerPath(String serverPath) {
        if (serverPath == null) {
            throw new NullPointerException("serverPath");
        }
        this.serverPath = serverPath;
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

    /**
     * Returns the cipher suites enabled for use on an {@link SSLEngine}.
     * If {@code null}, the default value will be used.
     *
     * @see SSLEngine#getEnabledCipherSuites()
     */
    public String[] getEnabledSslCipherSuites() {
        String[] suites = enabledSslCipherSuites;
        if (suites == null) {
            return null;
        } else {
            return suites.clone();
        }
    }

    /**
     * Sets the cipher suites enabled for use on an {@link SSLEngine}.
     * If {@code null}, the default value will be used.
     *
     * @see SSLEngine#setEnabledCipherSuites(String[])
     */
    public void setEnabledSslCipherSuites(String[] suites) {
        if (suites == null) {
            enabledSslCipherSuites = null;
        } else {
            enabledSslCipherSuites = suites.clone();
        }
    }

    /**
     * Returns the protocol versions enabled for use on an {@link SSLEngine}.
     *
     * @see SSLEngine#getEnabledProtocols()
     */
    public String[] getEnabledSslProtocols() {
        String[] protocols = enabledSslProtocols;
        if (protocols == null) {
            return null;
        } else {
            return protocols.clone();
        }
    }

    /**
     * Sets the protocol versions enabled for use on an {@link SSLEngine}.
     *
     * @see SSLEngine#setEnabledProtocols(String[])
     */
    public void setEnabledSslProtocols(String[] protocols) {
        if (protocols == null) {
            enabledSslProtocols = null;
        } else {
            enabledSslProtocols = protocols.clone();
        }
    }

    /**
     * Returns {@code true} if new {@link SSLSession}s may be established by
     * an {@link SSLEngine}.
     *
     * @see SSLEngine#getEnableSessionCreation()
     */
    public boolean isEnableSslSessionCreation() {
        return enableSslSessionCreation;
    }

    /**
     * Sets whether new {@link SSLSession}s may be established by an
     * {@link SSLEngine}.
     *
     * @see SSLEngine#setEnableSessionCreation(boolean)
     */
    public void setEnableSslSessionCreation(boolean flag) {
        enableSslSessionCreation = flag;
    }

    public void setOptions(Map<String, Object> options) {
        for (Entry<String, Object> e: options.entrySet()) {
            setOption(e.getKey(), e.getValue());
        }
    }

    public boolean setOption(String key, Object value) {
        if (channel.realChannel.getConfig().setOption(key, value)) {
            return true;
        }

        if (key.equals("serverName")){
            setServerName(String.valueOf(value));
        } else if (key.equals("serverPath")){
            setServerPath(String.valueOf(value));
        } else if (key.equals("sslContext")) {
            setSslContext((SSLContext) value);
        } else if (key.equals("enabledSslCipherSuites")){
            setEnabledSslCipherSuites(ConversionUtil.toStringArray(value));
        } else if (key.equals("enabledSslProtocols")){
            setEnabledSslProtocols(ConversionUtil.toStringArray(value));
        } else if (key.equals("enableSslSessionCreation")){
            setEnableSslSessionCreation(ConversionUtil.toBoolean(value));
        } else {
            return false;
        }

        return true;
    }

    public int getReceiveBufferSize() {
        return channel.realChannel.getConfig().getReceiveBufferSize();
    }

    public int getSendBufferSize() {
        return channel.realChannel.getConfig().getSendBufferSize();
    }

    public int getSoLinger() {
        return channel.realChannel.getConfig().getSoLinger();
    }

    public int getTrafficClass() {
        return channel.realChannel.getConfig().getTrafficClass();
    }

    public boolean isKeepAlive() {
        return channel.realChannel.getConfig().isKeepAlive();
    }

    public boolean isReuseAddress() {
        return channel.realChannel.getConfig().isReuseAddress();
    }

    public boolean isTcpNoDelay() {
        return channel.realChannel.getConfig().isTcpNoDelay();
    }

    public void setKeepAlive(boolean keepAlive) {
        channel.realChannel.getConfig().setKeepAlive(keepAlive);
    }

    public void setPerformancePreferences(
          int connectionTime, int latency, int bandwidth) {
        channel.realChannel.getConfig().setPerformancePreferences(connectionTime, latency, bandwidth);
    }

    public void setReceiveBufferSize(int receiveBufferSize) {
        channel.realChannel.getConfig().setReceiveBufferSize(receiveBufferSize);
    }

    public void setReuseAddress(boolean reuseAddress) {
        channel.realChannel.getConfig().setReuseAddress(reuseAddress);
    }

    public void setSendBufferSize(int sendBufferSize) {
        channel.realChannel.getConfig().setSendBufferSize(sendBufferSize);

    }

    public void setSoLinger(int soLinger) {
        channel.realChannel.getConfig().setSoLinger(soLinger);
    }

    public void setTcpNoDelay(boolean tcpNoDelay) {
        channel.realChannel.getConfig().setTcpNoDelay(tcpNoDelay);
    }

    public void setTrafficClass(int trafficClass) {
        channel.realChannel.getConfig().setTrafficClass(trafficClass);
    }

    public ChannelBufferFactory getBufferFactory() {
        return channel.realChannel.getConfig().getBufferFactory();
    }

    public int getConnectTimeoutMillis() {
        return channel.realChannel.getConfig().getConnectTimeoutMillis();
    }

    public ChannelPipelineFactory getPipelineFactory() {
        return channel.realChannel.getConfig().getPipelineFactory();
    }

    @Deprecated
    public int getWriteTimeoutMillis() {
        return channel.realChannel.getConfig().getWriteTimeoutMillis();
    }

    public void setBufferFactory(ChannelBufferFactory bufferFactory) {
        channel.realChannel.getConfig().setBufferFactory(bufferFactory);
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        channel.realChannel.getConfig().setConnectTimeoutMillis(connectTimeoutMillis);
    }

    public void setPipelineFactory(ChannelPipelineFactory pipelineFactory) {
        channel.realChannel.getConfig().setPipelineFactory(pipelineFactory);
    }

    @Deprecated
    public void setWriteTimeoutMillis(int writeTimeoutMillis) {
        channel.realChannel.getConfig().setWriteTimeoutMillis(writeTimeoutMillis);
    }
}