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

package io.netty.handler.ssl;

import io.netty.buffer.ByteBufAllocator;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSessionContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * An {@link SslContext} which uses JDK's SSL/TLS implementation.
 */
public abstract class JdkSslContext extends SslContext {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(JdkSslContext.class);

    static final String PROTOCOL = "TLS";
    static final String[] PROTOCOLS;
    static final List<String> DEFAULT_CIPHERS;

    static {
        SSLContext context;
        try {
            context = SSLContext.getInstance(PROTOCOL);
            context.init(null, null, null);
        } catch (Exception e) {
            throw new Error("failed to initialize the default SSL context", e);
        }

        SSLEngine engine = context.createSSLEngine();

        // Choose the sensible default list of protocols.
        String[] supportedProtocols = engine.getSupportedProtocols();
        List<String> protocols = new ArrayList<String>();
        addIfSupported(
                supportedProtocols, protocols,
                "TLSv1.2", "TLSv1.1", "TLSv1", "SSLv3");

        if (!protocols.isEmpty()) {
            PROTOCOLS = protocols.toArray(new String[protocols.size()]);
        } else {
            PROTOCOLS = engine.getEnabledProtocols();
        }

        // Choose the sensible default list of cipher suites.
        String[] supportedCiphers = engine.getSupportedCipherSuites();
        List<String> ciphers = new ArrayList<String>();
        addIfSupported(
                supportedCiphers, ciphers,
                // XXX: Make sure to sync this list with OpenSslEngineFactory.
                // GCM (Galois/Counter Mode) requires JDK 8.
                "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                "TLS_ECDHE_RSA_WITH_RC4_128_SHA",
                "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
                // AES256 requires JCE unlimited strength jurisdiction policy files.
                "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
                // GCM (Galois/Counter Mode) requires JDK 8.
                "TLS_RSA_WITH_AES_128_GCM_SHA256",
                "SSL_RSA_WITH_RC4_128_SHA",
                "SSL_RSA_WITH_RC4_128_MD5",
                "TLS_RSA_WITH_AES_128_CBC_SHA",
                // AES256 requires JCE unlimited strength jurisdiction policy files.
                "TLS_RSA_WITH_AES_256_CBC_SHA",
                "SSL_RSA_WITH_DES_CBC_SHA");

        if (!ciphers.isEmpty()) {
            DEFAULT_CIPHERS = Collections.unmodifiableList(ciphers);
        } else {
            // Use the default from JDK as fallback.
            DEFAULT_CIPHERS = Collections.unmodifiableList(Arrays.asList(engine.getEnabledCipherSuites()));
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Default protocols (JDK): {} ", Arrays.asList(PROTOCOLS));
            logger.debug("Default cipher suites (JDK): {}", DEFAULT_CIPHERS);
        }
    }

    private static void addIfSupported(String[] supported, List<String> enabled, String... names) {
        for (String n: names) {
            for (String s: supported) {
                if (n.equals(s)) {
                    enabled.add(s);
                    break;
                }
            }
        }
    }

    private final String[] cipherSuites;
    private final List<String> unmodifiableCipherSuites;

    JdkSslContext(Iterable<String> ciphers) {
        cipherSuites = toCipherSuiteArray(ciphers);
        unmodifiableCipherSuites = Collections.unmodifiableList(Arrays.asList(cipherSuites));
    }

    /**
     * Returns the JDK {@link SSLContext} object held by this context.
     */
    public abstract SSLContext context();

    /**
     * Returns the JDK {@link SSLSessionContext} object held by this context.
     */
    public final SSLSessionContext sessionContext() {
        if (isServer()) {
            return context().getServerSessionContext();
        } else {
            return context().getClientSessionContext();
        }
    }

    @Override
    public final List<String> cipherSuites() {
        return unmodifiableCipherSuites;
    }

    @Override
    public final long sessionCacheSize() {
        return sessionContext().getSessionCacheSize();
    }

    @Override
    public final long sessionTimeout() {
        return sessionContext().getSessionTimeout();
    }

    @Override
    public final SSLEngine newEngine(ByteBufAllocator alloc) {
        SSLEngine engine = context().createSSLEngine();
        engine.setEnabledCipherSuites(cipherSuites);
        engine.setEnabledProtocols(PROTOCOLS);
        engine.setUseClientMode(isClient());
        return wrapEngine(engine);
    }

    @Override
    public final SSLEngine newEngine(ByteBufAllocator alloc, String peerHost, int peerPort) {
        SSLEngine engine = context().createSSLEngine(peerHost, peerPort);
        engine.setEnabledCipherSuites(cipherSuites);
        engine.setEnabledProtocols(PROTOCOLS);
        engine.setUseClientMode(isClient());
        return wrapEngine(engine);
    }

    private SSLEngine wrapEngine(SSLEngine engine) {
        if (nextProtocols().isEmpty()) {
            return engine;
        } else {
            return new JettyNpnSslEngine(engine, nextProtocols(), isServer());
        }
    }

    private static String[] toCipherSuiteArray(Iterable<String> ciphers) {
        if (ciphers == null) {
            return DEFAULT_CIPHERS.toArray(new String[DEFAULT_CIPHERS.size()]);
        } else {
            List<String> newCiphers = new ArrayList<String>();
            for (String c: ciphers) {
                if (c == null) {
                    break;
                }
                newCiphers.add(c);
            }
            return newCiphers.toArray(new String[newCiphers.size()]);
        }
    }
}
