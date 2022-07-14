/*
 * Copyright 2018 The Netty Project
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
package io.netty5.channel.socket.nio;

import io.netty5.channel.ChannelException;
import io.netty5.channel.ChannelOption;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.channels.NetworkChannel;
import java.nio.channels.ServerSocketChannel;

/**
 * Provides {@link ChannelOption} over a given {@link java.net.SocketOption} which is then passed through the underlying
 * {@link java.nio.channels.NetworkChannel}.
 */
public final class NioChannelOption<T> extends ChannelOption<T> {

    private final java.net.SocketOption<T> option;

    @SuppressWarnings("deprecation")
    private NioChannelOption(java.net.SocketOption<T> option) {
        super(option.name());
        this.option = option;
    }

    /**
     * Returns a {@link ChannelOption} for the given {@link java.net.SocketOption}.
     */
    public static <T> ChannelOption<T> of(java.net.SocketOption<T> option) {
        return new NioChannelOption<>(option);
    }

    // Internal helper methods to remove code duplication between Nio*Channel implementations.
    static <T> void setOption(NetworkChannel channel, SocketOption<T> option, T value) {
        if (channel instanceof ServerSocketChannel && option == java.net.StandardSocketOptions.IP_TOS) {
            // Skip IP_TOS as a workaround for a JDK bug:
            // See https://mail.openjdk.java.net/pipermail/nio-dev/2018-August/005365.html
            return;
        }
        try {
            channel.setOption(option, value);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    static <T> T getOption(NetworkChannel channel, SocketOption<T> option) {
        if (channel instanceof ServerSocketChannel && option == java.net.StandardSocketOptions.IP_TOS) {
            // Skip IP_TOS as a workaround for a JDK bug:
            // See https://mail.openjdk.java.net/pipermail/nio-dev/2018-August/005365.html
            return null;
        }
        try {
            return channel.getOption(option);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    static boolean isOptionSupported(NetworkChannel channel, SocketOption<?> option) {
        return channel.supportedOptions().contains(option);
    }

    @SuppressWarnings("unchecked")
    static <T> SocketOption<T> toSocketOption(ChannelOption<T> option) {
        if (option instanceof NioChannelOption<?>) {
            return ((NioChannelOption<T>) option).option;
        }
        if (option == SO_RCVBUF) {
            return (SocketOption<T>) StandardSocketOptions.SO_RCVBUF;
        }
        if (option == SO_SNDBUF) {
            return (SocketOption<T>) StandardSocketOptions.SO_SNDBUF;
        }
        if (option == TCP_NODELAY) {
            return (SocketOption<T>) StandardSocketOptions.TCP_NODELAY;
        }
        if (option == SO_KEEPALIVE) {
            return (SocketOption<T>) StandardSocketOptions.SO_KEEPALIVE;
        }
        if (option == SO_REUSEADDR) {
            return (SocketOption<T>) StandardSocketOptions.SO_REUSEADDR;
        }
        if (option == SO_LINGER) {
            return (SocketOption<T>) StandardSocketOptions.SO_LINGER;
        }
        if (option == SO_BROADCAST) {
            return (SocketOption<T>) StandardSocketOptions.SO_BROADCAST;
        }
        if (option == IP_MULTICAST_LOOP_DISABLED) {
            return (SocketOption<T>) StandardSocketOptions.IP_MULTICAST_LOOP;
        }
        if (option == IP_MULTICAST_IF) {
            return (SocketOption<T>) StandardSocketOptions.IP_MULTICAST_IF;
        }
        if (option == IP_MULTICAST_TTL) {
            return (SocketOption<T>) StandardSocketOptions.IP_MULTICAST_TTL;
        }
        if (option == IP_TOS) {
            return (SocketOption<T>) StandardSocketOptions.IP_TOS;
        }
        return null;
    }
}
