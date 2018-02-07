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
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class DiskFileUploadTest {

    @Test
    public void testDiskFileUploadEquals() {
        DiskFileUpload f2 =
                new DiskFileUpload("d1", "d1", "application/json", null, null, 100);
        Assert.assertEquals(f2, f2);
    }

    @Test
    public void testEmptyBufferCaseShouldBeIdempotent() throws IOException {
        DiskFileUpload fileUpload =
                new DiskFileUpload("d1", "d1", "application/json", null, null, 100);
        //ensure https://github.com/netty/netty/issues/6418 works
        fileUpload.setContent(Unpooled.EMPTY_BUFFER);
        fileUpload.setContent(Unpooled.EMPTY_BUFFER);
        fileUpload.setContent(Unpooled.EMPTY_BUFFER);

        Assert.assertTrue("empty buffer should create file", fileUpload.getFile().exists());
    }

    @Test
    public void testWritingToFile() throws IOException {
        DiskFileUpload fileUpload =
                new DiskFileUpload("d1", "d1", "application/json", null, null, 100);
        String dataToWrite = "sample";
        fileUpload.setContent(Unpooled.wrappedBuffer(dataToWrite.getBytes()));

        Assert.assertEquals(readDataFromFile(fileUpload.getFile()), dataToWrite);
    }

    private String readDataFromFile(File file) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(file));
        return br.readLine();
    }
}
