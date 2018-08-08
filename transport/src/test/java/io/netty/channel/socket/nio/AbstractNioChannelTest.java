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

import io.netty.channel.ChannelOption;
import io.netty.channel.nio.AbstractNioChannel;
import org.junit.Test;

import java.io.IOException;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.channels.NetworkChannel;

import static org.junit.Assert.*;

public abstract class AbstractNioChannelTest<T extends AbstractNioChannel> {

    protected abstract T newNioChannel();

    protected abstract NetworkChannel jdkChannel(T channel);

    protected abstract SocketOption<?> newInvalidOption();

    @Test
    public void testNioChannelOption() throws IOException {
        T channel = newNioChannel();
        try {
            NetworkChannel jdkChannel = jdkChannel(channel);
            ChannelOption<Boolean> option = NioChannelOption.of(StandardSocketOptions.SO_REUSEADDR);
            boolean value1 = jdkChannel.getOption(StandardSocketOptions.SO_REUSEADDR);
            boolean value2 = channel.config().getOption(option);

            assertEquals(value1, value2);

            channel.config().setOption(option, !value2);
            boolean value3 = jdkChannel.getOption(StandardSocketOptions.SO_REUSEADDR);
            boolean value4 = channel.config().getOption(option);
            assertEquals(value3, value4);
            assertNotEquals(value1, value4);
        } finally {
            channel.unsafe().closeForcibly();
        }
    }

    @Test
    public void testInvalidNioChannelOption() {
        T channel = newNioChannel();
        try {
            ChannelOption<?> option = NioChannelOption.of(newInvalidOption());
            assertFalse(channel.config().setOption(option, null));
            assertNull(channel.config().getOption(option));
        } finally {
            channel.unsafe().closeForcibly();
        }
    }

    @Test
    public void testGetOptions()  {
        T channel = newNioChannel();
        try {
            channel.config().getOptions();
        } finally {
            channel.unsafe().closeForcibly();
        }
    }
}
