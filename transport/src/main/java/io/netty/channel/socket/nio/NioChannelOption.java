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
package io.netty.channel.socket.nio;

import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOption;

import java.io.IOException;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.channels.Channel;
import java.nio.channels.NetworkChannel;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Provides {@link ChannelOption} over a given {@link java.net.SocketOption} which is then passed through the underlying
 * {@link NetworkChannel}.
 */
public final class NioChannelOption<T> extends ChannelOption<T> {

    private final SocketOption<T> option;

    @SuppressWarnings("deprecation")
    private NioChannelOption(SocketOption<T> option) {
        super(option.name());
        this.option = option;
    }

    /**
     * Returns a {@link ChannelOption} for the given {@link java.net.SocketOption}.
     */
    public static <T> ChannelOption<T> of(SocketOption<T> option) {
        return new NioChannelOption<T>(option);
    }

    // Internal helper methods to remove code duplication between Nio*Channel implementations.
    static <T> boolean setOption(Channel jdkChannel, NioChannelOption<T> option, T value) {
        NetworkChannel channel = (NetworkChannel) jdkChannel;
        if (!channel.supportedOptions().contains(option.option)) {
            return false;
        }
        if (channel instanceof ServerSocketChannel && option.option == StandardSocketOptions.IP_TOS) {
            // Skip IP_TOS as a workaround for a JDK bug:
            // See https://mail.openjdk.java.net/pipermail/nio-dev/2018-August/005365.html
            return false;
        }
        try {
            channel.setOption(option.option, value);
            return true;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    static <T> T getOption(Channel jdkChannel, NioChannelOption<T> option) {
        NetworkChannel channel = (NetworkChannel) jdkChannel;

        if (!channel.supportedOptions().contains(option.option)) {
            return null;
        }
        if (channel instanceof ServerSocketChannel && option.option == StandardSocketOptions.IP_TOS) {
            // Skip IP_TOS as a workaround for a JDK bug:
            // See https://mail.openjdk.java.net/pipermail/nio-dev/2018-August/005365.html
            return null;
        }
        try {
            return channel.getOption(option.option);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @SuppressWarnings("unchecked")
    static ChannelOption<?>[] getOptions(Channel jdkChannel) {
        NetworkChannel channel = (NetworkChannel) jdkChannel;
        Set<SocketOption<?>> supportedOpts = channel.supportedOptions();

        if (channel instanceof ServerSocketChannel) {
            List<ChannelOption<?>> extraOpts = new ArrayList<ChannelOption<?>>(supportedOpts.size());
            for (SocketOption<?> opt : supportedOpts) {
                if (opt == StandardSocketOptions.IP_TOS) {
                    // Skip IP_TOS as a workaround for a JDK bug:
                    // See https://mail.openjdk.java.net/pipermail/nio-dev/2018-August/005365.html
                    continue;
                }
                extraOpts.add(new NioChannelOption(opt));
            }
            return extraOpts.toArray(new ChannelOption[0]);
        } else {
            ChannelOption<?>[] extraOpts = new ChannelOption[supportedOpts.size()];

            int i = 0;
            for (SocketOption<?> opt : supportedOpts) {
                extraOpts[i++] = new NioChannelOption(opt);
            }
            return extraOpts;
        }
    }
}
