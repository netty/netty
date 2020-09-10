/*
 * Copyright 2020 The Netty Project
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
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpRequest;
import org.junit.Test;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static org.junit.Assert.*;

/**
 * Test DeleteFileOnExitHook
 */
public class DeleteFileOnExitHookTest {
    private static final HttpRequest req1 = new DefaultHttpRequest(HTTP_1_1, POST, "/form");
    final String dir = "target/DeleteFileOnExitHookTest/tmp";

    @Test
    public void testTriggerDeleteFileOnExitHook() throws IOException {
        final DefaultHttpDataFactory defaultHttpDataFactory = new DefaultHttpDataFactory(true);
        File baseDir = new File(dir);
        baseDir.mkdirs();  // we don't need to clean it since it is in volatile files anyway

        defaultHttpDataFactory.setBaseDir(dir);
        defaultHttpDataFactory.setDeleteOnExit(true);
        final FileUpload fu = defaultHttpDataFactory.createFileUpload(
                req1, "attribute1", "tmp_f.txt", "text/plain", null, null, 0);

        fu.setContent(Unpooled.wrappedBuffer(new byte[]{1, 2, 3, 4}));
        assertTrue(fu.getFile().exists());
    }

    @Test
    public void testDeleteFileOnExitHookExecutionSuccessful() {
        File[] files = new File(dir).listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith(DiskFileUpload.prefix);
            }
        });

        assertEquals(0, files.length);
    }

    @Test
    public void testRemoveDeleteFileOnExitHook() throws IOException {
        final DefaultHttpDataFactory defaultHttpDataFactory = new DefaultHttpDataFactory(true);
        File baseDir = new File(dir);
        baseDir.mkdirs();  // we don't need to clean it since it is in volatile files anyway

        defaultHttpDataFactory.setBaseDir(dir);
        defaultHttpDataFactory.setDeleteOnExit(true);
        final FileUpload fu = defaultHttpDataFactory.createFileUpload(
                req1, "attribute1", "tmp_f.txt", "text/plain", null, null, 0);

        fu.setContent(Unpooled.wrappedBuffer(new byte[]{1, 2, 3, 4}));
        assertTrue(fu.getFile().exists());

        String filePath = fu.getFile().getPath();
        assertTrue(DeleteFileOnExitHook.checkFileExist(filePath));

        fu.release();
        assertFalse(DeleteFileOnExitHook.checkFileExist(filePath));
    }
}
