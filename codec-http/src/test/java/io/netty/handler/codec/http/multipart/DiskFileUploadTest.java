/*
 * Copyright 2016 The Netty Project
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
import io.netty.buffer.api.CompositeBuffer;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static io.netty.buffer.api.DefaultGlobalBufferAllocator.DEFAULT_GLOBAL_BUFFER_ALLOCATOR;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class DiskFileUploadTest {
    @Disabled("buffer migration")
    @Test
    public void testSpecificCustomBaseDir() throws IOException {
        File baseDir = new File("target/DiskFileUploadTest/testSpecificCustomBaseDir");
        assertTrue(baseDir.mkdirs()); // we don't need to clean it since it is in volatile files anyway
        DiskFileUpload f =
                new DiskFileUpload(DEFAULT_GLOBAL_BUFFER_ALLOCATOR, "d1", "d1",
                        "application/json", null, null, 100,
                        baseDir.getAbsolutePath(), false);

        f.setContent(DEFAULT_GLOBAL_BUFFER_ALLOCATOR.allocate(0));

        assertTrue(f.getFile().getAbsolutePath().startsWith(baseDir.getAbsolutePath()));
        assertTrue(f.getFile().exists());
        assertEquals(0, f.getFile().length());
        f.delete();
    }

    @Test
    public final void testDiskFileUploadEquals() {
        DiskFileUpload f2 =
                new DiskFileUpload(DEFAULT_GLOBAL_BUFFER_ALLOCATOR, "d1", "d1",
                        "application/json", null, null, 100);
        assertEquals(f2, f2);
        f2.delete();
    }

     @Test
     public void testEmptyBufferSetMultipleTimes() throws IOException {
         DiskFileUpload f =
                 new DiskFileUpload(DEFAULT_GLOBAL_BUFFER_ALLOCATOR, "d1", "d1",
                         "application/json", null, null, 100);

         f.setContent(DEFAULT_GLOBAL_BUFFER_ALLOCATOR.allocate(0));

         assertTrue(f.getFile().exists());
         assertEquals(0, f.getFile().length());
         f.setContent(DEFAULT_GLOBAL_BUFFER_ALLOCATOR.allocate(0));
         assertTrue(f.getFile().exists());
         assertEquals(0, f.getFile().length());
         f.delete();
     }

    @Test
    public void testEmptyBufferSetAfterNonEmptyBuffer() throws IOException {
        DiskFileUpload f =
                new DiskFileUpload(DEFAULT_GLOBAL_BUFFER_ALLOCATOR, "d1", "d1",
                        "application/json", null, null, 100);

        f.setContent(DEFAULT_GLOBAL_BUFFER_ALLOCATOR.allocate(16).writeBytes(new byte[]{1, 2, 3, 4}));

        assertTrue(f.getFile().exists());
        assertEquals(4, f.getFile().length());
        f.setContent(DEFAULT_GLOBAL_BUFFER_ALLOCATOR.allocate(0));
        assertTrue(f.getFile().exists());
        assertEquals(0, f.getFile().length());
        f.delete();
    }

    @Test
    public void testNonEmptyBufferSetMultipleTimes() throws IOException {
        DiskFileUpload f =
                new DiskFileUpload(DEFAULT_GLOBAL_BUFFER_ALLOCATOR, "d1", "d1",
                        "application/json", null, null, 100);

        f.setContent(DEFAULT_GLOBAL_BUFFER_ALLOCATOR.allocate(16).writeBytes(new byte[]{1, 2, 3, 4}));

        assertTrue(f.getFile().exists());
        assertEquals(4, f.getFile().length());
        f.setContent(DEFAULT_GLOBAL_BUFFER_ALLOCATOR.allocate(16).writeBytes(new byte[]{1, 2}));
        assertTrue(f.getFile().exists());
        assertEquals(2, f.getFile().length());
        f.delete();
    }

    @Test
    public void testAddContents() throws Exception {
        DiskFileUpload f1 = new DiskFileUpload(DEFAULT_GLOBAL_BUFFER_ALLOCATOR, "file1", "file1",
                "application/json", null, null, 0);
        try {
            byte[] jsonBytes = new byte[4096];
            ThreadLocalRandom.current().nextBytes(jsonBytes);

            f1.addContent(DEFAULT_GLOBAL_BUFFER_ALLOCATOR.allocate(1024)
                    .writeBytes(jsonBytes, 0, 1024), false);
            f1.addContent(DEFAULT_GLOBAL_BUFFER_ALLOCATOR.allocate(jsonBytes.length - 1024)
                    .writeBytes(jsonBytes, 1024, jsonBytes.length - 1024), true);
            assertArrayEquals(jsonBytes, f1.get());

            File file = f1.getFile();
            assertEquals(jsonBytes.length, file.length());

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buf = new byte[jsonBytes.length];
                int offset = 0;
                int read;
                int len = buf.length;
                while ((read = fis.read(buf, offset, len)) > 0) {
                    len -= read;
                    offset += read;
                    if (len <= 0 || offset >= buf.length) {
                        break;
                    }
                }
                assertArrayEquals(jsonBytes, buf);
            }
        } finally {
            f1.delete();
        }
    }

    @Test
    public void testSetContentFromByteBuf() throws Exception {
        DiskFileUpload f1 = new DiskFileUpload(DEFAULT_GLOBAL_BUFFER_ALLOCATOR, "file2", "file2",
                "application/json", null, null, 0);
        try {
            String json = "{\"hello\":\"world\"}";
            byte[] bytes = json.getBytes(CharsetUtil.UTF_8);
            f1.setContent(DEFAULT_GLOBAL_BUFFER_ALLOCATOR.allocate(bytes.length).writeBytes(bytes));
            assertEquals(json, f1.getString());
            assertArrayEquals(bytes, f1.get());
            File file = f1.getFile();
            assertEquals(bytes.length, file.length());
            assertArrayEquals(bytes, doReadFile(file, bytes.length));
        } finally {
            f1.delete();
        }
    }

    @Test
    public void testSetContentFromInputStream() throws Exception {
        String json = "{\"hello\":\"world\",\"foo\":\"bar\"}";
        DiskFileUpload f1 = new DiskFileUpload(DEFAULT_GLOBAL_BUFFER_ALLOCATOR, "file3", "file3",
                "application/json", null, null, 0);
        try {
            byte[] bytes = json.getBytes(CharsetUtil.UTF_8);
            ByteBuf buf = Unpooled.wrappedBuffer(bytes);
            try (InputStream is = new ByteBufInputStream(buf)) {
                f1.setContent(is);
                assertEquals(json, f1.getString());
                assertArrayEquals(bytes, f1.get());
                File file = f1.getFile();
                assertEquals(bytes.length, file.length());
                assertArrayEquals(bytes, doReadFile(file, bytes.length));
            } finally {
                buf.release();
            }
        } finally {
            f1.delete();
        }
    }

    @Test
    public void testAddContentFromByteBuf() throws Exception {
        testAddContentFromByteBuf0(false);
    }

    @Test
    public void testAddContentFromCompositeByteBuf() throws Exception {
        testAddContentFromByteBuf0(true);
    }

    private static void testAddContentFromByteBuf0(boolean composite) throws Exception {
        DiskFileUpload f1 = new DiskFileUpload(DEFAULT_GLOBAL_BUFFER_ALLOCATOR, "file3", "file3",
                "application/json", null, null, 0);
        try {
            byte[] bytes = new byte[4096];
            ThreadLocalRandom.current().nextBytes(bytes);

            final Buffer buffer;

            if (composite) {
                buffer = CompositeBuffer.compose(DEFAULT_GLOBAL_BUFFER_ALLOCATOR,
                                DEFAULT_GLOBAL_BUFFER_ALLOCATOR.allocate(bytes.length / 2)
                                        .writeBytes(bytes, 0 , bytes.length / 2).send(),
                        DEFAULT_GLOBAL_BUFFER_ALLOCATOR.allocate(bytes.length / 2)
                                .writeBytes(bytes, bytes.length / 2, bytes.length / 2).send());
            } else {
                buffer = DEFAULT_GLOBAL_BUFFER_ALLOCATOR.allocate(bytes.length).writeBytes(bytes);
            }
            f1.addContent(buffer, true);
            Buffer buf = f1.getBuffer();
            assertEquals(buf.readerOffset(), 0);
            assertEquals(buf.writerOffset(), bytes.length);
            byte[] bufBytes = new byte[buf.readableBytes()];
            buf.copyInto(buf.readerOffset(), bufBytes, 0, bufBytes.length);
            assertArrayEquals(bytes, bufBytes);
        } finally {
            //release the Buffer
            f1.delete();
        }
    }

    private static byte[] doReadFile(File file, int maxRead) throws Exception {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[maxRead];
            int offset = 0;
            int read;
            int len = buf.length;
            while ((read = fis.read(buf, offset, len)) > 0) {
                len -= read;
                offset += read;
                if (len <= 0 || offset >= buf.length) {
                    break;
                }
            }
            return buf;
        }
    }

    @Test
    public void testDelete() throws Exception {
        String json = "{\"foo\":\"bar\"}";
        byte[] bytes = json.getBytes(CharsetUtil.UTF_8);
        File tmpFile = null;
        DiskFileUpload f1 = new DiskFileUpload(DEFAULT_GLOBAL_BUFFER_ALLOCATOR, "file4", "file4",
                "application/json", null, null, 0);
        try {
            assertNull(f1.getFile());
            f1.setContent(DEFAULT_GLOBAL_BUFFER_ALLOCATOR.allocate(bytes.length).writeBytes(bytes));
            assertNotNull(tmpFile = f1.getFile());
        } finally {
            f1.delete();
            assertNull(f1.getFile());
            assertNotNull(tmpFile);
            assertFalse(tmpFile.exists());
        }
    }

    @Disabled("buffer migration")
    @Test
    public void setSetContentFromFileExceptionally() throws Exception {
        final long maxSize = 4;
        DiskFileUpload f1 = new DiskFileUpload(DEFAULT_GLOBAL_BUFFER_ALLOCATOR, "file5", "file5",
                "application/json", null, null, 0);
        f1.setMaxSize(maxSize);
        try {
            f1.setContent(DEFAULT_GLOBAL_BUFFER_ALLOCATOR.allocate(1).writeBytes(new byte[(int) maxSize]));
            File originalFile = f1.getFile();
            assertNotNull(originalFile);
            assertEquals(maxSize, originalFile.length());
            assertEquals(maxSize, f1.length());
            byte[] bytes = new byte[8];

            ThreadLocalRandom.current().nextBytes(bytes);
            File tmpFile = PlatformDependent.createTempFile(UUID.randomUUID().toString(), ".tmp", null);
            tmpFile.deleteOnExit();
            try (FileOutputStream fos = new FileOutputStream(tmpFile)) {
                fos.write(bytes);
                fos.flush();
            }
            try {
                f1.setContent(tmpFile);
                fail("should not reach here!");
            } catch (IOException e) {
                assertNotNull(f1.getFile());
                assertEquals(originalFile, f1.getFile());
                assertEquals(maxSize, f1.length());
            }
        } finally {
            f1.delete();
        }
    }
}
