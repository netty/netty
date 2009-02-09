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
package org.jboss.netty.channel.socket.servlet;

import java.net.Socket;
import java.net.SocketException;
import java.util.Map;
import java.util.Map.Entry;

import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.buffer.HeapChannelBufferFactory;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.socket.SocketChannelConfig;
import org.jboss.netty.util.ConversionUtil;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public class ServletSocketChannelConfig implements SocketChannelConfig {

    final Socket socket;

    private volatile ChannelBufferFactory bufferFactory = HeapChannelBufferFactory.getInstance();

    private volatile int connectTimeoutMillis = 10000; // 10 seconds

    private Integer trafficClass;

    private Boolean tcpNoDelay;

    private Integer soLinger;

    private Integer sendBufferSize;

    private Boolean reuseAddress;

    private Integer receiveBufferSize;

    private Integer connectionTime;

    private Integer latency;

    private Integer bandwidth;

    private Boolean keepAlive;

    /**
     * Creates a new instance.
     */
    public ServletSocketChannelConfig(Socket socket) {
        this.socket = socket;
    }

    public void setOptions(Map<String, Object> options) {
        for (Entry<String, Object> e : options.entrySet()) {
            setOption(e.getKey(), e.getValue());
        }
    }

    /**
     * Sets an individual option.  You can override this method to support
     * additional configuration parameters.
     */
    protected boolean setOption(String key, Object value) {
        if (key.equals("receiveBufferSize")) {
            setReceiveBufferSize(ConversionUtil.toInt(value));
        }
        else if (key.equals("sendBufferSize")) {
            setSendBufferSize(ConversionUtil.toInt(value));
        }
        else if (key.equals("tcpNoDelay")) {
            setTcpNoDelay(ConversionUtil.toBoolean(value));
        }
        else if (key.equals("keepAlive")) {
            setKeepAlive(ConversionUtil.toBoolean(value));
        }
        else if (key.equals("reuseAddress")) {
            setReuseAddress(ConversionUtil.toBoolean(value));
        }
        else if (key.equals("soLinger")) {
            setSoLinger(ConversionUtil.toInt(value));
        }
        else if (key.equals("trafficClass")) {
            setTrafficClass(ConversionUtil.toInt(value));
        }
        else if (key.equals("writeTimeoutMillis")) {
            setWriteTimeoutMillis(ConversionUtil.toInt(value));
        }
        else if (key.equals("connectTimeoutMillis")) {
            setConnectTimeoutMillis(ConversionUtil.toInt(value));
        }
        else if (key.equals("pipelineFactory")) {
            setPipelineFactory((ChannelPipelineFactory) value);
        }
        else if (key.equals("bufferFactory")) {
            setBufferFactory((ChannelBufferFactory) value);
        }
        else {
            return false;
        }
        return true;
    }

    public int getReceiveBufferSize() {
        try {
            return socket.getReceiveBufferSize();
        }
        catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    public int getSendBufferSize() {
        try {
            return socket.getSendBufferSize();
        }
        catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    public int getSoLinger() {
        try {
            return socket.getSoLinger();
        }
        catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    public int getTrafficClass() {
        try {
            return socket.getTrafficClass();
        }
        catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    public boolean isKeepAlive() {
        try {
            return socket.getKeepAlive();
        }
        catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    public boolean isReuseAddress() {
        try {
            return socket.getReuseAddress();
        }
        catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    public boolean isTcpNoDelay() {
        try {
            return socket.getTcpNoDelay();
        }
        catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    public void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
        try {
            socket.setKeepAlive(keepAlive);
        }
        catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    public void setPerformancePreferences(
          int connectionTime, int latency, int bandwidth) {
        this.connectionTime = connectionTime;
        this.latency = latency;
        this.bandwidth = bandwidth;
        socket.setPerformancePreferences(connectionTime, latency, bandwidth);

    }

    public void setReceiveBufferSize(int receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
        try {
            socket.setReceiveBufferSize(receiveBufferSize);
        }
        catch (SocketException e) {
            throw new ChannelException(e);
        }

    }

    public void setReuseAddress(boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
        try {
            socket.setReuseAddress(reuseAddress);
        }
        catch (SocketException e) {
            throw new ChannelException(e);
        }

    }

    public void setSendBufferSize(int sendBufferSize) {
        this.sendBufferSize = sendBufferSize;
        if (socket != null) {
            try {
                socket.setSendBufferSize(sendBufferSize);
            }
            catch (SocketException e) {
                throw new ChannelException(e);
            }
        }
    }

    public void setSoLinger(int soLinger) {
        this.soLinger = soLinger;
        try {
            if (soLinger < 0) {
                socket.setSoLinger(false, 0);
            }
            else {
                socket.setSoLinger(true, soLinger);
            }
        }
        catch (SocketException e) {
            throw new ChannelException(e);
        }

    }

    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
        try {
            socket.setTcpNoDelay(tcpNoDelay);
        }
        catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    public void setTrafficClass(int trafficClass) {
        this.trafficClass = trafficClass;
        try {
            socket.setTrafficClass(trafficClass);
        }
        catch (SocketException e) {
            throw new ChannelException(e);
        }

    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public ChannelBufferFactory getBufferFactory() {
        return bufferFactory;
    }

    public void setBufferFactory(ChannelBufferFactory bufferFactory) {
        if (bufferFactory == null) {
            throw new NullPointerException("bufferFactory");
        }
        this.bufferFactory = bufferFactory;
    }

    public ChannelPipelineFactory getPipelineFactory() {
        return null;
    }

    public int getWriteTimeoutMillis() {
        return 0;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        if (connectTimeoutMillis < 0) {
            throw new IllegalArgumentException("connectTimeoutMillis: " + connectTimeoutMillis);
        }
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public void setPipelineFactory(ChannelPipelineFactory pipelineFactory) {
        // Unused
    }

    public void setWriteTimeoutMillis(int writeTimeoutMillis) {
        // Unused
    }

    ServletSocketChannelConfig copyConfig(Socket socket) {
        ServletSocketChannelConfig config = new ServletSocketChannelConfig(socket);
        config.setConnectTimeoutMillis(connectTimeoutMillis);
        if (trafficClass != null) {
            config.setTrafficClass(trafficClass);
        }
        if (tcpNoDelay != null) {
            config.setTcpNoDelay(tcpNoDelay);
        }

        if (soLinger != null) {
            config.setSoLinger(soLinger);
        }

        if (sendBufferSize != null) {
            config.setSendBufferSize(sendBufferSize);
        }

        if (reuseAddress != null) {
            config.setReuseAddress(reuseAddress);
        }

        if (receiveBufferSize != null) {
            config.setReceiveBufferSize(receiveBufferSize);
        }

        if (keepAlive != null) {
            config.setKeepAlive(keepAlive);
        }
        if (connectionTime != null) {
            config.setPerformancePreferences(connectionTime, latency, bandwidth);
        }

        return config;
    }
}