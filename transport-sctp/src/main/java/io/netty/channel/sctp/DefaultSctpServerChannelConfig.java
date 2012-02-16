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
package io.netty.channel.sctp;

import static com.sun.nio.sctp.SctpStandardSocketOptions.*;

import io.netty.channel.ChannelException;
import io.netty.channel.DefaultServerChannelConfig;
import io.netty.util.internal.ConversionUtil;

import java.io.IOException;

/**
 * The default {@link io.netty.channel.socket.ServerSocketChannelConfig} implementation for SCTP.
 */
public class DefaultSctpServerChannelConfig extends DefaultServerChannelConfig
        implements SctpServerChannelConfig {

    private final com.sun.nio.sctp.SctpServerChannel serverChannel;
    private volatile int backlog;

    /**
     * Creates a new instance.
     */
    public DefaultSctpServerChannelConfig(com.sun.nio.sctp.SctpServerChannel serverChannel) {
        if (serverChannel == null) {
            throw new NullPointerException("serverChannel");
        }
        this.serverChannel = serverChannel;
    }

    @Override
    public boolean setOption(String key, Object value) {
        if (super.setOption(key, value)) {
            return true;
        }

        if (key.equals("sctpInitMaxStreams")) {
            final Integer maxInOutStreams = ConversionUtil.toInt(value);
            setInitMaxStreams(InitMaxStreams.create(maxInOutStreams, maxInOutStreams));
        } else if (key.equals("backlog")) {
            setBacklog(ConversionUtil.toInt(value));
        } else {
            return false;
        }
        return true;
    }

    @Override
    public int getSendBufferSize() {
        try {
            return serverChannel.getOption(SO_SNDBUF);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setSendBufferSize(int sendBufferSize) {
        try {
            serverChannel.setOption(SO_SNDBUF, sendBufferSize);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public int getReceiveBufferSize() {
        try {
            return serverChannel.getOption(SO_RCVBUF);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setReceiveBufferSize(int receiveBufferSize) {
        try {
            serverChannel.setOption(SO_RCVBUF, receiveBufferSize);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public InitMaxStreams getInitMaxStreams() {
        try {
            return serverChannel.getOption(SCTP_INIT_MAXSTREAMS);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setInitMaxStreams(InitMaxStreams initMaxStreams) {
        try {
            serverChannel.setOption(SCTP_INIT_MAXSTREAMS, initMaxStreams);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public int getBacklog() {
        return backlog;
    }

    @Override
    public void setBacklog(int backlog) {
        if (backlog < 0) {
            throw new IllegalArgumentException("backlog: " + backlog);
        }
        this.backlog = backlog;
    }
}
