/*
 * Copyright 2014 The Netty Project
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

package org.jboss.netty.handler.ssl;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSessionContext;
import java.io.File;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class JdkSslServerContext extends JdkSslContext {

    private final SSLContext ctx;
    private final SSLSessionContext sessCtx;

    public JdkSslServerContext(File certChainFile, File keyFile) throws SSLException {
        this(certChainFile, keyFile, null);
    }

    public JdkSslServerContext(File certChainFile, File keyFile, String keyPassword) throws SSLException {
        this(null, certChainFile, keyFile, keyPassword, null, null, 0, 0);
    }

    /**
     * Creates a new factory that creates a new server-side {@link SSLEngine}.
     */
    public JdkSslServerContext(
            SslBufferPool bufPool,
            File certChainFile, File keyFile, String keyPassword,
            Iterable<String> ciphers, Iterable<String> nextProtocols,
            long sessionCacheSize, long sessionTimeout) throws SSLException {

        super(bufPool, ciphers);

        if (certChainFile == null) {
            throw new NullPointerException("certChainFile");
        }
        if (keyFile == null) {
            throw new NullPointerException("keyFile");
        }

        if (keyPassword == null) {
            keyPassword = "";
        }

        if (nextProtocols != null && nextProtocols.iterator().hasNext()) {
            throw new SSLException("NPN/ALPN unsupported: " + nextProtocols);
        }

        String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
        if (algorithm == null) {
            algorithm = "SunX509";
        }

        try {
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(null, null);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            KeyFactory rsaKF = KeyFactory.getInstance("RSA");
            KeyFactory dsaKF = KeyFactory.getInstance("DSA");

            ChannelBuffer encodedKeyBuf = PemReader.readPrivateKey(keyFile);
            byte[] encodedKey = new byte[encodedKeyBuf.readableBytes()];
            encodedKeyBuf.readBytes(encodedKey);
            PKCS8EncodedKeySpec encodedKeySpec = new PKCS8EncodedKeySpec(encodedKey);

            PrivateKey key;
            try {
                key = rsaKF.generatePrivate(encodedKeySpec);
            } catch (InvalidKeySpecException ignore) {
                key = dsaKF.generatePrivate(encodedKeySpec);
            }

            List<Certificate> certChain = new ArrayList<Certificate>();
            for (ChannelBuffer buf: PemReader.readCertificates(certChainFile)) {
                certChain.add(cf.generateCertificate(new ChannelBufferInputStream(buf)));
            }

            ks.setKeyEntry("key", key, keyPassword.toCharArray(), certChain.toArray(new Certificate[certChain.size()]));

            // Set up key manager factory to use our key store
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);
            kmf.init(ks, keyPassword.toCharArray());

            // Initialize the SSLContext to work with our key managers.
            ctx = SSLContext.getInstance(PROTOCOL);
            ctx.init(kmf.getKeyManagers(), null, null);

            sessCtx = ctx.getServerSessionContext();
            if (sessionCacheSize > 0) {
                sessCtx.setSessionCacheSize((int) Math.min(sessionCacheSize, Integer.MAX_VALUE));
            }
            if (sessionTimeout > 0) {
                sessCtx.setSessionTimeout((int) Math.min(sessionTimeout, Integer.MAX_VALUE));
            }
        } catch (Exception e) {
            throw new SSLException("failed to initialize the server-side SSL context", e);
        }
    }

    @Override
    public boolean isClient() {
        return false;
    }

    @Override
    public ApplicationProtocolSelector nextProtocolSelector() {
        return null;
    }

    @Override
    public List<String> nextProtocols() {
        return Collections.emptyList();
    }

    /**
     * Returns the {@link SSLContext} object of this factory.
     */
    @Override
    public SSLContext context() {
        return ctx;
    }
}
