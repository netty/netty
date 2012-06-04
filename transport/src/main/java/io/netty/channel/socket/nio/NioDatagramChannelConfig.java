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
package io.netty.channel.socket.nio;

import io.netty.channel.ChannelException;
import io.netty.channel.socket.DefaultDatagramChannelConfig;
import io.netty.util.internal.DetectionUtil;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.NetworkChannel;
import java.util.Enumeration;

/**
 * The default {@link NioSocketChannelConfig} implementation.
 */
class NioDatagramChannelConfig extends DefaultDatagramChannelConfig {

    private static final Object IP_MULTICAST_TTL;
    private static final Object IP_MULTICAST_IF;
    private static final Object IP_MULTICAST_LOOP;
    private static final Method GET_OPTION;
    private static final Method SET_OPTION;

    static {
        ClassLoader classLoader = DatagramChannel.class.getClassLoader();
        Class<?> socketOptionType = null;
        try {
            socketOptionType = Class.forName("java.net.SocketOption", true, classLoader);
        } catch (Exception e) {
            // Not Java 7+
        }

        Object ipMulticastTtl = null;
        Object ipMulticastIf = null;
        Object ipMulticastLoop = null;
        Method getOption = null;
        Method setOption = null;
        if (socketOptionType != null) {
            try {
                ipMulticastTtl = Class.forName("java.net.StandardSocketOptions", true, classLoader).getDeclaredField("IP_MULTICAST_TTL").get(null);
            } catch (Exception e) {
                throw new Error("cannot locate the IP_MULTICAST_TTL field", e);
            }

            try {
                ipMulticastIf = Class.forName("java.net.StandardSocketOptions", true, classLoader).getDeclaredField("IP_MULTICAST_IF").get(null);
            } catch (Exception e) {
                throw new Error("cannot locate the IP_MULTICAST_IF field", e);
            }

            try {
                ipMulticastLoop = Class.forName("java.net.StandardSocketOptions", true, classLoader).getDeclaredField("IP_MULTICAST_LOOP").get(null);
            } catch (Exception e) {
                throw new Error("cannot locate the IP_MULTICAST_LOOP field", e);
            }

            try {
                getOption = NetworkChannel.class.getDeclaredMethod("getOption", socketOptionType);
            } catch (Exception e) {
                throw new Error("cannot locate the getOption() method", e);
            }

            try {
                setOption = NetworkChannel.class.getDeclaredMethod("setOption", socketOptionType, Object.class);
            } catch (Exception e) {
                throw new Error("cannot locate the setOption() method", e);
            }
        }
        IP_MULTICAST_TTL = ipMulticastTtl;
        IP_MULTICAST_IF = ipMulticastIf;
        IP_MULTICAST_LOOP = ipMulticastLoop;
        GET_OPTION = getOption;
        SET_OPTION = setOption;
    }

    private final DatagramChannel channel;

    NioDatagramChannelConfig(DatagramChannel channel) {
        super(channel.socket());
        this.channel = channel;
    }

    @Override
    public int getTimeToLive() {
        return (Integer) getOption0(IP_MULTICAST_TTL);
    }

    @Override
    public void setTimeToLive(int ttl) {
        setOption0(IP_MULTICAST_TTL, ttl);
    }

    @Override
    public InetAddress getInterface() {
        NetworkInterface inf = getNetworkInterface();
        if (inf == null) {
            return null;
        } else {
            Enumeration<InetAddress> addresses = inf.getInetAddresses();
            if (addresses.hasMoreElements()) {
                return addresses.nextElement();
            }
            return null;
        }
    }

    @Override
    public void setInterface(InetAddress interfaceAddress) {
        try {
            setNetworkInterface(NetworkInterface.getByInetAddress(interfaceAddress));
        } catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public NetworkInterface getNetworkInterface() {
        return (NetworkInterface) getOption0(IP_MULTICAST_IF);
    }

    @Override
    public void setNetworkInterface(NetworkInterface networkInterface) {
        setOption0(IP_MULTICAST_IF, networkInterface);
    }

    @Override
    public boolean isLoopbackModeDisabled() {
        return (Boolean) getOption0(IP_MULTICAST_LOOP);
    }

    @Override
    public void setLoopbackModeDisabled(boolean loopbackModeDisabled) {
        setOption0(IP_MULTICAST_LOOP, loopbackModeDisabled);
    }

    private Object getOption0(Object option) {
        if (DetectionUtil.javaVersion() < 7) {
            throw new UnsupportedOperationException();
        } else {
            try {
                return GET_OPTION.invoke(channel, option);
            } catch (Exception e) {
                throw new ChannelException(e);
            }
        }
    }

    private void setOption0(Object option, Object value) {
        if (DetectionUtil.javaVersion() < 7) {
            throw new UnsupportedOperationException();
        } else {
            try {
                SET_OPTION.invoke(channel, option, value);
            } catch (Exception e) {
                throw new ChannelException(e);
            }
        }
    }
}
