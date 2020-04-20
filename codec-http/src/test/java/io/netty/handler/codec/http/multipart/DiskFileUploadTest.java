/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
import io.netty.util.CharsetUtil;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DiskFileUploadTest {
    @Test
    public void testSpecificCustomBaseDir() throws IOException {
        File baseDir = new File("target/DiskFileUploadTest/testSpecificCustomBaseDir");
        baseDir.mkdirs(); // we don't need to clean it since it is in volatile files anyway
        DiskFileUpload f =
                new DiskFileUpload("d1", "d1", "application/json", null, null, 100,
                        baseDir.getAbsolutePath(), false);

        f.setContent(Unpooled.EMPTY_BUFFER);

        assertTrue(f.getFile().getAbsolutePath().startsWith(baseDir.getAbsolutePath()));
        assertTrue(f.getFile().exists());
        assertEquals(0, f.getFile().length());
        f.delete();
    }

    @Test
    public final void testDiskFileUploadEquals() {
        DiskFileUpload f2 =
                new DiskFileUpload("d1", "d1", "application/json", null, null, 100);
        assertEquals(f2, f2);
        f2.delete();
    }

     @Test
     public void testEmptyBufferSetMultipleTimes() throws IOException {
         DiskFileUpload f =
                 new DiskFileUpload("d1", "d1", "application/json", null, null, 100);

         f.setContent(Unpooled.EMPTY_BUFFER);

         assertTrue(f.getFile().exists());
         assertEquals(0, f.getFile().length());
         f.setContent(Unpooled.EMPTY_BUFFER);
         assertTrue(f.getFile().exists());
         assertEquals(0, f.getFile().length());
         f.delete();
     }

    @Test
    public void testEmptyBufferSetAfterNonEmptyBuffer() throws IOException {
        DiskFileUpload f =
                new DiskFileUpload("d1", "d1", "application/json", null, null, 100);

        f.setContent(Unpooled.wrappedBuffer(new byte[] { 1, 2, 3, 4 }));

        assertTrue(f.getFile().exists());
        assertEquals(4, f.getFile().length());
        f.setContent(Unpooled.EMPTY_BUFFER);
        assertTrue(f.getFile().exists());
        assertEquals(0, f.getFile().length());
        f.delete();
    }

    @Test
    public void testNonEmptyBufferSetMultipleTimes() throws IOException {
        DiskFileUpload f =
                new DiskFileUpload("d1", "d1", "application/json", null, null, 100);

        f.setContent(Unpooled.wrappedBuffer(new byte[] { 1, 2, 3, 4 }));

        assertTrue(f.getFile().exists());
        assertEquals(4, f.getFile().length());
        f.setContent(Unpooled.wrappedBuffer(new byte[] { 1, 2}));
        assertTrue(f.getFile().exists());
        assertEquals(2, f.getFile().length());
        f.delete();
    }

    @Test
    public void testAddContents() throws Exception {
        DiskFileUpload f1 = new DiskFileUpload("file1", "file1", "application/json", null, null, 0);
        try {
            String json = "{\"foo\":\"bar\"}";
            byte[] bytes = json.getBytes(CharsetUtil.UTF_8);
            f1.addContent(Unpooled.wrappedBuffer(bytes), true);
            assertEquals(json, f1.getString());
            assertArrayEquals(bytes, f1.get());
            File file = f1.getFile();
            assertEquals((long) bytes.length, file.length());
            FileInputStream fis = new FileInputStream(file);
            try {
                byte[] buf = new byte[bytes.length];
                int offset = 0;
                int read = 0;
                int len = buf.length;
                while ((read = fis.read(buf, offset, len)) > 0) {
                    len -= read;
                    offset += read;
                    if (len <= 0 || offset >= buf.length) {
                        break;
                    }
                }
                assertArrayEquals(bytes, buf);
            } finally {
                fis.close();
            }
        } finally {
            f1.delete();
        }
    }

    @Test
    public void testSetContentFromByteBuf() throws Exception {
        DiskFileUpload f1 = new DiskFileUpload("file2", "file2", "application/json", null, null, 0);
        try {
            String json = "{\"hello\":\"world\"}";
            byte[] bytes = json.getBytes(CharsetUtil.UTF_8);
            f1.setContent(Unpooled.wrappedBuffer(bytes));
            assertEquals(json, f1.getString());
            assertArrayEquals(bytes, f1.get());
            File file = f1.getFile();
            assertEquals((long) bytes.length, file.length());
            assertArrayEquals(bytes, doReadFile(file, bytes.length));
        } finally {
            f1.delete();
        }
    }

    @Test
    public void testSetContentFromInputStream() throws Exception {
        String json = "{\"hello\":\"world\",\"foo\":\"bar\"}";
        DiskFileUpload f1 = new DiskFileUpload("file3", "file3", "application/json", null, null, 0);
        try {
            byte[] bytes = json.getBytes(CharsetUtil.UTF_8);
            ByteBuf buf = Unpooled.wrappedBuffer(bytes);
            InputStream is = new ByteBufInputStream(buf);
            try {
                f1.setContent(is);
                assertEquals(json, f1.getString());
                assertArrayEquals(bytes, f1.get());
                File file = f1.getFile();
                assertEquals((long) bytes.length, file.length());
                assertArrayEquals(bytes, doReadFile(file, bytes.length));
            } finally {
                buf.release();
                is.close();
            }
        } finally {
            f1.delete();
        }
    }

    private static byte[] doReadFile(File file, int maxRead) throws Exception {
        FileInputStream fis = new FileInputStream(file);
        try {
            byte[] buf = new byte[maxRead];
            int offset = 0;
            int read = 0;
            int len = buf.length;
            while ((read = fis.read(buf, offset, len)) > 0) {
                len -= read;
                offset += read;
                if (len <= 0 || offset >= buf.length) {
                    break;
                }
            }
            return buf;
        } finally {
            fis.close();
        }
    }
}
