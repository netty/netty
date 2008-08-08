/*
 * Copyright (C) 2008  Trustin Heuiseung Lee
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, 5th Floor, Boston, MA 02110-1301 USA
 */
package net.gleamynode.netty.channel.socket;

import java.net.Socket;
import java.net.SocketException;
import java.util.Map;
import java.util.Map.Entry;

import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.ChannelException;
import net.gleamynode.netty.pipeline.PipelineFactory;
import net.gleamynode.netty.util.ConvertUtil;

public class DefaultSocketChannelConfig implements SocketChannelConfig {

    private final Socket socket;
    private volatile int connectTimeoutMillis = 10000; // 10 seconds

    public DefaultSocketChannelConfig(Socket socket) {
        if (socket == null) {
            throw new NullPointerException("socket");
        }
        this.socket = socket;
    }

    public void setOptions(Map<String, Object> options) {
        for (Entry<String, Object> e: options.entrySet()) {
            setOption(e.getKey(), e.getValue());
        }
    }

    protected boolean setOption(String key, Object value) {
        if (key.equals("receiveBufferSize")) {
            setReceiveBufferSize(ConvertUtil.toInt(value));
        } else if (key.equals("sendBufferSize")) {
            setSendBufferSize(ConvertUtil.toInt(value));
        } else if (key.equals("tcpNoDelay")) {
            setTcpNoDelay(ConvertUtil.toBoolean(value));
        } else if (key.equals("keepAlive")) {
            setKeepAlive(ConvertUtil.toBoolean(value));
        } else if (key.equals("reuseAddress")) {
            setReuseAddress(ConvertUtil.toBoolean(value));
        } else if (key.equals("soLinger")) {
            setSoLinger(ConvertUtil.toInt(value));
        } else if (key.equals("trafficClass")) {
            setTrafficClass(ConvertUtil.toInt(value));
        } else if (key.equals("writeTimeoutMillis")) {
            setWriteTimeoutMillis(ConvertUtil.toInt(value));
        } else if (key.equals("connectTimeoutMillis")) {
            setConnectTimeoutMillis(ConvertUtil.toInt(value));
        } else if (key.equals("pipelineFactory")) {
            setPipelineFactory(toPipelineFactory(value));
        } else {
            return false;
        }
        return true;
    }

    public int getReceiveBufferSize() {
        try {
            return socket.getReceiveBufferSize();
        } catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    public int getSendBufferSize() {
        try {
            return socket.getSendBufferSize();
        } catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    public int getSoLinger() {
        try {
            return socket.getSoLinger();
        } catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    public int getTrafficClass() {
        try {
            return socket.getTrafficClass();
        } catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    public boolean isKeepAlive() {
        try {
            return socket.getKeepAlive();
        } catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    public boolean isReuseAddress() {
        try {
            return socket.getReuseAddress();
        } catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    public boolean isTcpNoDelay() {
        try {
            return socket.getTcpNoDelay();
        } catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    public void setKeepAlive(boolean keepAlive) {
        try {
            socket.setKeepAlive(keepAlive);
        } catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    public void setPerformancePreferences(
            int connectionTime, int latency, int bandwidth) {
        socket.setPerformancePreferences(connectionTime, latency, bandwidth);
    }

    public void setReceiveBufferSize(int receiveBufferSize) {
        try {
            socket.setReceiveBufferSize(receiveBufferSize);
        } catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    public void setReuseAddress(boolean reuseAddress) {
        try {
            socket.setReuseAddress(reuseAddress);
        } catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    public void setSendBufferSize(int sendBufferSize) {
        try {
            socket.setSendBufferSize(sendBufferSize);
        } catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    public void setSoLinger(int soLinger) {
        try {
            if (soLinger < 0) {
                socket.setSoLinger(false, 0);
            } else {
                socket.setSoLinger(true, soLinger);
            }
        } catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    public void setTcpNoDelay(boolean tcpNoDelay) {
        try {
            socket.setTcpNoDelay(tcpNoDelay);
        } catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    public void setTrafficClass(int trafficClass) {
        try {
            socket.setTrafficClass(trafficClass);
        } catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public PipelineFactory<ChannelEvent> getPipelineFactory() {
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

    public void setPipelineFactory(PipelineFactory<ChannelEvent> pipelineFactory) {
        // Unused
    }

    public void setWriteTimeoutMillis(int writeTimeoutMillis) {
        // Unused
    }

    @SuppressWarnings("unchecked")
    private static PipelineFactory<ChannelEvent> toPipelineFactory(Object value) {
        return (PipelineFactory<ChannelEvent>) value;
    }
}
