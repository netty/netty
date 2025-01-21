/*
 * Copyright 2024 The Netty Project
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
package io.netty.util.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BoundedInputStreamTest {

    @Test
    void testBoundEnforced() throws IOException {
        final byte[] bytes = new byte[64];
        PlatformDependent.threadLocalRandom().nextBytes(bytes);
        final BoundedInputStream reader = new BoundedInputStream(new ByteArrayInputStream(bytes), bytes.length - 1);
        assertEquals(bytes[0], (byte) reader.read());

        assertThrows(IOException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                reader.read(new byte[64], 0, 64);
            }
        });
        reader.close();
    }

    @Test
    void testBigReadsPermittedIfUnderlyingStreamIsSmall() throws IOException {
        final byte[] bytes = new byte[64];
        PlatformDependent.threadLocalRandom().nextBytes(bytes);
        final BoundedInputStream reader = new BoundedInputStream(new ByteArrayInputStream(bytes), 8192);
        final byte[] buffer = new byte[10000];
        reader.read(buffer, 0, 10000);
        assertArrayEquals(bytes, Arrays.copyOfRange(buffer, 0, 64));
        reader.close();
    }
}
