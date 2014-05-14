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

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.util.List;

/**
 * Creates a new {@link SSLEngine}. Using its factory methods, you can let it choose the optimal {@link SSLEngine}
 * implementation available to you (the default JDK {@link SSLEngine} or the one that uses OpenSSL native library).
 */
public abstract class SslContext {

    public static SslProvider defaultServerProvider() {
        if (OpenSsl.isAvailable()) {
            return SslProvider.OPENSSL;
        } else {
            return SslProvider.JDK;
        }
    }

    public static SslProvider defaultClientProvider() {
        return SslProvider.JDK;
    }

    public static SslContext newServerContext(File certChainFile, File keyFile) throws SSLException {
        return newServerContext(null, null, certChainFile, keyFile, null, null, null, 0, 0);
    }

    public static SslContext newServerContext(
            File certChainFile, File keyFile, String keyPassword) throws SSLException {
        return newServerContext(null, null, certChainFile, keyFile, keyPassword, null, null, 0, 0);
    }

    public static SslContext newServerContext(
            SslBufferPool bufPool,
            File certChainFile, File keyFile, String keyPassword,
            Iterable<String> ciphers, Iterable<String> nextProtocols,
            long sessionCacheSize, long sessionTimeout) throws SSLException {
        return newServerContext(
                null, bufPool, certChainFile, keyFile, keyPassword,
                ciphers, nextProtocols, sessionCacheSize, sessionTimeout);
    }

    public static SslContext newServerContext(
            SslProvider provider, File certChainFile, File keyFile) throws SSLException {
        return newServerContext(provider, null, certChainFile, keyFile, null, null, null, 0, 0);
    }

    public static SslContext newServerContext(
            SslProvider provider, File certChainFile, File keyFile, String keyPassword) throws SSLException {
        return newServerContext(provider, null, certChainFile, keyFile, keyPassword, null, null, 0, 0);
    }

    public static SslContext newServerContext(
            SslProvider provider, SslBufferPool bufPool,
            File certChainFile, File keyFile, String keyPassword,
            Iterable<String> ciphers, Iterable<String> nextProtocols,
            long sessionCacheSize, long sessionTimeout) throws SSLException {

        if (provider == null) {
            provider = OpenSsl.isAvailable()? SslProvider.OPENSSL : SslProvider.JDK;
        }

        switch (provider) {
            case JDK:
                return new JdkSslServerContext(
                        bufPool, certChainFile, keyFile, keyPassword,
                        ciphers, nextProtocols, sessionCacheSize, sessionTimeout);
            case OPENSSL:
                return new OpenSslServerContext(
                        bufPool, certChainFile, keyFile, keyPassword,
                        ciphers, nextProtocols, sessionCacheSize, sessionTimeout);
            default:
                throw new Error(provider.toString());
        }
    }

    public static SslContext newClientContext() throws SSLException {
        return newClientContext(null, null, null, null, null, null, 0, 0);
    }

    public static SslContext newClientContext(File certChainFile) throws SSLException {
        return newClientContext(null, null, certChainFile, null, null, null, 0, 0);
    }

    public static SslContext newClientContext(TrustManagerFactory trustManagerFactory) throws SSLException {
        return newClientContext(null, null, null, trustManagerFactory, null, null, 0, 0);
    }

    public static SslContext newClientContext(
            File certChainFile, TrustManagerFactory trustManagerFactory) throws SSLException {
        return newClientContext(null, null, certChainFile, trustManagerFactory, null, null, 0, 0);
    }

    public static SslContext newClientContext(
            SslBufferPool bufPool,
            File certChainFile, TrustManagerFactory trustManagerFactory,
            Iterable<String> ciphers, ApplicationProtocolSelector nextProtocolSelector,
            long sessionCacheSize, long sessionTimeout) throws SSLException {
        return newClientContext(
                null, bufPool, certChainFile, trustManagerFactory,
                ciphers, nextProtocolSelector, sessionCacheSize, sessionTimeout);
    }

    public static SslContext newClientContext(SslProvider provider) throws SSLException {
        return newClientContext(provider, null, null, null, null, null, 0, 0);
    }

    public static SslContext newClientContext(SslProvider provider, File certChainFile) throws SSLException {
        return newClientContext(provider, null, certChainFile, null, null, null, 0, 0);
    }

    public static SslContext newClientContext(
            SslProvider provider, TrustManagerFactory trustManagerFactory) throws SSLException {
        return newClientContext(provider, null, null, trustManagerFactory, null, null, 0, 0);
    }

    public static SslContext newClientContext(
            SslProvider provider, File certChainFile, TrustManagerFactory trustManagerFactory) throws SSLException {
        return newClientContext(provider, null, certChainFile, trustManagerFactory, null, null, 0, 0);
    }

    public static SslContext newClientContext(
            SslProvider provider, SslBufferPool bufPool,
            File certChainFile, TrustManagerFactory trustManagerFactory,
            Iterable<String> ciphers, ApplicationProtocolSelector nextProtocolSelector,
            long sessionCacheSize, long sessionTimeout) throws SSLException {

        if (provider != null && provider != SslProvider.JDK) {
            throw new SSLException("client context unsupported for: " + provider);
        }

        return new JdkSslClientContext(
                bufPool, certChainFile, trustManagerFactory,
                ciphers, nextProtocolSelector, sessionCacheSize, sessionTimeout);
    }

    private final SslBufferPool bufferPool;

    SslContext(SslBufferPool bufferPool) {
        this.bufferPool = bufferPool == null? newBufferPool() : bufferPool;
    }

    SslBufferPool newBufferPool() {
        return new SslBufferPool(false, false);
    }

    public final boolean isServer() {
        return !isClient();
    }

    public final SslBufferPool bufferPool() {
        return bufferPool;
    }

    public abstract boolean isClient();

    public abstract List<String> cipherSuites();

    public abstract long sessionCacheSize();

    public abstract long sessionTimeout();

    public abstract ApplicationProtocolSelector nextProtocolSelector();

    public abstract List<String> nextProtocols();

    public abstract SSLEngine newEngine();

    public abstract SSLEngine newEngine(String host, int port);

    public final SslHandler newHandler() {
        return newHandler(newEngine());
    }

    public final SslHandler newHandler(String host, int port) {
        return newHandler(newEngine(host, port));
    }

    private SslHandler newHandler(SSLEngine engine) {
        SslHandler handler = new SslHandler(engine, bufferPool());
        if (isClient()) {
            handler.setIssueHandshake(true);
        }
        handler.setCloseOnSSLException(true);
        return handler;
    }
}
