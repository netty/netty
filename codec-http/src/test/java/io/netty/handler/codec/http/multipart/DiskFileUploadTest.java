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

import io.netty.buffer.Unpooled;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DiskFileUploadTest {

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
}
