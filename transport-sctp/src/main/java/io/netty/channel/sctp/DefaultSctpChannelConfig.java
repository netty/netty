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

import static io.netty.channel.ChannelOption.*;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultChannelConfig;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sun.nio.sctp.SctpChannel;
import com.sun.nio.sctp.SctpStandardSocketOptions.InitMaxStreams;

/**
 * The default {@link NioSocketChannelConfig} implementation for SCTP.
 */
class DefaultSctpChannelConfig extends DefaultChannelConfig implements SctpChannelConfig {

    private final SctpChannel channel;

    DefaultSctpChannelConfig(SctpChannel channel) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        this.channel = channel;
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        // TODO: Investigate if other SCTP options such as SCTP_PRIMARY_ADDR can be exposed.
        return getOptions(super.getOptions(), SO_RCVBUF, SO_SNDBUF, SCTP_NODELAY, SCTP_INIT_MAXSTREAMS);
    }

    @Override
    public <T> T getOption(ChannelOption<T> option) {
        if (option == SO_RCVBUF) {
            return (T) Integer.valueOf(getReceiveBufferSize());
        }
        if (option == SO_SNDBUF) {
            return (T) Integer.valueOf(getSendBufferSize());
        }
        if (option == SCTP_NODELAY) {
            return (T) Boolean.valueOf(isSctpNoDelay());
        }
        if (option == SCTP_INIT_MAXSTREAMS) {
            InitMaxStreams ims = getInitMaxStreams();
            if (ims == null) {
                return null;
            }
            List<Integer> values = new ArrayList<Integer>(2);
            values.add(ims.maxInStreams());
            values.add(ims.maxOutStreams());
            @SuppressWarnings("unchecked")
            T ret = (T) values;
            return ret;
        }

        return super.getOption(option);
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        validate(option, value);

        if (option == SO_RCVBUF) {
            setReceiveBufferSize((Integer) value);
        } else if (option == SO_SNDBUF) {
            setSendBufferSize((Integer) value);
        } else if (option == SCTP_NODELAY) {
            setSctpNoDelay((Boolean) value);
        } else if (option == SCTP_INIT_MAXSTREAMS) {
            @SuppressWarnings("unchecked")
            List<Integer> values = (List<Integer>) value;
            setInitMaxStreams(InitMaxStreams.create(values.get(0), values.get(1)));
        } else {
            return super.setOption(option, value);
        }

        return true;
    }

    @Override
    public boolean isSctpNoDelay() {
        try {
            return channel.getOption(com.sun.nio.sctp.SctpStandardSocketOptions.SCTP_NODELAY);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setSctpNoDelay(boolean sctpNoDelay) {
        try {
            channel.setOption(com.sun.nio.sctp.SctpStandardSocketOptions.SCTP_NODELAY, sctpNoDelay);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public int getSendBufferSize() {
        try {
            return channel.getOption(com.sun.nio.sctp.SctpStandardSocketOptions.SO_SNDBUF);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setSendBufferSize(int sendBufferSize) {
        try {
            channel.setOption(com.sun.nio.sctp.SctpStandardSocketOptions.SO_SNDBUF, sendBufferSize);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public int getReceiveBufferSize() {
        try {
            return channel.getOption(com.sun.nio.sctp.SctpStandardSocketOptions.SO_RCVBUF);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setReceiveBufferSize(int receiveBufferSize) {
        try {
            channel.setOption(com.sun.nio.sctp.SctpStandardSocketOptions.SO_RCVBUF, receiveBufferSize);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public InitMaxStreams getInitMaxStreams() {
        try {
            return channel.getOption(com.sun.nio.sctp.SctpStandardSocketOptions.SCTP_INIT_MAXSTREAMS);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setInitMaxStreams(InitMaxStreams initMaxStreams) {
        try {
            channel.setOption(com.sun.nio.sctp.SctpStandardSocketOptions.SCTP_INIT_MAXSTREAMS, initMaxStreams);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }
}
