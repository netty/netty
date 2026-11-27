/*
 * Copyright 2026 The Netty Project
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

import io.netty.channel.ChannelConfig;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.unix.Errors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A 0 from the splice of the pipe into the socket means the socket is full, and says nothing about the file. Only a 0
 * from the splice of the file into the pipe may be the end of the file, and is checked.
 */
public class IoUringFileRegionValidationTest {

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
    }

    @Test
    public void testFullSocketDoesNotValidateTheFile() {
        assertValidations(Errors.ERRNO_EAGAIN_NEGATIVE, IoUringFileRegion.SPLICE_TO_SOCKET, 0);
    }

    @Test
    public void testZeroFromTheFileValidatesTheFile() {
        assertValidations(0, IoUringFileRegion.SPLICE_TO_PIPE, 1);
    }

    private static void assertValidations(int res, short data, int expected) {
        ValidationCountingChannel channel = new ValidationCountingChannel();
        // Only the completion is handled: nothing is spliced, so the file is never opened.
        IoUringFileRegion region = new IoUringFileRegion(new DefaultFileRegion(new File("never-opened"), 0, 16));
        try {
            AbstractIoUringStreamChannel.IoUringStreamUnsafe unsafe =
                    (AbstractIoUringStreamChannel.IoUringStreamUnsafe) channel.unsafe();
            // false: the splice is tried again.
            assertThat(unsafe.handleWriteCompleteFileRegion(null, region, res, data)).isFalse();
            assertThat(channel.validations).isEqualTo(expected);
        } finally {
            region.release();
            channel.unsafe().closeForcibly();
        }
    }

    private static final class ValidationCountingChannel extends AbstractIoUringStreamChannel {
        private final ChannelConfig config = new DefaultChannelConfig(this);
        int validations;

        ValidationCountingChannel() {
            super(null, LinuxSocket.newSocketStream(), false);
        }

        @Override
        public ChannelConfig config() {
            return config;
        }

        @Override
        protected void validateFileRegion(DefaultFileRegion region, long position) {
            validations++;
        }
    }
}
