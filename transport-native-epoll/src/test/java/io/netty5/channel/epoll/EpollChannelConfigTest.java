/*
 * Copyright 2015 The Netty Project
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
package io.netty5.channel.epoll;

import io.netty5.channel.ChannelException;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.unix.Buffer;
import io.netty5.channel.unix.IntegerUnixChannelOption;
import io.netty5.channel.unix.RawUnixChannelOption;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class EpollChannelConfigTest {

    @Test
    public void testOptionGetThrowsChannelException() throws Exception {
        Epoll.ensureAvailability();
        EventLoopGroup group = new MultithreadEventLoopGroup(1, EpollIoHandler.newFactory());
        try {
            EpollSocketChannel channel = new EpollSocketChannel(group.next());
            channel.getOption(ChannelOption.SO_LINGER);
            channel.fd().close();
            try {
                channel.getOption(ChannelOption.SO_LINGER);
                fail();
            } catch (ChannelException e) {
                // expected
            }
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    public void testOptionSetThrowsChannelException() throws Exception {
        Epoll.ensureAvailability();
        EventLoopGroup group = new MultithreadEventLoopGroup(1, EpollIoHandler.newFactory());
        try {
            EpollSocketChannel channel = new EpollSocketChannel(group.next());
            channel.setOption(ChannelOption.SO_KEEPALIVE, true);
            channel.fd().close();
            try {
                channel.setOption(ChannelOption.SO_KEEPALIVE, true);
                fail();
            } catch (ChannelException e) {
                // expected
            }
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    public void testIntegerOption() throws Exception {
        Epoll.ensureAvailability();
        EventLoopGroup group = new MultithreadEventLoopGroup(1, EpollIoHandler.newFactory());
        try {
            EpollSocketChannel channel = new EpollSocketChannel(group.next());
            IntegerUnixChannelOption opt = new IntegerUnixChannelOption("INT_OPT", 1, 2);
            Integer zero = 0;
            assertEquals(zero, channel.getOption(opt));
            channel.setOption(opt, 1);
            assertNotEquals(zero, channel.getOption(opt));
            channel.fd().close();
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    public void testRawOption() throws Exception {
        Epoll.ensureAvailability();
        EventLoopGroup group = new MultithreadEventLoopGroup(1, EpollIoHandler.newFactory());
        try {
            EpollSocketChannel channel = new EpollSocketChannel(group.next());
            // Value for SOL_SOCKET and SO_REUSEADDR
            // See https://github.com/torvalds/linux/blob/v5.17/include/uapi/asm-generic/socket.h
            RawUnixChannelOption opt = new RawUnixChannelOption("RAW_OPT", 1, 2, 4);

            ByteBuffer disabled = Buffer.allocateDirectWithNativeOrder(4);
            disabled.putInt(0).flip();
            assertEquals(disabled, channel.getOption(opt));

            ByteBuffer enabled = Buffer.allocateDirectWithNativeOrder(4);
            enabled.putInt(1).flip();

            channel.setOption(opt, enabled);
            assertNotEquals(disabled, channel.getOption(opt));
            channel.fd().close();
        } finally {
            group.shutdownGracefully();
        }
    }
}
