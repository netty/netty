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
package io.netty.channel.socket.nio;

import io.netty.channel.ChannelException;
import io.netty.channel.socket.DefaultDatagramChannelConfig;
import io.netty.util.internal.ConversionUtil;
import io.netty.util.internal.DetectionUtil;

import java.lang.reflect.Method;
import java.net.NetworkInterface;
import java.nio.channels.DatagramChannel;

/**
 * The default {@link NioSocketChannelConfig} implementation.
 */
class DefaultNioDatagramChannelConfig extends DefaultDatagramChannelConfig
        implements NioDatagramChannelConfig {

    private static final Object IP_MULTICAST_IF;
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

        Object ipMulticastIf = null;
        if (socketOptionType != null) {
            try {
                ipMulticastIf = Class.forName("java.net.StandardSocketOptions", true, classLoader).getDeclaredField("IP_MULTICAST_IF").get(null);
            } catch (Exception e) {
                throw new Error("cannot locate the IP_MULTICAST_IF field", e);
            }
        }
        IP_MULTICAST_IF = ipMulticastIf;

        Method getOption;
        try {
            getOption = DatagramChannel.class.getDeclaredMethod("getOption", socketOptionType);
        } catch (Exception e) {
            throw new Error("cannot locate the getOption() method", e);
        }
        GET_OPTION = getOption;

        Method setOption;
        try {
            setOption = DatagramChannel.class.getDeclaredMethod("setOption", socketOptionType, Object.class);
        } catch (Exception e) {
            throw new Error("cannot locate the setOption() method", e);
        }
        SET_OPTION = setOption;
    }

    private final DatagramChannel channel;
    private volatile int writeSpinCount = 16;

    DefaultNioDatagramChannelConfig(DatagramChannel channel) {
        super(channel.socket());
        this.channel = channel;
    }

    @Override
    public boolean setOption(String key, Object value) {
        if (super.setOption(key, value)) {
            return true;
        }

        if (key.equals("writeSpinCount")) {
            setWriteSpinCount(ConversionUtil.toInt(value));
        } else {
            return false;
        }
        return true;
    }

    @Override
    public int getWriteSpinCount() {
        return writeSpinCount;
    }

    @Override
    public void setWriteSpinCount(int writeSpinCount) {
        if (writeSpinCount <= 0) {
            throw new IllegalArgumentException(
                    "writeSpinCount must be a positive integer.");
        }
        this.writeSpinCount = writeSpinCount;
    }

    @Override
    public void setNetworkInterface(NetworkInterface networkInterface) {
        if (DetectionUtil.javaVersion() < 7) {
            throw new UnsupportedOperationException();
        } else {
            try {
                SET_OPTION.invoke(channel, IP_MULTICAST_IF, networkInterface);
            } catch (Exception e) {
                throw new ChannelException(e);
            }
        }
    }

    @Override
    public NetworkInterface getNetworkInterface() {
        if (DetectionUtil.javaVersion() < 7) {
            throw new UnsupportedOperationException();
        } else {
            try {
                return (NetworkInterface) GET_OPTION.invoke(channel, IP_MULTICAST_IF);
            } catch (Exception e) {
                throw new ChannelException(e);
            }
        }
    }
}
