/*
 * Copyright 2021 The Netty Project
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
package io.netty.handler.ssl;

import io.netty.buffer.ByteBuf;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PemReaderTest {

    @Test
    public void testCertificateInputStreamClosed() throws Exception {
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        TestFilterInputStream inputStream = new TestFilterInputStream(new FileInputStream(ssc.certificate()));
        ByteBuf[] buffers = PemReader.readCertificates(inputStream);
        assertEquals(1, buffers.length);
        buffers[0].release();
        assertTrue(inputStream.isClosed());
    }

    @Test
    public void testPrivateKeyInputStreamClosed() throws Exception {
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        TestFilterInputStream inputStream = new TestFilterInputStream(new FileInputStream(ssc.privateKey()));
        ByteBuf buffer = PemReader.readPrivateKey(inputStream);
        buffer.release();
        assertTrue(inputStream.isClosed());
    }

    private static final class TestFilterInputStream extends FilterInputStream {
        private boolean closed;

        TestFilterInputStream(InputStream in) {
            super(in);
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                closed = true;
            }
        }

        boolean isClosed() {
            return closed;
        }
    }
}
