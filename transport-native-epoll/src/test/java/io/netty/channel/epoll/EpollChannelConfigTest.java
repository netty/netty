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
package io.netty.channel.epoll;

import io.netty.channel.ChannelException;

import io.netty.channel.unix.Buffer;
import io.netty.channel.unix.IntegerUnixChannelOption;
import io.netty.channel.unix.RawUnixChannelOption;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class EpollChannelConfigTest {

    @Test
    public void testOptionGetThrowsChannelException() throws Exception {
        Epoll.ensureAvailability();
        EpollSocketChannel channel = new EpollSocketChannel();
        channel.config().getSoLinger();
        channel.fd().close();
        try {
            channel.config().getSoLinger();
            fail();
        } catch (ChannelException e) {
            // expected
        }
    }

    @Test
    public void testOptionSetThrowsChannelException() throws Exception {
        Epoll.ensureAvailability();
        EpollSocketChannel channel = new EpollSocketChannel();
        channel.config().setKeepAlive(true);
        channel.fd().close();
        try {
            channel.config().setKeepAlive(true);
            fail();
        } catch (ChannelException e) {
            // expected
        }
    }

    @Test
    public void testIntegerOption() throws Exception {
        Epoll.ensureAvailability();
        EpollSocketChannel channel = new EpollSocketChannel();
        IntegerUnixChannelOption opt = new IntegerUnixChannelOption("INT_OPT", 1, 2);
        Integer zero = 0;
        assertEquals(zero, channel.config().getOption(opt));
        channel.config().setOption(opt, 1);
        assertNotEquals(zero, channel.config().getOption(opt));
        channel.fd().close();
    }

    @Test
    public void testRawOption() throws Exception {
        Epoll.ensureAvailability();
        EpollSocketChannel channel = new EpollSocketChannel();
        // Value for SOL_SOCKET and SO_REUSEADDR
        // See https://github.com/torvalds/linux/blob/v5.17/include/uapi/asm-generic/socket.h
        RawUnixChannelOption opt = new RawUnixChannelOption("RAW_OPT", 1, 2, 4);

        ByteBuffer disabled = Buffer.allocateDirectWithNativeOrder(4);
        disabled.putInt(0).flip();
        assertEquals(disabled, channel.config().getOption(opt));

        ByteBuffer enabled = Buffer.allocateDirectWithNativeOrder(4);
        enabled.putInt(1).flip();

        channel.config().setOption(opt, enabled);
        assertNotEquals(disabled, channel.config().getOption(opt));
        channel.fd().close();
    }
}

