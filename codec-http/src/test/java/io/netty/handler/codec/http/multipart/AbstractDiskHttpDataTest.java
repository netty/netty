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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.internal.PlatformDependent;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.UUID;

import static io.netty.util.CharsetUtil.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * {@link AbstractDiskHttpData} test cases
 */
public class AbstractDiskHttpDataTest {

    @Test
    public void testGetChunk() throws Exception {
        TestHttpData test = new TestHttpData("test", UTF_8, 0);
        try {
            File tmpFile = File.createTempFile(UUID.randomUUID().toString(), ".tmp");
            tmpFile.deleteOnExit();
            FileOutputStream fos = new FileOutputStream(tmpFile);
            byte[] bytes = new byte[4096];
            PlatformDependent.threadLocalRandom().nextBytes(bytes);
            try {
                fos.write(bytes);
                fos.flush();
            } finally {
                fos.close();
            }
            test.setContent(tmpFile);
            ByteBuf buf1 = test.getChunk(1024);
            assertEquals(buf1.readerIndex(), 0);
            assertEquals(buf1.writerIndex(), 1024);
            ByteBuf buf2 = test.getChunk(1024);
            assertEquals(buf2.readerIndex(), 0);
            assertEquals(buf2.writerIndex(), 1024);
            assertFalse("Arrays should not be equal",
                    Arrays.equals(ByteBufUtil.getBytes(buf1), ByteBufUtil.getBytes(buf2)));
        } finally {
            test.delete();
        }
    }

    private static final class TestHttpData extends AbstractDiskHttpData {

        private TestHttpData(String name, Charset charset, long size) {
            super(name, charset, size);
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
        public HttpData copy() {
            return null;
        }

        @Override
        public HttpData duplicate() {
            return null;
        }

        @Override
        public HttpData retainedDuplicate() {
            return null;
        }

        @Override
        public HttpData replace(ByteBuf content) {
            return null;
        }

        @Override
        public HttpDataType getHttpDataType() {
            return null;
        }

        @Override
        public int compareTo(InterfaceHttpData o) {
            return 0;
        }
    }
}
