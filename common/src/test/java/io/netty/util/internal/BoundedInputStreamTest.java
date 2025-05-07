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

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BoundedInputStreamTest {

    @RepeatedTest(50)
    void testBoundEnforced() throws IOException {
        final byte[] bytes = new byte[64];
        PlatformDependent.threadLocalRandom().nextBytes(bytes);
        final BoundedInputStream reader = new BoundedInputStream(new ByteArrayInputStream(bytes), bytes.length - 1);
        assertEquals(bytes[0], (byte) reader.read());

        assertThrows(IOException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                int max = bytes.length;
                do {
                    int result = reader.read(new byte[max], 0, max);
                    assertThat(result).isNotEqualTo(-1);
                    max -= result;
                } while (max > 0);
            }
        });
        reader.close();
    }

    @Test
    void testBoundEnforced256() throws IOException {
        final byte[] bytes = new byte[256];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) i;
        }
        final BoundedInputStream reader = new BoundedInputStream(new ByteArrayInputStream(bytes), bytes.length - 1);
        for (byte expectedByte : bytes) {
            assertEquals(expectedByte, (byte) reader.read());
        }

        assertThrows(IOException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                reader.read();
            }
        });
        assertThrows(IOException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                reader.read(new byte[1], 0, 1);
            }
        });
        reader.close();
    }

    @RepeatedTest(50)
    void testBigReadsPermittedIfUnderlyingStreamIsSmall() throws IOException {
        final byte[] bytes = new byte[64];
        PlatformDependent.threadLocalRandom().nextBytes(bytes);
        final BoundedInputStream reader = new BoundedInputStream(new ByteArrayInputStream(bytes), 8192);
        final byte[] buffer = new byte[10000];
        assertThat(reader.read(buffer, 0, 10000)).isEqualTo(64);
        assertArrayEquals(bytes, Arrays.copyOfRange(buffer, 0, 64));
        reader.close();
    }
}
