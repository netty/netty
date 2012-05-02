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
package io.netty.channel.socket;

import io.netty.channel.ChannelException;
import io.netty.channel.DefaultChannelConfig;
import io.netty.util.internal.ConversionUtil;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;

/**
 * The default {@link DatagramChannelConfig} implementation.
 */
public class DefaultDatagramChannelConfig extends DefaultChannelConfig
                                        implements DatagramChannelConfig {

    private final DatagramSocket socket;

    /**
     * Creates a new instance.
     */
    public DefaultDatagramChannelConfig(DatagramSocket socket) {
        if (socket == null) {
            throw new NullPointerException("socket");
        }
        this.socket = socket;
    }

    @Override
    public boolean setOption(String key, Object value) {
        if (super.setOption(key, value)) {
            return true;
        }

        if (key.equals("broadcast")) {
            setBroadcast(ConversionUtil.toBoolean(value));
        } else if (key.equals("receiveBufferSize")) {
            setReceiveBufferSize(ConversionUtil.toInt(value));
        } else if (key.equals("sendBufferSize")) {
            setSendBufferSize(ConversionUtil.toInt(value));
        } else if (key.equals("reuseAddress")) {
            setReuseAddress(ConversionUtil.toBoolean(value));
        } else if (key.equals("loopbackModeDisabled")) {
            setLoopbackModeDisabled(ConversionUtil.toBoolean(value));
        } else if (key.equals("interface")) {
            setInterface((InetAddress) value);
        } else if (key.equals("networkInterface")) {
            setNetworkInterface((NetworkInterface) value);
        } else if (key.equals("timeToLive")) {
            setTimeToLive(ConversionUtil.toInt(value));
        } else if (key.equals("trafficClass")) {
            setTrafficClass(ConversionUtil.toInt(value));
        } else {
            return false;
        }
        return true;
    }

    @Override
    public boolean isBroadcast() {
        try {
            return socket.getBroadcast();
        } catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setBroadcast(boolean broadcast) {
        try {
            socket.setBroadcast(broadcast);
        } catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public InetAddress getInterface() {
        if (socket instanceof MulticastSocket) {
            try {
                return ((MulticastSocket) socket).getInterface();
            } catch (SocketException e) {
                throw new ChannelException(e);
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public void setInterface(InetAddress interfaceAddress) {
        if (socket instanceof MulticastSocket) {
            try {
                ((MulticastSocket) socket).setInterface(interfaceAddress);
            } catch (SocketException e) {
                throw new ChannelException(e);
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public boolean isLoopbackModeDisabled() {
        if (socket instanceof MulticastSocket) {
            try {
                return ((MulticastSocket) socket).getLoopbackMode();
            } catch (SocketException e) {
                throw new ChannelException(e);
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public void setLoopbackModeDisabled(boolean loopbackModeDisabled) {
        if (socket instanceof MulticastSocket) {
            try {
                ((MulticastSocket) socket).setLoopbackMode(loopbackModeDisabled);
            } catch (SocketException e) {
                throw new ChannelException(e);
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public NetworkInterface getNetworkInterface() {
        if (socket instanceof MulticastSocket) {
            try {
                return ((MulticastSocket) socket).getNetworkInterface();
            } catch (SocketException e) {
                throw new ChannelException(e);
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public void setNetworkInterface(NetworkInterface networkInterface) {
        if (socket instanceof MulticastSocket) {
            try {
                ((MulticastSocket) socket).setNetworkInterface(networkInterface);
            } catch (SocketException e) {
                throw new ChannelException(e);
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public boolean isReuseAddress() {
        try {
            return socket.getReuseAddress();
        } catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setReuseAddress(boolean reuseAddress) {
        try {
            socket.setReuseAddress(reuseAddress);
        } catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public int getReceiveBufferSize() {
        try {
            return socket.getReceiveBufferSize();
        } catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setReceiveBufferSize(int receiveBufferSize) {
        try {
            socket.setReceiveBufferSize(receiveBufferSize);
        } catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public int getSendBufferSize() {
        try {
            return socket.getSendBufferSize();
        } catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setSendBufferSize(int sendBufferSize) {
        try {
            socket.setSendBufferSize(sendBufferSize);
        } catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public int getTimeToLive() {
        if (socket instanceof MulticastSocket) {
            try {
                return ((MulticastSocket) socket).getTimeToLive();
            } catch (IOException e) {
                throw new ChannelException(e);
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public void setTimeToLive(int ttl) {
        if (socket instanceof MulticastSocket) {
            try {
                ((MulticastSocket) socket).setTimeToLive(ttl);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public int getTrafficClass() {
        try {
            return socket.getTrafficClass();
        } catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setTrafficClass(int trafficClass) {
        try {
            socket.setTrafficClass(trafficClass);
        } catch (SocketException e) {
            throw new ChannelException(e);
        }
    }
}
