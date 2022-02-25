/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty5.buffer.api.tests;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static java.nio.charset.StandardCharsets.UTF_8;

public class BufferEqualsTest extends BufferTestSupport {

    @ParameterizedTest
    @MethodSource("allocators")
    void emptyBuffersAreEqual(Fixture fixture) {
        testEqualsCommutative(fixture, "");
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void equalBuffersAreEqual(Fixture fixture) {
        testEqualsCommutative(fixture, "foo");
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void differentBuffersAreNotEqual(Fixture fixture) {
        byte[] data1 = "foo".getBytes(UTF_8);
        byte[] data2 = "foo1".getBytes(UTF_8);
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf1 = allocator.allocate(data1.length);
             Buffer buf2 = allocator.allocate(data2.length)) {
            buf1.writeBytes(data1);
            buf2.writeBytes(data2);

            Assertions.assertNotEquals(buf1, buf2);
            Assertions.assertNotEquals(buf2, buf1);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void sameReadableContentBuffersAreEqual(Fixture fixture) {
        byte[] data1 = "foo".getBytes(UTF_8);
        byte[] data2 = "notfoomaybe".getBytes(UTF_8);
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf1 = allocator.allocate(data1.length);
             Buffer buf2 = allocator.allocate(data2.length)) {
            buf1.writeBytes(data1);
            buf2.writeBytes(data2);
            buf2.skipReadable(3);
            buf2.skipWritable(-5);

            Assertions.assertEquals(buf1, buf2);
            Assertions.assertEquals(buf2, buf1);
        }
    }

    private static void testEqualsCommutative(Fixture fixture, String value) {
        final byte[] data = value.getBytes(UTF_8);
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf1 = allocator.allocate(data.length);
             Buffer buf2 = allocator.allocate(data.length)) {
            buf1.writeBytes(data);
            buf2.writeBytes(data);

            Assertions.assertEquals(buf1, buf2);
            Assertions.assertEquals(buf2, buf1);
        }
    }
}
