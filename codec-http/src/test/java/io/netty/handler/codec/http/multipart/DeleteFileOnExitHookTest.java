/*
 * Copyright 2017 The Netty Project
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
import java.io.IOException;

import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test DeleteFileOnExitHook
 */
public class DeleteFileOnExitHookTest {
    private static final HttpRequest req1 = new DefaultHttpRequest(HTTP_1_1, POST, "/form");

    @Test
    public void customBaseDirAndDeleteOnHookExit() throws IOException {
        final DefaultHttpDataFactory defaultHttpDataFactory = new DefaultHttpDataFactory(true);
        final String dir = "target/DeleteFileOnExitHookTest/customBaseDirAndDeleteOnHookExit";
        File baseDir = new File(dir);
        baseDir.mkdirs();  // we don't need to clean it since it is in volatile files anyway

        defaultHttpDataFactory.setBaseDir(dir);
        defaultHttpDataFactory.setDeleteOnExit(true);
        final Attribute attr = defaultHttpDataFactory.createAttribute(req1, "attribute1");
        final FileUpload fu = defaultHttpDataFactory.createFileUpload(
                req1, "attribute1", "tmp_f.txt", "text/plain", null, null, 0);
        //DeleteFileOnExitHook.add(new File(dir, fu.getFilename()).getAbsolutePath());

        assertEquals(dir, DiskAttribute.class.cast(attr).getBaseDirectory());
        assertEquals(dir, DiskFileUpload.class.cast(fu).getBaseDirectory());
        assertTrue(DiskAttribute.class.cast(attr).deleteOnExit());
        assertTrue(DiskFileUpload.class.cast(fu).deleteOnExit());

        fu.setContent(Unpooled.wrappedBuffer(new byte[]{1, 2, 3, 4}));
        assertTrue(fu.getFile().exists());
        //fu.delete();

        assertEquals(0, fu.getFile().length());
        fu.delete();
        //fu.release();
    }
}
