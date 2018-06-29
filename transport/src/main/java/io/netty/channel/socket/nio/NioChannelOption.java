/*
 * Copyright 2018 The Netty Project
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
import io.netty.channel.ChannelOption;

import java.io.IOException;
import java.net.SocketOption;
import java.nio.channels.NetworkChannel;
import java.util.Set;

/**
 * Provides {@link ChannelOption} over a given {@link SocketOption} which is then passed through the underlying
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
     * Returns a {@link ChannelOption} for the given {@link SocketOption}.
     */
    public static <T> ChannelOption<T> of(SocketOption<T> option) {
        return new NioChannelOption<T>(option);
    }

    // Internal helper methods to remove code duplication between Nio*Channel implementations.
    static <T> boolean setOption(NetworkChannel channel, NioChannelOption<T> option, T value) {
        try {
            channel.setOption(option.option, value);
            return true;
        } catch (UnsupportedOperationException ignore) {
            return false;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    static <T> T getOption(NetworkChannel channel, NioChannelOption<T> option) {
        try {
            return channel.getOption(option.option);
        } catch (UnsupportedOperationException ignore) {
            return null;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @SuppressWarnings("unchecked")
    static ChannelOption[] getOptions(NetworkChannel channel) {
        Set<SocketOption<?>> supportedOpts = channel.supportedOptions();
        ChannelOption<?>[] extraOpts = new ChannelOption[supportedOpts.size()];

        int i = 0;
        for (SocketOption<?> opt : supportedOpts) {
            extraOpts[i++] = new NioChannelOption(opt);
        }
        return extraOpts;
    }
}
