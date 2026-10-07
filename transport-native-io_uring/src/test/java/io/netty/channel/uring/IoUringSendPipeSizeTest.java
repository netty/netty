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

import io.netty.channel.unix.FileDescriptor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class IoUringSendPipeSizeTest {

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void setAndGetPipeSize() throws IOException {
        FileDescriptor[] pipe = FileDescriptor.pipe();
        try {
            int fd = pipe[1].intValue();
            int original = Native.getPipeSize(fd);
            assertTrue(original > 0, "getPipeSize returned " + original);

            // Shrinking a pipe is allowed down to the page size and does not require any privileges.
            assumeTrue(original > Native.PAGE_SIZE,
                    "default pipe size (" + original + ") is not larger than a page");
            int result = Native.setPipeSize(fd, Native.PAGE_SIZE);
            assertEquals(Native.PAGE_SIZE, result);
            assertEquals(Native.PAGE_SIZE, Native.getPipeSize(fd));
        } finally {
            pipe[0].close();
            pipe[1].close();
        }
    }
}
