/*
 * Copyright 2016 The Netty Project
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

import io.netty.util.internal.ResourcesUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public abstract class SslContextTest {

    @Test
    public void testUnencryptedEmptyPassword() throws Exception {
        assertThrows(IOException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                SslContext.toPrivateKey(
                        ResourcesUtil.getFile(getClass(), "test2_unencrypted.pem"), "");
            }
        });
    }

    @Test
    public void testUnEncryptedNullPassword() throws Exception {
        PrivateKey key = SslContext.toPrivateKey(
                ResourcesUtil.getFile(getClass(), "test2_unencrypted.pem"), null);
        assertNotNull(key);
    }

    @Test
    public void testEncryptedEmptyPassword() throws Exception {
        PrivateKey key = SslContext.toPrivateKey(
                ResourcesUtil.getFile(getClass(), "test_encrypted_empty_pass.pem"), "");
        assertNotNull(key);
    }

    @Test
    public void testEncryptedNullPassword() throws Exception {
        assertThrows(InvalidKeySpecException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                SslContext.toPrivateKey(
                        ResourcesUtil.getFile(getClass(), "test_encrypted_empty_pass.pem"), null);
            }
        });
    }

    @Test
    public void testSslContextWithEncryptedPrivateKey() throws SSLException {
        File keyFile = ResourcesUtil.getFile(getClass(), "test_encrypted.pem");
        File crtFile = ResourcesUtil.getFile(getClass(), "test.crt");

        newSslContext(crtFile, keyFile, "12345");
    }

    @Test
    public void testSslContextWithEncryptedPrivateKey2() throws SSLException {
        File keyFile = ResourcesUtil.getFile(getClass(), "test2_encrypted.pem");
        File crtFile = ResourcesUtil.getFile(getClass(), "test2.crt");

        newSslContext(crtFile, keyFile, "12345");
    }

    @Test
    public void testSslContextWithUnencryptedPrivateKey() throws SSLException {
        File keyFile = ResourcesUtil.getFile(getClass(), "test_unencrypted.pem");
        File crtFile = ResourcesUtil.getFile(getClass(), "test.crt");

        newSslContext(crtFile, keyFile, null);
    }

    @Test
    public void testSslContextWithUnencryptedPrivateKeyEmptyPass() throws SSLException {
        final File keyFile = ResourcesUtil.getFile(getClass(), "test_unencrypted.pem");
        final File crtFile = ResourcesUtil.getFile(getClass(), "test.crt");

        assertThrows(SSLException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                newSslContext(crtFile, keyFile, "");
            }
        });
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
        assumeTrue(exception != null);
        File keyFile = ResourcesUtil.getFile(getClass(), "test_unencrypted.pem");
        File crtFile = ResourcesUtil.getFile(getClass(), "test.crt");

        SslContext sslContext = newSslContext(crtFile, keyFile, null);
        assertFalse(sslContext.cipherSuites().contains(unsupportedCipher));
    }

    @Test
    public void testUnsupportedParams() throws CertificateException {
        assertThrows(CertificateException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                SslContext.toX509Certificates(
                        new File(getClass().getResource("ec_params_unsupported.pem").getFile()));
            }
        });
    }

    protected abstract SslContext newSslContext(File crtFile, File keyFile, String pass) throws SSLException;

    @Test
    public void testPkcs1UnencryptedRsa() throws Exception {
        PrivateKey key = SslContext.toPrivateKey(
                new File(getClass().getResource("rsa_pkcs1_unencrypted.key").getFile()), null);
        assertNotNull(key);
    }

    @Test
    public void testPkcs8UnencryptedRsa() throws Exception {
        PrivateKey key = SslContext.toPrivateKey(
                new File(getClass().getResource("rsa_pkcs8_unencrypted.key").getFile()), null);
        assertNotNull(key);
    }

    @Test
    public void testPkcs8AesEncryptedRsa() throws Exception {
        PrivateKey key = SslContext.toPrivateKey(new File(getClass().getResource("rsa_pkcs8_aes_encrypted.key")
                .getFile()), "example");
        assertNotNull(key);
    }

    @Test
    public void testPkcs8Des3EncryptedRsa() throws Exception {
        PrivateKey key = SslContext.toPrivateKey(new File(getClass().getResource("rsa_pkcs8_des3_encrypted.key")
                .getFile()), "example");
        assertNotNull(key);
    }

    @Test
    public void testPkcs8Pbes2()  throws Exception {
        PrivateKey key = SslContext.toPrivateKey(new File(getClass().getResource("rsa_pbes2_enc_pkcs8.key")
                .getFile()), "12345678", false);
        assertNotNull(key);
    }

    @Test
    public void testPkcs1UnencryptedRsaEmptyPassword() throws Exception {
        assertThrows(IOException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                SslContext.toPrivateKey(
                        new File(getClass().getResource("rsa_pkcs1_unencrypted.key").getFile()), "");
            }
        });
    }

    @Test
    public void testPkcs1Des3EncryptedRsa() throws Exception {
        PrivateKey key = SslContext.toPrivateKey(new File(getClass().getResource("rsa_pkcs1_des3_encrypted.key")
                .getFile()), "example");
        assertNotNull(key);
    }

    @Test
    public void testPkcs1AesEncryptedRsa() throws Exception {
        PrivateKey key = SslContext.toPrivateKey(new File(getClass().getResource("rsa_pkcs1_aes_encrypted.key")
                .getFile()), "example");
        assertNotNull(key);
    }

    @Test
    public void testPkcs1Des3EncryptedRsaNoPassword() throws Exception {
        assertThrows(InvalidKeySpecException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                SslContext.toPrivateKey(new File(getClass().getResource("rsa_pkcs1_des3_encrypted.key")
                        .getFile()), null);
            }
        });
    }

    @Test
    public void testPkcs1AesEncryptedRsaNoPassword() throws Exception {
        assertThrows(InvalidKeySpecException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                SslContext.toPrivateKey(new File(getClass().getResource("rsa_pkcs1_aes_encrypted.key")
                        .getFile()), null);
            }
        });
    }

    @Test
    public void testPkcs1Des3EncryptedRsaEmptyPassword() throws Exception {
        assertThrows(IOException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                SslContext.toPrivateKey(new File(getClass().getResource("rsa_pkcs1_des3_encrypted.key")
                        .getFile()), "");
            }
        });
    }

    @Test
    public void testPkcs1AesEncryptedRsaEmptyPassword() throws Exception {
        assertThrows(IOException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                SslContext.toPrivateKey(new File(getClass().getResource("rsa_pkcs1_aes_encrypted.key")
                        .getFile()), "");
            }
        });
    }

    @Test
    public void testPkcs1Des3EncryptedRsaWrongPassword() throws Exception {
        assertThrows(IOException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                SslContext.toPrivateKey(new File(getClass().getResource("rsa_pkcs1_des3_encrypted.key")
                        .getFile()), "wrong");
            }
        });
    }

    @Test
    public void testPkcs1AesEncryptedRsaWrongPassword() throws Exception {
        assertThrows(IOException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                SslContext.toPrivateKey(new File(getClass().getResource("rsa_pkcs1_aes_encrypted.key")
                        .getFile()), "wrong");
            }
        });
    }

    @Test
    public void testPkcs1UnencryptedDsa() throws Exception {
        PrivateKey key = SslContext.toPrivateKey(
                new File(getClass().getResource("dsa_pkcs1_unencrypted.key").getFile()), null);
        assertNotNull(key);
    }

    @Test
    public void testPkcs1UnencryptedDsaEmptyPassword() throws Exception {
        assertThrows(IOException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                PrivateKey key = SslContext.toPrivateKey(
                        new File(getClass().getResource("dsa_pkcs1_unencrypted.key").getFile()), "");
            }
        });
    }

    @Test
    public void testPkcs1Des3EncryptedDsa() throws Exception {
        PrivateKey key = SslContext.toPrivateKey(new File(getClass().getResource("dsa_pkcs1_des3_encrypted.key")
                .getFile()), "example");
        assertNotNull(key);
    }

    @Test
    public void testPkcs1AesEncryptedDsa() throws Exception {
        PrivateKey key = SslContext.toPrivateKey(new File(getClass().getResource("dsa_pkcs1_aes_encrypted.key")
                .getFile()), "example");
        assertNotNull(key);
    }

    @Test
    public void testPkcs1Des3EncryptedDsaNoPassword() throws Exception {
        assertThrows(InvalidKeySpecException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                SslContext.toPrivateKey(new File(getClass().getResource("dsa_pkcs1_des3_encrypted.key")
                        .getFile()), null);
            }
        });
    }

    @Test
    public void testPkcs1AesEncryptedDsaNoPassword() throws Exception {
        assertThrows(InvalidKeySpecException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                SslContext.toPrivateKey(new File(getClass().getResource("dsa_pkcs1_aes_encrypted.key")
                        .getFile()), null);
            }
        });
    }

    @Test
    public void testPkcs1Des3EncryptedDsaEmptyPassword() throws Exception {
        assertThrows(IOException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                SslContext.toPrivateKey(new File(getClass().getResource("dsa_pkcs1_des3_encrypted.key")
                        .getFile()), "");
            }
        });
    }

    @Test
    public void testPkcs1AesEncryptedDsaEmptyPassword() throws Exception {
        assertThrows(IOException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                SslContext.toPrivateKey(new File(getClass().getResource("dsa_pkcs1_aes_encrypted.key")
                        .getFile()), "");
            }
        });
    }

    @Test
    public void testPkcs1Des3EncryptedDsaWrongPassword() throws Exception {
        assertThrows(IOException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                SslContext.toPrivateKey(new File(getClass().getResource("dsa_pkcs1_des3_encrypted.key")
                        .getFile()), "wrong");
            }
        });
    }

    @Test
    public void testPkcs1AesEncryptedDsaWrongPassword() throws Exception {
        assertThrows(IOException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                SslContext.toPrivateKey(new File(getClass().getResource("dsa_pkcs1_aes_encrypted.key")
                        .getFile()), "wrong");
            }
        });
    }
}
