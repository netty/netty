/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.socket.nio;

import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.DatagramChannelConfig;
import io.netty.channel.socket.DefaultDatagramChannelConfig;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SocketUtils;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.channels.DatagramChannel;
import java.util.Enumeration;
import java.util.Map;

/**
 * The default {@link NioDatagramChannelConfig} implementation.
 */
class NioDatagramChannelConfig extends DefaultDatagramChannelConfig {

    private static final Object IP_MULTICAST_TTL;
    private static final Object IP_MULTICAST_IF;
    private static final Object IP_MULTICAST_LOOP;
    private static final Method GET_OPTION;
    private static final Method SET_OPTION;

    static {
        ClassLoader classLoader = PlatformDependent.getClassLoader(DatagramChannel.class);
        Class<?> socketOptionType = null;
        try {
            socketOptionType = Class.forName("java.net.SocketOption", true, classLoader);
        } catch (Exception e) {
            // Not Java 7+
        }
        Class<?> stdSocketOptionType = null;
        try {
            stdSocketOptionType = Class.forName("java.net.StandardSocketOptions", true, classLoader);
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
                ipMulticastTtl = stdSocketOptionType.getDeclaredField("IP_MULTICAST_TTL").get(null);
            } catch (Exception e) {
                throw new Error("cannot locate the IP_MULTICAST_TTL field", e);
            }

            try {
                ipMulticastIf = stdSocketOptionType.getDeclaredField("IP_MULTICAST_IF").get(null);
            } catch (Exception e) {
                throw new Error("cannot locate the IP_MULTICAST_IF field", e);
            }

            try {
                ipMulticastLoop = stdSocketOptionType.getDeclaredField("IP_MULTICAST_LOOP").get(null);
            } catch (Exception e) {
                throw new Error("cannot locate the IP_MULTICAST_LOOP field", e);
            }

            Class<?> networkChannelClass = null;
            try {
                networkChannelClass = Class.forName("java.nio.channels.NetworkChannel", true, classLoader);
            } catch (Throwable ignore) {
                // Not Java 7+
            }

            if (networkChannelClass == null) {
                getOption = null;
                setOption = null;
            } else {
                try {
                    getOption = networkChannelClass.getDeclaredMethod("getOption", socketOptionType);
                } catch (Exception e) {
                    throw new Error("cannot locate the getOption() method", e);
                }

                try {
                    setOption = networkChannelClass.getDeclaredMethod("setOption", socketOptionType, Object.class);
                } catch (Exception e) {
                    throw new Error("cannot locate the setOption() method", e);
                }
            }
        }
        IP_MULTICAST_TTL = ipMulticastTtl;
        IP_MULTICAST_IF = ipMulticastIf;
        IP_MULTICAST_LOOP = ipMulticastLoop;
        GET_OPTION = getOption;
        SET_OPTION = setOption;
    }

    private final DatagramChannel javaChannel;

    NioDatagramChannelConfig(NioDatagramChannel channel, DatagramChannel javaChannel) {
        super(channel, javaChannel.socket());
        this.javaChannel = javaChannel;
    }

    @Override
    public int getTimeToLive() {
        return (Integer) getOption0(IP_MULTICAST_TTL);
    }

    @Override
    public DatagramChannelConfig setTimeToLive(int ttl) {
        setOption0(IP_MULTICAST_TTL, ttl);
        return this;
    }

    @Override
    public InetAddress getInterface() {
        NetworkInterface inf = getNetworkInterface();
        if (inf != null) {
            Enumeration<InetAddress> addresses = SocketUtils.addressesFromNetworkInterface(inf);
            if (addresses.hasMoreElements()) {
                return addresses.nextElement();
            }
        }
        return null;
    }

    @Override
    public DatagramChannelConfig setInterface(InetAddress interfaceAddress) {
        try {
            setNetworkInterface(NetworkInterface.getByInetAddress(interfaceAddress));
        } catch (SocketException e) {
            throw new ChannelException(e);
        }
        return this;
    }

    @Override
    public NetworkInterface getNetworkInterface() {
        return (NetworkInterface) getOption0(IP_MULTICAST_IF);
    }

    @Override
    public DatagramChannelConfig setNetworkInterface(NetworkInterface networkInterface) {
        setOption0(IP_MULTICAST_IF, networkInterface);
        return this;
    }

    @Override
    public boolean isLoopbackModeDisabled() {
        return (Boolean) getOption0(IP_MULTICAST_LOOP);
    }

    @Override
    public DatagramChannelConfig setLoopbackModeDisabled(boolean loopbackModeDisabled) {
        setOption0(IP_MULTICAST_LOOP, loopbackModeDisabled);
        return this;
    }

    @Override
    public DatagramChannelConfig setAutoRead(boolean autoRead) {
        super.setAutoRead(autoRead);
        return this;
    }

    @Override
    protected void autoReadCleared() {
        ((NioDatagramChannel) channel).clearReadPending0();
    }

    private Object getOption0(Object option) {
        if (GET_OPTION == null) {
            throw new UnsupportedOperationException();
        } else {
            try {
                return GET_OPTION.invoke(javaChannel, option);
            } catch (Exception e) {
                throw new ChannelException(e);
            }
        }
    }

    private void setOption0(Object option, Object value) {
        if (SET_OPTION == null) {
            throw new UnsupportedOperationException();
        } else {
            try {
                SET_OPTION.invoke(javaChannel, option, value);
            } catch (Exception e) {
                throw new ChannelException(e);
            }
        }
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        if (PlatformDependent.javaVersion() >= 7 && option instanceof NioChannelOption) {
            return NioChannelOption.setOption(javaChannel, (NioChannelOption<T>) option, value);
        }
        return super.setOption(option, value);
    }

    @Override
    public <T> T getOption(ChannelOption<T> option) {
        if (PlatformDependent.javaVersion() >= 7 && option instanceof NioChannelOption) {
            return NioChannelOption.getOption(javaChannel, (NioChannelOption<T>) option);
        }
        return super.getOption(option);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        if (PlatformDependent.javaVersion() >= 7) {
            return getOptions(super.getOptions(), NioChannelOption.getOptions(javaChannel));
        }
        return super.getOptions();
    }
}
