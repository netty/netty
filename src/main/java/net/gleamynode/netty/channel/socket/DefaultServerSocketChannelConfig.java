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

import java.net.ServerSocket;
import java.net.SocketException;
import java.util.Map;
import java.util.Map.Entry;

import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.ChannelException;
import net.gleamynode.netty.pipeline.PipelineFactory;
import net.gleamynode.netty.util.ConvertUtil;

public class DefaultServerSocketChannelConfig implements ServerSocketChannelConfig {

    private final ServerSocket socket;
    private volatile int backlog;
    private volatile PipelineFactory<ChannelEvent> pipelineFactory;

    public DefaultServerSocketChannelConfig(ServerSocket socket) {
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
        } else if (key.equals("reuseAddress")) {
            setReuseAddress(ConvertUtil.toBoolean(value));
        } else if (key.equals("backlog")) {
            setBacklog(ConvertUtil.toInt(value));
        } else {
            return false;
        }
        return true;
    }

    public boolean isReuseAddress() {
        try {
            return socket.getReuseAddress();
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

    public int getReceiveBufferSize() {
        try {
            return socket.getReceiveBufferSize();
        } catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    public void setReceiveBufferSize(int receiveBufferSize) {
        try {
            socket.setReceiveBufferSize(receiveBufferSize);
        } catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
        socket.setPerformancePreferences(connectionTime, latency, bandwidth);
    }

    public PipelineFactory<ChannelEvent> getPipelineFactory() {
        return pipelineFactory;
    }

    public void setPipelineFactory(PipelineFactory<ChannelEvent> pipelineFactory) {
        if (pipelineFactory == null) {
            throw new NullPointerException("pipelineFactory");
        }
        this.pipelineFactory = pipelineFactory;
    }

    public int getBacklog() {
        return backlog;
    }

    public void setBacklog(int backlog) {
        if (backlog < 1) {
            throw new IllegalArgumentException("backlog: " + backlog);
        }
        this.backlog = backlog;
    }

    public int getConnectTimeoutMillis() {
        return 0;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        // Unused
    }

    public int getWriteTimeoutMillis() {
        return 0;
    }

    public void setWriteTimeoutMillis(int writeTimeoutMillis) {
        // Unused
    }
}
