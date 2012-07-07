/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.socket.aio;

import static io.netty.channel.ChannelOption.*;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.socket.SocketChannelConfig;

import java.io.IOException;
import java.net.StandardSocketOptions;
import java.nio.channels.NetworkChannel;
import java.util.Map;

/**
 * The default {@link SocketChannelConfig} implementation.
 */
final class AioSocketChannelConfig extends DefaultChannelConfig
                                        implements SocketChannelConfig {

    private volatile NetworkChannel channel;
    private volatile Integer receiveBufferSize;
    private volatile Integer sendBufferSize;
    private volatile Boolean tcpNoDelay;
    private volatile Boolean keepAlive;
    private volatile Boolean reuseAddress;
    private volatile Integer soLinger;
    private volatile Integer trafficClass;

    /**
     * Creates a new instance.
     */
    void setChannel(NetworkChannel channel) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        if (this.channel != null) {
            throw new IllegalStateException();
        }
        this.channel = channel;

        if (receiveBufferSize != null) {
            setReceiveBufferSize(receiveBufferSize);
        }
        if (sendBufferSize != null) {
            setSendBufferSize(sendBufferSize);
        }
        if (reuseAddress != null) {
            setReuseAddress(reuseAddress);
        }
        if (tcpNoDelay != null) {
            setTcpNoDelay(tcpNoDelay);
        }
        if (keepAlive != null) {
            setKeepAlive(keepAlive);
        }
        if (soLinger != null) {
            setSoLinger(soLinger);
        }
        if (trafficClass != null) {
            setTrafficClass(trafficClass);
        }
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(
                super.getOptions(),
                SO_RCVBUF, SO_SNDBUF, TCP_NODELAY, SO_KEEPALIVE, SO_REUSEADDR, SO_LINGER, IP_TOS);
    }

    @Override
    public <T> T getOption(ChannelOption<T> option) {
        if (option == SO_RCVBUF) {
            return (T) Integer.valueOf(getReceiveBufferSize());
        }
        if (option == SO_SNDBUF) {
            return (T) Integer.valueOf(getSendBufferSize());
        }
        if (option == TCP_NODELAY) {
            return (T) Boolean.valueOf(isTcpNoDelay());
        }
        if (option == SO_KEEPALIVE) {
            return (T) Boolean.valueOf(isKeepAlive());
        }
        if (option == SO_REUSEADDR) {
            return (T) Boolean.valueOf(isReuseAddress());
        }
        if (option == SO_LINGER) {
            return (T) Integer.valueOf(getSoLinger());
        }
        if (option == IP_TOS) {
            return (T) Integer.valueOf(getTrafficClass());
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
        } else if (option == TCP_NODELAY) {
            setTcpNoDelay((Boolean) value);
        } else if (option == SO_KEEPALIVE) {
            setKeepAlive((Boolean) value);
        } else if (option == SO_REUSEADDR) {
            setReuseAddress((Boolean) value);
        } else if (option == SO_LINGER) {
            setSoLinger((Integer) value);
        } else if (option == IP_TOS) {
            setTrafficClass((Integer) value);
        } else {
            return super.setOption(option, value);
        }

        return true;
    }

    @Override
    public int getReceiveBufferSize() {
        NetworkChannel channel = this.channel;
        if (channel == null) {
            if (receiveBufferSize == null) {
                return 0;
            } else {
                return receiveBufferSize;
            }
        }

        try {
            return channel.getOption(StandardSocketOptions.SO_RCVBUF);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public int getSendBufferSize() {
        NetworkChannel channel = this.channel;
        if (channel == null) {
            if (sendBufferSize == null) {
                return 0;
            } else {
                return sendBufferSize;
            }
        }

        try {
            return channel.getOption(StandardSocketOptions.SO_SNDBUF);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public int getSoLinger() {
        NetworkChannel channel = this.channel;
        if (channel == null) {
            if (soLinger == null) {
                return 1;
            } else {
                return soLinger;
            }
        }

        try {
            return channel.getOption(StandardSocketOptions.SO_LINGER);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public int getTrafficClass() {
        NetworkChannel channel = this.channel;
        if (channel == null) {
            if (trafficClass == null) {
                return 0;
            } else {
                return trafficClass;
            }
        }

        try {
            return channel.getOption(StandardSocketOptions.IP_TOS);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public boolean isKeepAlive() {
        NetworkChannel channel = this.channel;
        if (channel == null) {
            if (keepAlive == null) {
                return false;
            } else {
                return keepAlive;
            }
        }

        try {
            return channel.getOption(StandardSocketOptions.SO_KEEPALIVE);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public boolean isReuseAddress() {
        NetworkChannel channel = this.channel;
        if (channel == null) {
            if (reuseAddress == null) {
                return false;
            } else {
                return reuseAddress;
            }
        }

        try {
            return channel.getOption(StandardSocketOptions.SO_REUSEADDR);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public boolean isTcpNoDelay() {
        NetworkChannel channel = this.channel;
        if (channel == null) {
            if (tcpNoDelay == null) {
                return false;
            } else {
                return tcpNoDelay;
            }
        }

        try {
            return channel.getOption(StandardSocketOptions.SO_REUSEADDR);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setKeepAlive(boolean keepAlive) {
        NetworkChannel channel = this.channel;
        if (channel == null) {
            this.keepAlive = keepAlive;
            return;
        }

        try {
            channel.setOption(StandardSocketOptions.SO_KEEPALIVE, keepAlive);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setPerformancePreferences(
            int connectionTime, int latency, int bandwidth) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setReceiveBufferSize(int receiveBufferSize) {
        NetworkChannel channel = this.channel;
        if (channel == null) {
            this.receiveBufferSize = receiveBufferSize;
            return;
        }

        try {
            channel.setOption(StandardSocketOptions.SO_RCVBUF, receiveBufferSize);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setReuseAddress(boolean reuseAddress) {
        NetworkChannel channel = this.channel;
        if (channel == null) {
            this.reuseAddress = reuseAddress;
            return;
        }

        try {
            channel.setOption(StandardSocketOptions.SO_REUSEADDR, reuseAddress);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setSendBufferSize(int sendBufferSize) {
        NetworkChannel channel = this.channel;
        if (channel == null) {
            this.sendBufferSize = sendBufferSize;
            return;
        }

        try {
            channel.setOption(StandardSocketOptions.SO_SNDBUF, sendBufferSize);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setSoLinger(int soLinger) {
        NetworkChannel channel = this.channel;
        if (channel == null) {
            this.soLinger = soLinger;
            return;
        }

        try {
            channel.setOption(StandardSocketOptions.SO_LINGER, soLinger);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setTcpNoDelay(boolean tcpNoDelay) {
        NetworkChannel channel = this.channel;
        if (channel == null) {
            this.tcpNoDelay = tcpNoDelay;
            return;
        }

        try {
            channel.setOption(StandardSocketOptions.TCP_NODELAY, tcpNoDelay);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setTrafficClass(int trafficClass) {
        NetworkChannel channel = this.channel;
        if (channel == null) {
            this.trafficClass = trafficClass;
        }

        try {
            channel.setOption(StandardSocketOptions.IP_TOS, trafficClass);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }
}
