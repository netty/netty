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

import com.sun.nio.sctp.SctpChannel;
import static com.sun.nio.sctp.SctpStandardSocketOptions.*;
import io.netty.channel.ChannelException;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.socket.nio.NioSocketChannelConfig;
import io.netty.util.internal.ConversionUtil;

import java.io.IOException;

/**
 * The default {@link NioSocketChannelConfig} implementation for SCTP.
 */
class DefaultSctpChannelConfig extends DefaultChannelConfig implements SctpChannelConfig {

    private SctpChannel channel;

    DefaultSctpChannelConfig(SctpChannel channel) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        this.channel = channel;
    }

    @Override
    public boolean setOption(String key, Object value) {
        if (super.setOption(key, value)) {
            return true;
        }

        if (key.equals("receiveBufferSize")) {
            setReceiveBufferSize(ConversionUtil.toInt(value));
        } else if (key.equals("sendBufferSize")) {
            setSendBufferSize(ConversionUtil.toInt(value));
        } else if (key.equals("sctpNoDelay")) {
            setSctpNoDelay(ConversionUtil.toBoolean(value));
        } else if (key.equals("sctpInitMaxStreams")) {
            setInitMaxStreams((InitMaxStreams) value);
        } else {
            return false;
        }
        return true;
    }

    @Override
    public boolean isSctpNoDelay() {
        try {
            return channel.getOption(SCTP_NODELAY);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setSctpNoDelay(boolean sctpNoDelay) {
        try {
            channel.setOption(SCTP_NODELAY, sctpNoDelay);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public int getSendBufferSize() {
        try {
            return channel.getOption(SO_SNDBUF);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setSendBufferSize(int sendBufferSize) {
        try {
            channel.setOption(SO_SNDBUF, sendBufferSize);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public int getReceiveBufferSize() {
        try {
            return channel.getOption(SO_RCVBUF);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setReceiveBufferSize(int receiveBufferSize) {
        try {
            channel.setOption(SO_RCVBUF, receiveBufferSize);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public InitMaxStreams getInitMaxStreams() {
        try {
            return channel.getOption(SCTP_INIT_MAXSTREAMS);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setInitMaxStreams(InitMaxStreams initMaxStreams) {
        try {
            channel.setOption(SCTP_INIT_MAXSTREAMS, initMaxStreams);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }
}
