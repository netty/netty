/*
 * Copyright 2021 The Netty Project
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
package io.netty.handler.codec.http.multipart;

import io.netty.buffer.api.Buffer;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Random;

import static io.netty.buffer.api.DefaultGlobalBufferAllocator.DEFAULT_GLOBAL_BUFFER_ALLOCATOR;
import static io.netty.util.CharsetUtil.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@Disabled("buffer migration")
class HttpDataTest {
    private static final byte[] BYTES = new byte[64];

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @ParameterizedTest(name = "{displayName}({0})")
    @MethodSource("data")
    @interface ParameterizedHttpDataTest {
    }

    @SuppressWarnings("rawtypes")
    static HttpData[] data() {
        return new HttpData[]{
                new MemoryAttribute(DEFAULT_GLOBAL_BUFFER_ALLOCATOR, "test", 10),
                new MemoryFileUpload(DEFAULT_GLOBAL_BUFFER_ALLOCATOR, "test", "", "text/plain",
                        null, UTF_8, 10),
                new MixedAttribute(DEFAULT_GLOBAL_BUFFER_ALLOCATOR, "test", 10, -1),
                new MixedFileUpload(DEFAULT_GLOBAL_BUFFER_ALLOCATOR, "test", "", "text/plain",
                        null, UTF_8, 10, -1),
                new DiskAttribute(DEFAULT_GLOBAL_BUFFER_ALLOCATOR, "test", 10),
                new DiskFileUpload(DEFAULT_GLOBAL_BUFFER_ALLOCATOR, "test", "", "text/plain",
                        null, UTF_8, 10)
        };
    }

    @BeforeAll
    static void setUp() {
        Random rndm = new Random();
        rndm.nextBytes(BYTES);
    }

    @ParameterizedHttpDataTest
    void testAddContentEmptyBuffer(HttpData<?> httpData) throws IOException {
        Buffer content = DEFAULT_GLOBAL_BUFFER_ALLOCATOR.allocate(128);
        httpData.addContent(content, false);
        assertThat(content.isAccessible()).isEqualTo(false);
    }

    @Test
    void testAddContentExceedsDefinedSizeDiskFileUpload() {
        doTestAddContentExceedsSize(
                new DiskFileUpload(DEFAULT_GLOBAL_BUFFER_ALLOCATOR, "test", "", "application/json", null, UTF_8, 10),
                "Out of size: 64 > 10");
    }

    @Test
    void testAddContentExceedsDefinedSizeMemoryFileUpload() {
        doTestAddContentExceedsSize(
                new MemoryFileUpload(DEFAULT_GLOBAL_BUFFER_ALLOCATOR, "test", "", "application/json", null, UTF_8, 10),
                "Out of size: 64 > 10");
    }

    @ParameterizedHttpDataTest
    void testAddContentExceedsMaxSize(final HttpData<?> httpData) {
        httpData.setMaxSize(10);
        doTestAddContentExceedsSize(httpData, "Size exceed allowed maximum capacity");
    }

    @ParameterizedHttpDataTest
    void testSetContentExceedsDefinedSize(final HttpData<?> httpData) {
        doTestSetContentExceedsSize(httpData, "Out of size: 64 > 10");
    }

    @ParameterizedHttpDataTest
    void testSetContentExceedsMaxSize(final HttpData<?> httpData) {
        httpData.setMaxSize(10);
        doTestSetContentExceedsSize(httpData, "Size exceed allowed maximum capacity");
    }

    private static void doTestAddContentExceedsSize(final HttpData<?> httpData, String expectedMessage) {
        Buffer content = DEFAULT_GLOBAL_BUFFER_ALLOCATOR.allocate(128);
        content.writeBytes(BYTES);

        assertThatExceptionOfType(IOException.class)
                .isThrownBy(new ThrowableAssert.ThrowingCallable() {

                    @Override
                    public void call() throws Throwable {
                        httpData.addContent(content, false);
                    }
                })
                .withMessage(expectedMessage);

        assertThat(content.isAccessible()).isEqualTo(false);
    }

    private static void doTestSetContentExceedsSize(final HttpData<?> httpData, String expectedMessage) {
        Buffer content = DEFAULT_GLOBAL_BUFFER_ALLOCATOR.allocate(128);
        content.writeBytes(BYTES);

        assertThatExceptionOfType(IOException.class)
                .isThrownBy(new ThrowableAssert.ThrowingCallable() {

                    @Override
                    public void call() throws Throwable {
                        httpData.setContent(content);
                    }
                })
                .withMessage(expectedMessage);

        assertThat(content.isAccessible()).isEqualTo(false);
    }
}
