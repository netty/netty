/*
 * Copyright 2025 The Netty Project
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
package io.netty.channel.uring;

import io.netty.channel.unix.Buffer;
import io.netty.channel.unix.IntegerUnixChannelOption;
import io.netty.channel.unix.RawUnixChannelOption;
import io.netty.util.internal.CleanableDirectBuffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class IoUringChannelConfigTest {
    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
    }

    @Test
    public void testIntegerOption() throws Exception {
        IoUringSocketChannel channel = new IoUringSocketChannel();
        IntegerUnixChannelOption opt = new IntegerUnixChannelOption("INT_OPT", 1, 2);
        Integer zero = 0;
        assertEquals(zero, channel.config().getOption(opt));
        channel.config().setOption(opt, 1);
        assertNotEquals(zero, channel.config().getOption(opt));
        channel.fd().close();
    }

    @Test
    public void testRawOption() throws Exception {
        IoUringSocketChannel channel = new IoUringSocketChannel();
        // Value for SOL_SOCKET and SO_REUSEADDR
        // See https://github.com/torvalds/linux/blob/v5.17/include/uapi/asm-generic/socket.h
        RawUnixChannelOption opt = new RawUnixChannelOption("RAW_OPT", 1, 2, 4);

        CleanableDirectBuffer disabledCleanable = Buffer.allocateDirectBufferWithNativeOrder(4);
        ByteBuffer disabled = disabledCleanable.buffer();
        disabled.putInt(0).flip();
        assertEquals(disabled, channel.config().getOption(opt));

        CleanableDirectBuffer enabledCleanable = Buffer.allocateDirectBufferWithNativeOrder(4);
        ByteBuffer enabled = enabledCleanable.buffer();
        enabled.putInt(1).flip();

        channel.config().setOption(opt, enabled);
        assertNotEquals(disabled, channel.config().getOption(opt));
        channel.fd().close();
        disabledCleanable.clean();
        enabledCleanable.clean();
    }
}
