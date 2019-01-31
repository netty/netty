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
package io.netty.handler.ssl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;

import org.junit.Test;

public class SslUtilsTest {

    @Test
    public void testPacketLength() throws SSLException, NoSuchAlgorithmException {
        SSLEngine engineBE = newEngine();

        ByteBuffer empty = ByteBuffer.allocate(0);
        ByteBuffer cTOsBE = ByteBuffer.allocate(17 * 1024);

        assertTrue(engineBE.wrap(empty, cTOsBE).bytesProduced() > 0);
    }

    private static SSLEngine newEngine() throws SSLException, NoSuchAlgorithmException  {
        SSLEngine engine = SSLContext.getDefault().createSSLEngine();
        engine.setUseClientMode(true);
        engine.beginHandshake();
        return engine;
    }

    @Test
    public void testIsTLSv13Cipher() {
        assertTrue(SslUtils.isTLSv13Cipher("TLS_AES_128_GCM_SHA256"));
        assertTrue(SslUtils.isTLSv13Cipher("TLS_AES_256_GCM_SHA384"));
        assertTrue(SslUtils.isTLSv13Cipher("TLS_CHACHA20_POLY1305_SHA256"));
        assertTrue(SslUtils.isTLSv13Cipher("TLS_AES_128_CCM_SHA256"));
        assertTrue(SslUtils.isTLSv13Cipher("TLS_AES_128_CCM_8_SHA256"));
        assertFalse(SslUtils.isTLSv13Cipher("TLS_DHE_RSA_WITH_AES_128_GCM_SHA256"));
    }

}
