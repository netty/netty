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
package io.netty.handler.ssl;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeNotNull;

public abstract class SslContextTest {

    @Test(expected = IOException.class)
    public void testUnencryptedEmptyPassword() throws Exception {
        PrivateKey key = SslContext.toPrivateKey(
                new File(getClass().getResource("test2_unencrypted.pem").getFile()), "");
        Assert.assertNotNull(key);
    }

    @Test
    public void testUnEncryptedNullPassword() throws Exception {
        PrivateKey key = SslContext.toPrivateKey(
                new File(getClass().getResource("test2_unencrypted.pem").getFile()), null);
        Assert.assertNotNull(key);
    }

    @Test
    public void testEncryptedEmptyPassword() throws Exception {
        PrivateKey key = SslContext.toPrivateKey(
                new File(getClass().getResource("test_encrypted_empty_pass.pem").getFile()), "");
        Assert.assertNotNull(key);
    }

    @Test(expected = InvalidKeySpecException.class)
    public void testEncryptedNullPassword() throws Exception {
        SslContext.toPrivateKey(
                new File(getClass().getResource("test_encrypted_empty_pass.pem").getFile()), null);
    }

    @Test
    public void testSslServerWithEncryptedPrivateKey() throws SSLException {
        File keyFile = new File(getClass().getResource("test_encrypted.pem").getFile());
        File crtFile = new File(getClass().getResource("test.crt").getFile());

        newServerContext(crtFile, keyFile, "12345");
    }

    @Test
    public void testSslServerWithEncryptedPrivateKey2() throws SSLException {
        File keyFile = new File(getClass().getResource("test2_encrypted.pem").getFile());
        File crtFile = new File(getClass().getResource("test2.crt").getFile());

        newServerContext(crtFile, keyFile, "12345");
    }

    @Test
    public void testSslServerWithUnencryptedPrivateKey() throws SSLException {
        File keyFile = new File(getClass().getResource("test_unencrypted.pem").getFile());
        File crtFile = new File(getClass().getResource("test.crt").getFile());

        newServerContext(crtFile, keyFile, null);
    }

    @Test(expected = SSLException.class)
    public void testSslServerWithUnencryptedPrivateKeyEmptyPass() throws SSLException {
        File keyFile = new File(getClass().getResource("test_unencrypted.pem").getFile());
        File crtFile = new File(getClass().getResource("test.crt").getFile());

        newServerContext(crtFile, keyFile, "");
    }

    @Test
    public void testSupportedCiphers() throws KeyManagementException, NoSuchAlgorithmException, SSLException {
        SSLContext jdkSslContext = SSLContext.getInstance("TLS");
        jdkSslContext.init(null, null, null);
        SSLEngine sslEngine = jdkSslContext.createSSLEngine();

        String unsupportedCipher = "TLS_DH_anon_WITH_DES_CBC_SHA";
        IllegalArgumentException exception = null;
        try {
            sslEngine.setEnabledCipherSuites(new String[] {unsupportedCipher});
        } catch (IllegalArgumentException e) {
            exception = e;
        }
        assumeNotNull(exception);
        File keyFile = new File(getClass().getResource("test_unencrypted.pem").getFile());
        File crtFile = new File(getClass().getResource("test.crt").getFile());

        SslContext sslContext = newServerContext(crtFile, keyFile, null);
        assertFalse(sslContext.cipherSuites().contains(unsupportedCipher));
    }

    protected abstract SslContext newServerContext(File crtFile, File keyFile, String pass) throws SSLException;
}
