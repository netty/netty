/*
 * Copyright 2020 The Netty Project
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
import io.netty.buffer.api.BufferAllocator;
import io.netty.buffer.api.Send;
import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static io.netty.buffer.api.DefaultGlobalBufferAllocator.DEFAULT_GLOBAL_BUFFER_ALLOCATOR;
import static io.netty.util.CharsetUtil.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link AbstractDiskHttpData} test cases
 */
public class AbstractDiskHttpDataTest {

    @Test
    public void testGetChunk() throws Exception {
        TestHttpData test = new TestHttpData(DEFAULT_GLOBAL_BUFFER_ALLOCATOR, "test", UTF_8, 0);
        try {
            File tmpFile = PlatformDependent.createTempFile(UUID.randomUUID().toString(), ".tmp", null);
            tmpFile.deleteOnExit();
            FileOutputStream fos = new FileOutputStream(tmpFile);
            byte[] bytes = new byte[4096];
            ThreadLocalRandom.current().nextBytes(bytes);
            try {
                fos.write(bytes);
                fos.flush();
            } finally {
                fos.close();
            }
            test.setContent(tmpFile);
            Buffer buf1 = test.getChunk(1024);
            assertEquals(buf1.readerOffset(), 0);
            assertEquals(buf1.writerOffset(), 1024);
            Buffer buf2 = test.getChunk(1024);
            assertEquals(buf2.readerOffset(), 0);
            assertEquals(buf2.writerOffset(), 1024);
            Assertions.assertNotEquals(buf1, buf2, "Buffers should not be equal");
        } finally {
            test.delete();
        }
    }

    private static final class TestHttpData extends AbstractDiskHttpData<TestHttpData> {

        private TestHttpData(BufferAllocator allocator, String name, Charset charset, long size) {
            super(allocator, name, charset, size);
        }

        @Override
        protected String getDiskFilename() {
            return null;
        }

        @Override
        protected String getPrefix() {
            return null;
        }

        @Override
        protected String getBaseDirectory() {
            return null;
        }

        @Override
        protected String getPostfix() {
            return null;
        }

        @Override
        protected boolean deleteOnExit() {
            return false;
        }

        @Override
        public HttpDataType getHttpDataType() {
            return null;
        }

        @Override
        public int compareTo(InterfaceHttpData o) {
            return 0;
        }

        @Override
        public Send<TestHttpData> send() {
            return Send.sending(TestHttpData.class, () -> this);
        }
    }
}
