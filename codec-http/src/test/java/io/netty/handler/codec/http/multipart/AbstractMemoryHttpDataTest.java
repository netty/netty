/*
 * Copyright 2012 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.buffer.api.Buffer;
import io.netty.buffer.api.Send;
import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static io.netty.buffer.api.DefaultGlobalBufferAllocator.DEFAULT_GLOBAL_BUFFER_ALLOCATOR;
import static io.netty.util.CharsetUtil.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link AbstractMemoryHttpData} test cases. */
public class AbstractMemoryHttpDataTest {

    @Test
    public void testSetContentFromFile() throws Exception {
        TestHttpData test = new TestHttpData("test", UTF_8, 0);
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
            Buffer buf = test.getBuffer();
            assertEquals(buf.readerOffset(), 0);
            assertEquals(buf.writerOffset(), bytes.length);
            assertArrayEquals(bytes, test.get());
            byte[] bufBytes = new byte[buf.readableBytes()];
            buf.copyInto(buf.readerOffset(), bufBytes, 0, buf.readableBytes());
            assertArrayEquals(bytes, bufBytes);
        } finally {
            //release the ByteBuf
            test.delete();
        }
    }

    @Test
    public void testRenameTo() throws Exception {
        TestHttpData test = new TestHttpData("test", UTF_8, 0);
        try {
            File tmpFile = PlatformDependent.createTempFile(UUID.randomUUID().toString(), ".tmp", null);
            tmpFile.deleteOnExit();
            final int totalByteCount = 4096;
            byte[] bytes = new byte[totalByteCount];
            ThreadLocalRandom.current().nextBytes(bytes);
            Buffer content = DEFAULT_GLOBAL_BUFFER_ALLOCATOR.allocate(bytes.length).writeBytes(bytes);
            test.setContent(content);
            boolean succ = test.renameTo(tmpFile);
            assertTrue(succ);
            try (FileInputStream fis = new FileInputStream(tmpFile)) {
                byte[] buf = new byte[totalByteCount];
                int count;
                int offset = 0;
                int size = totalByteCount;
                while ((count = fis.read(buf, offset, size)) > 0) {
                    offset += count;
                    size -= count;
                    if (offset >= totalByteCount || size <= 0) {
                        break;
                    }
                }
                assertArrayEquals(bytes, buf);
                assertEquals(0, fis.available());
            }
        } finally {
            //release the ByteBuf in AbstractMemoryHttpData
            test.delete();
        }
    }
    /**
     * Provide content into HTTP data with input stream.
     *
     * @throws Exception In case of any exception.
     */
    @Disabled("buffer migration")
    @Test
    public void testSetContentFromStream() throws Exception {
        // definedSize=0
        TestHttpData test = new TestHttpData("test", UTF_8, 0);
        String contentStr = "foo_test";
        ByteBuf buf = Unpooled.wrappedBuffer(contentStr.getBytes(UTF_8));
        int readerIndex = buf.readerIndex();

        try (ByteBufInputStream is = new ByteBufInputStream(buf)) {
            test.setContent(is);
            assertFalse(buf.isReadable());
            assertEquals(test.getString(UTF_8), contentStr);
            buf.readerIndex(readerIndex);
            assertEquals(buf, test.getBuffer());
        }

        Random random = new SecureRandom();

        for (int i = 0; i < 20; i++) {
            // Generate input data bytes.
            int size = random.nextInt(Short.MAX_VALUE);
            byte[] bytes = new byte[size];

            random.nextBytes(bytes);

            // Generate parsed HTTP data block.
            TestHttpData data = new TestHttpData("name", UTF_8, 0);

            data.setContent(new ByteArrayInputStream(bytes));

            // Validate stored data.
            Buffer buffer = data.getBuffer();

            assertEquals(0, buffer.readerOffset());
            assertEquals(bytes.length, buffer.writerOffset());

            byte[] bufBytes = new byte[buf.readableBytes()];
            buffer.copyInto(buffer.readerOffset(), bufBytes, 0, bytes.length);
            assertArrayEquals(bytes, bufBytes);
            assertArrayEquals(bytes, data.get());
        }
    }

    /** Memory-based HTTP data implementation for test purposes. */
    private static final class TestHttpData extends AbstractMemoryHttpData<TestHttpData> {
        private boolean closed;

        /**
         * Constructs HTTP data for tests.
         *
         * @param name    Name of parsed data block.
         * @param charset Used charset for data decoding.
         * @param size    Expected data block size.
         */
        private TestHttpData(String name, Charset charset, long size) {
            super(DEFAULT_GLOBAL_BUFFER_ALLOCATOR, name, charset, size);
        }

        @Override
        public InterfaceHttpData.HttpDataType getHttpDataType() {
            throw reject();
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj);
        }

        private static UnsupportedOperationException reject() {
            throw new UnsupportedOperationException("Should never be called.");
        }

        @Override
        public Send<TestHttpData> send() {
            throw new UnsupportedOperationException("Should never be called.");
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public boolean isAccessible() {
            return !closed;
        }

        @Override
        public int compareTo(InterfaceHttpData<TestHttpData> o) {
            throw new UnsupportedOperationException("Should never be called.");
        }
    }
}
