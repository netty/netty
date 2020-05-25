/*
 * Copyright 2018 The Netty Project
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

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.internal.tcnative.SSL;
import io.netty.util.ReferenceCountUtil;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.X509KeyManager;

import java.net.Socket;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class OpenSslKeyMaterialProviderTest {

    static final String PASSWORD = "example";
    static final String EXISTING_ALIAS = "1";
    private static final String NON_EXISTING_ALIAS = "nonexisting";

    @BeforeClass
    public static void checkOpenSsl() {
        assumeTrue(OpenSsl.isAvailable());
    }

    protected KeyManagerFactory newKeyManagerFactory() throws Exception {
       return newKeyManagerFactory(KeyManagerFactory.getDefaultAlgorithm());
    }

    protected KeyManagerFactory newKeyManagerFactory(String algorithm) throws Exception {
        char[] password = PASSWORD.toCharArray();
        final KeyStore keystore = KeyStore.getInstance("PKCS12");
        keystore.load(getClass().getResourceAsStream("mutual_auth_server.p12"), password);

        KeyManagerFactory kmf =
                KeyManagerFactory.getInstance(algorithm);
        kmf.init(keystore, password);
        return kmf;
    }

    protected OpenSslKeyMaterialProvider newMaterialProvider(KeyManagerFactory factory, String password) {
        return new OpenSslKeyMaterialProvider(ReferenceCountedOpenSslContext.chooseX509KeyManager(
                factory.getKeyManagers()), password);
    }

    protected void assertRelease(OpenSslKeyMaterial material) {
        assertTrue(material.release());
    }

    @Test
    public void testChooseKeyMaterial() throws Exception {
        OpenSslKeyMaterialProvider provider = newMaterialProvider(newKeyManagerFactory(), PASSWORD);
        OpenSslKeyMaterial nonExistingMaterial = provider.chooseKeyMaterial(
                UnpooledByteBufAllocator.DEFAULT, NON_EXISTING_ALIAS);
        assertNull(nonExistingMaterial);

        OpenSslKeyMaterial material = provider.chooseKeyMaterial(UnpooledByteBufAllocator.DEFAULT, EXISTING_ALIAS);
        assertNotNull(material);
        assertNotEquals(0, material.certificateChainAddress());
        assertNotEquals(0, material.privateKeyAddress());
        assertRelease(material);

        provider.destroy();
    }

    /**
     * Test class used by testChooseOpenSslPrivateKeyMaterial().
     */
    private static final class SingleKeyManager implements X509KeyManager {
        private final String keyAlias;
        private final PrivateKey pk;
        private final X509Certificate[] certChain;

        SingleKeyManager(String keyAlias, PrivateKey pk, X509Certificate[] certChain) {
            this.keyAlias = keyAlias;
            this.pk = pk;
            this.certChain = certChain;
        }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return new String[]{keyAlias};
        }

        @Override
        public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
            return keyAlias;
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            return new String[]{keyAlias};
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            return keyAlias;
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return certChain;
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            return pk;
        }
    }

    @Test
    public void testChooseOpenSslPrivateKeyMaterial() throws Exception {
        PrivateKey privateKey = SslContext.toPrivateKey(
                getClass().getResourceAsStream("localhost_server.key"),
                null);
        assertNotNull(privateKey);
        assertEquals("PKCS#8", privateKey.getFormat());
        final X509Certificate[] certChain = SslContext.toX509Certificates(
                getClass().getResourceAsStream("localhost_server.pem"));
        assertNotNull(certChain);
        PemEncoded pemKey = null;
        long pkeyBio = 0L;
        OpenSslPrivateKey sslPrivateKey;
        try {
            pemKey = PemPrivateKey.toPEM(ByteBufAllocator.DEFAULT, true, privateKey);
            pkeyBio = ReferenceCountedOpenSslContext.toBIO(ByteBufAllocator.DEFAULT, pemKey.retain());
            sslPrivateKey = new OpenSslPrivateKey(SSL.parsePrivateKey(pkeyBio, null));
        } finally {
            ReferenceCountUtil.safeRelease(pemKey);
            if (pkeyBio != 0L) {
                SSL.freeBIO(pkeyBio);
            }
        }
        final String keyAlias = "key";

        OpenSslKeyMaterialProvider provider = new OpenSslKeyMaterialProvider(
                new SingleKeyManager(keyAlias, sslPrivateKey, certChain),
                null);
        OpenSslKeyMaterial material = provider.chooseKeyMaterial(ByteBufAllocator.DEFAULT, keyAlias);
        assertNotNull(material);
        assertEquals(2, sslPrivateKey.refCnt());
        assertEquals(1, material.refCnt());
        assertTrue(material.release());
        assertEquals(1, sslPrivateKey.refCnt());
        // Can get material multiple times from the same key
        material = provider.chooseKeyMaterial(ByteBufAllocator.DEFAULT, keyAlias);
        assertNotNull(material);
        assertEquals(2, sslPrivateKey.refCnt());
        assertTrue(material.release());
        assertTrue(sslPrivateKey.release());
        assertEquals(0, sslPrivateKey.refCnt());
        assertEquals(0, material.refCnt());
        assertEquals(0, ((OpenSslPrivateKey.OpenSslPrivateKeyMaterial) material).certificateChain);
    }
}
