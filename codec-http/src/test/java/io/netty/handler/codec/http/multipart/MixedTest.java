/*
 * Copyright 2022 The Netty Project
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

import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MixedTest {
    @Test
    public void mixedAttributeRefCnt() throws IOException {
        MixedAttribute attribute = new MixedAttribute("foo", 100);
        Assertions.assertEquals(1, attribute.refCnt());
        attribute.retain();
        Assertions.assertEquals(2, attribute.refCnt());

        attribute.addContent(Unpooled.wrappedBuffer(new byte[90]), false);
        Assertions.assertEquals(2, attribute.refCnt());

        attribute.addContent(Unpooled.wrappedBuffer(new byte[90]), true);
        Assertions.assertEquals(2, attribute.refCnt());

        attribute.release(2);
    }

    @Test
    public void mixedFileUploadRefCnt() throws IOException {
        MixedFileUpload upload = new MixedFileUpload("foo", "foo", "foo", "UTF-8", CharsetUtil.UTF_8, 0, 100);
        Assertions.assertEquals(1, upload.refCnt());
        upload.retain();
        Assertions.assertEquals(2, upload.refCnt());

        upload.addContent(Unpooled.wrappedBuffer(new byte[90]), false);
        Assertions.assertEquals(2, upload.refCnt());

        upload.addContent(Unpooled.wrappedBuffer(new byte[90]), true);
        Assertions.assertEquals(2, upload.refCnt());

        upload.release(2);
    }

    @Test
    public void testSpecificCustomBaseDir() throws IOException {
        File baseDir = new File("target/MixedTest/testSpecificCustomBaseDir");
        baseDir.mkdirs(); // we don't need to clean it since it is in volatile files anyway
        MixedFileUpload upload = new MixedFileUpload("foo", "foo", "foo", "UTF-8", CharsetUtil.UTF_8, 1000, 100,
                                                     baseDir.getAbsolutePath(), true);

        upload.addContent(Unpooled.wrappedBuffer(new byte[1000]), true);

        assertTrue(upload.getFile().getAbsolutePath().startsWith(baseDir.getAbsolutePath()));
        assertTrue(upload.getFile().exists());
        assertEquals(1000, upload.getFile().length());
        upload.delete();
    }
}
