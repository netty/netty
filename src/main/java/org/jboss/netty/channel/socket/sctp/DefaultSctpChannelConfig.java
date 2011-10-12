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
package org.jboss.netty.channel.socket.sctp;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.Map;

import com.sun.nio.sctp.SctpChannel;
import com.sun.nio.sctp.SctpStandardSocketOption;

import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.DefaultChannelConfig;
import org.jboss.netty.channel.socket.nio.NioSocketChannelConfig;
import org.jboss.netty.util.internal.ConversionUtil;


/**
 * The default {@link NioSocketChannelConfig} implementation for SCTP.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @author <a href="http://github.com/jestan">Jestan Nirojan</a>
 * @version $Rev$, $Date$
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
        } else if (key.equals("sctpPrimaryAddress")) {
            setPrimaryAddress(ConversionUtil.toSocketAddress(value));
        } else if (key.equals("sctpPeerPrimaryAddress")) {
            setPeerPrimaryAddress(ConversionUtil.toSocketAddress(value));
        } else if (key.equals("soLinger")) {
            setSoLinger(ConversionUtil.toInt(value));
        } else if (key.equals("sctpInitMaxStreams")) {
            setInitMaxStreams((SctpStandardSocketOption.InitMaxStreams) value);
        } else {
            return false;
        }
        return true;
    }

    @Override
    public boolean isSctpNoDelay() {
        try {
            return channel.getOption(SctpStandardSocketOption.SCTP_NODELAY);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setSctpNoDelay(boolean tcpNoDelay) {
        try {
            channel.setOption(SctpStandardSocketOption.SCTP_NODELAY, tcpNoDelay);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public int getSoLinger() {
        try {
            return channel.getOption(SctpStandardSocketOption.SO_LINGER);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setSoLinger(int soLinger) {
        try {
            channel.setOption(SctpStandardSocketOption.SO_LINGER, soLinger);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public int getSendBufferSize() {
        try {
            return channel.getOption(SctpStandardSocketOption.SO_SNDBUF);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setSendBufferSize(int sendBufferSize) {
        try {
            channel.setOption(SctpStandardSocketOption.SO_SNDBUF, sendBufferSize);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public int getReceiveBufferSize() {
        try {
            return channel.getOption(SctpStandardSocketOption.SO_RCVBUF);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setReceiveBufferSize(int receiveBufferSize) {
        try {
            channel.setOption(SctpStandardSocketOption.SO_RCVBUF, receiveBufferSize);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public SocketAddress getPrimaryAddress() {
        try {
            return channel.getOption(SctpStandardSocketOption.SCTP_PRIMARY_ADDR);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setPrimaryAddress(SocketAddress primaryAddress) {
        try {
            channel.setOption(SctpStandardSocketOption.SCTP_PRIMARY_ADDR, primaryAddress);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public SocketAddress getPeerPrimaryAddress() {
        try {
            return channel.getOption(SctpStandardSocketOption.SCTP_SET_PEER_PRIMARY_ADDR);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setPeerPrimaryAddress(SocketAddress peerPrimaryAddress) {
        try {
            channel.setOption(SctpStandardSocketOption.SCTP_SET_PEER_PRIMARY_ADDR, peerPrimaryAddress);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public SctpStandardSocketOption.InitMaxStreams getInitMaxStreams() {
        try {
            return channel.getOption(SctpStandardSocketOption.SCTP_INIT_MAXSTREAMS);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setInitMaxStreams(SctpStandardSocketOption.InitMaxStreams initMaxStreams) {
        try {
            channel.setOption(SctpStandardSocketOption.SCTP_INIT_MAXSTREAMS, initMaxStreams);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }
}
