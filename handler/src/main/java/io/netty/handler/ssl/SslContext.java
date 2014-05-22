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
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.util.List;

/**
 * A secure socket protocol implementation which acts as a factory for {@link SSLEngine} and {@link SslHandler}.
 * Internally, it is implemented via JDK's {@link SSLContext} or OpenSSL's {@code SSL_CTX}.
 *
 * <h3>Making your server support SSL/TLS</h3>
 * <pre>
 * // In your {@link ChannelInitializer}:
 * {@link ChannelPipeline} p = channel.pipeline();
 * {@link SslContext} sslCtx = {@link #newServerContext(File, File) SslContext.newServerContext(...)};
 * p.addLast("ssl", {@link #newEngine(ByteBufAllocator) sslCtx.newEngine(channel.alloc())});
 * ...
 * </pre>
 *
 * <h3>Making your client support SSL/TLS</h3>
 * <pre>
 * // In your {@link ChannelInitializer}:
 * {@link ChannelPipeline} p = channel.pipeline();
 * {@link SslContext} sslCtx = {@link #newClientContext(File) SslContext.newClientContext(...)};
 * p.addLast("ssl", {@link #newEngine(ByteBufAllocator, String, int) sslCtx.newEngine(channel.alloc(), host, port)});
 * ...
 * </pre>
 */
public abstract class SslContext {

    /**
     * Returns the default server-side implementation provider currently in use.
     *
     * @return {@link SslProvider#OPENSSL} if OpenSSL is available. {@link SslProvider#JDK} otherwise.
     */
    public static SslProvider defaultServerProvider() {
        if (OpenSsl.isAvailable()) {
            return SslProvider.OPENSSL;
        } else {
            return SslProvider.JDK;
        }
    }

    /**
     * Returns the default client-side implementation provider currently in use.
     *
     * @return {@link SslProvider#JDK}, because it is the only implementation at the moment
     */
    public static SslProvider defaultClientProvider() {
        return SslProvider.JDK;
    }

    /**
     * Creates a new server-side {@link SslContext}.
     *
     * @param certChainFile an X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     * @return a new server-side {@link SslContext}
     */
    public static SslContext newServerContext(File certChainFile, File keyFile) throws SSLException {
        return newServerContext(null, certChainFile, keyFile, null, null, null, 0, 0);
    }

    /**
     * Creates a new server-side {@link SslContext}.
     *
     * @param certChainFile an X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     * @param keyPassword the password of the {@code keyFile}.
     *                    {@code null} if it's not password-protected.
     * @return a new server-side {@link SslContext}
     */
    public static SslContext newServerContext(
            File certChainFile, File keyFile, String keyPassword) throws SSLException {
        return newServerContext(null, certChainFile, keyFile, keyPassword, null, null, 0, 0);
    }

    /**
     * Creates a new server-side {@link SslContext}.
     *
     * @param certChainFile an X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     * @param keyPassword the password of the {@code keyFile}.
     *                    {@code null} if it's not password-protected.
     * @param ciphers the cipher suites to enable, in the order of preference.
     *                {@code null} to use the default cipher suites.
     * @param nextProtocols the application layer protocols to accept, in the order of preference.
     *                      {@code null} to disable TLS NPN/ALPN extension.
     * @param sessionCacheSize the size of the cache used for storing SSL session objects.
     *                         {@code 0} to use the default value.
     * @param sessionTimeout the timeout for the cached SSL session objects, in seconds.
     *                       {@code 0} to use the default value.
     * @return a new server-side {@link SslContext}
     */
    public static SslContext newServerContext(
            File certChainFile, File keyFile, String keyPassword,
            Iterable<String> ciphers, Iterable<String> nextProtocols,
            long sessionCacheSize, long sessionTimeout) throws SSLException {
        return newServerContext(
                null, certChainFile, keyFile, keyPassword,
                ciphers, nextProtocols, sessionCacheSize, sessionTimeout);
    }

    /**
     * Creates a new server-side {@link SslContext}.
     *
     * @param provider the {@link SslContext} implementation to use.
     *                 {@code null} to use the current default one.
     * @param certChainFile an X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     * @return a new server-side {@link SslContext}
     */
    public static SslContext newServerContext(
            SslProvider provider, File certChainFile, File keyFile) throws SSLException {
        return newServerContext(provider, certChainFile, keyFile, null, null, null, 0, 0);
    }

    /**
     * Creates a new server-side {@link SslContext}.
     *
     * @param provider the {@link SslContext} implementation to use.
     *                 {@code null} to use the current default one.
     * @param certChainFile an X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     * @param keyPassword the password of the {@code keyFile}.
     *                    {@code null} if it's not password-protected.
     * @return a new server-side {@link SslContext}
     */
    public static SslContext newServerContext(
            SslProvider provider, File certChainFile, File keyFile, String keyPassword) throws SSLException {
        return newServerContext(provider, certChainFile, keyFile, keyPassword, null, null, 0, 0);
    }

    /**
     * Creates a new server-side {@link SslContext}.
     *
     * @param provider the {@link SslContext} implementation to use.
     *                 {@code null} to use the current default one.
     * @param certChainFile an X.509 certificate chain file in PEM format
     * @param keyFile a PKCS#8 private key file in PEM format
     * @param keyPassword the password of the {@code keyFile}.
     *                    {@code null} if it's not password-protected.
     * @param ciphers the cipher suites to enable, in the order of preference.
     *                {@code null} to use the default cipher suites.
     * @param nextProtocols the application layer protocols to accept, in the order of preference.
     *                      {@code null} to disable TLS NPN/ALPN extension.
     * @param sessionCacheSize the size of the cache used for storing SSL session objects.
     *                         {@code 0} to use the default value.
     * @param sessionTimeout the timeout for the cached SSL session objects, in seconds.
     *                       {@code 0} to use the default value.
     * @return a new server-side {@link SslContext}
     */
    public static SslContext newServerContext(
            SslProvider provider,
            File certChainFile, File keyFile, String keyPassword,
            Iterable<String> ciphers, Iterable<String> nextProtocols,
            long sessionCacheSize, long sessionTimeout) throws SSLException {

        if (provider == null) {
            provider = OpenSsl.isAvailable()? SslProvider.OPENSSL : SslProvider.JDK;
        }

        switch (provider) {
            case JDK:
                return new JdkSslServerContext(
                        certChainFile, keyFile, keyPassword,
                        ciphers, nextProtocols, sessionCacheSize, sessionTimeout);
            case OPENSSL:
                return new OpenSslServerContext(
                        certChainFile, keyFile, keyPassword,
                        ciphers, nextProtocols, sessionCacheSize, sessionTimeout);
            default:
                throw new Error(provider.toString());
        }
    }

    /**
     * Creates a new client-side {@link SslContext}.
     *
     * @return a new client-side {@link SslContext}
     */
    public static SslContext newClientContext() throws SSLException {
        return newClientContext(null, null, null, null, null, 0, 0);
    }

    /**
     * Creates a new client-side {@link SslContext}.
     *
     * @param certChainFile an X.509 certificate chain file in PEM format
     *
     * @return a new client-side {@link SslContext}
     */
    public static SslContext newClientContext(File certChainFile) throws SSLException {
        return newClientContext(null, certChainFile, null, null, null, 0, 0);
    }

    /**
     * Creates a new client-side {@link SslContext}.
     *
     * @param trustManagerFactory the {@link TrustManagerFactory} that provides the {@link TrustManager}s
     *                            that verifies the certificates sent from servers.
     *                            {@code null} to use the default.
     *
     * @return a new client-side {@link SslContext}
     */
    public static SslContext newClientContext(TrustManagerFactory trustManagerFactory) throws SSLException {
        return newClientContext(null, null, trustManagerFactory, null, null, 0, 0);
    }

    /**
     * Creates a new client-side {@link SslContext}.
     *
     * @param certChainFile an X.509 certificate chain file in PEM format.
     *                      {@code null} to use the system default
     * @param trustManagerFactory the {@link TrustManagerFactory} that provides the {@link TrustManager}s
     *                            that verifies the certificates sent from servers.
     *                            {@code null} to use the default.
     *
     * @return a new client-side {@link SslContext}
     */
    public static SslContext newClientContext(
            File certChainFile, TrustManagerFactory trustManagerFactory) throws SSLException {
        return newClientContext(null, certChainFile, trustManagerFactory, null, null, 0, 0);
    }

    /**
     * Creates a new client-side {@link SslContext}.
     *
     * @param certChainFile an X.509 certificate chain file in PEM format.
     *                      {@code null} to use the system default
     * @param trustManagerFactory the {@link TrustManagerFactory} that provides the {@link TrustManager}s
     *                            that verifies the certificates sent from servers.
     *                            {@code null} to use the default.
     * @param ciphers the cipher suites to enable, in the order of preference.
     *                {@code null} to use the default cipher suites.
     * @param nextProtocols the application layer protocols to accept, in the order of preference.
     *                      {@code null} to disable TLS NPN/ALPN extension.
     * @param sessionCacheSize the size of the cache used for storing SSL session objects.
     *                         {@code 0} to use the default value.
     * @param sessionTimeout the timeout for the cached SSL session objects, in seconds.
     *                       {@code 0} to use the default value.
     *
     * @return a new client-side {@link SslContext}
     */
    public static SslContext newClientContext(
            File certChainFile, TrustManagerFactory trustManagerFactory,
            Iterable<String> ciphers, Iterable<String> nextProtocols,
            long sessionCacheSize, long sessionTimeout) throws SSLException {
        return newClientContext(
                null, certChainFile, trustManagerFactory,
                ciphers, nextProtocols, sessionCacheSize, sessionTimeout);
    }

    /**
     * Creates a new client-side {@link SslContext}.
     *
     * @param provider the {@link SslContext} implementation to use.
     *                 {@code null} to use the current default one.
     *
     * @return a new client-side {@link SslContext}
     */
    public static SslContext newClientContext(SslProvider provider) throws SSLException {
        return newClientContext(provider, null, null, null, null, 0, 0);
    }

    /**
     * Creates a new client-side {@link SslContext}.
     *
     * @param provider the {@link SslContext} implementation to use.
     *                 {@code null} to use the current default one.
     * @param certChainFile an X.509 certificate chain file in PEM format.
     *                      {@code null} to use the system default
     *
     * @return a new client-side {@link SslContext}
     */
    public static SslContext newClientContext(SslProvider provider, File certChainFile) throws SSLException {
        return newClientContext(provider, certChainFile, null, null, null, 0, 0);
    }

    /**
     * Creates a new client-side {@link SslContext}.
     *
     * @param provider the {@link SslContext} implementation to use.
     *                 {@code null} to use the current default one.
     * @param trustManagerFactory the {@link TrustManagerFactory} that provides the {@link TrustManager}s
     *                            that verifies the certificates sent from servers.
     *                            {@code null} to use the default.
     *
     * @return a new client-side {@link SslContext}
     */
    public static SslContext newClientContext(
            SslProvider provider, TrustManagerFactory trustManagerFactory) throws SSLException {
        return newClientContext(provider, null, trustManagerFactory, null, null, 0, 0);
    }

    /**
     * Creates a new client-side {@link SslContext}.
     *
     * @param provider the {@link SslContext} implementation to use.
     *                 {@code null} to use the current default one.
     * @param certChainFile an X.509 certificate chain file in PEM format.
     *                      {@code null} to use the system default
     * @param trustManagerFactory the {@link TrustManagerFactory} that provides the {@link TrustManager}s
     *                            that verifies the certificates sent from servers.
     *                            {@code null} to use the default.
     *
     * @return a new client-side {@link SslContext}
     */
    public static SslContext newClientContext(
            SslProvider provider, File certChainFile, TrustManagerFactory trustManagerFactory) throws SSLException {
        return newClientContext(provider, certChainFile, trustManagerFactory, null, null, 0, 0);
    }

    /**
     * Creates a new client-side {@link SslContext}.
     *
     * @param provider the {@link SslContext} implementation to use.
     *                 {@code null} to use the current default one.
     * @param certChainFile an X.509 certificate chain file in PEM format.
     *                      {@code null} to use the system default
     * @param trustManagerFactory the {@link TrustManagerFactory} that provides the {@link TrustManager}s
     *                            that verifies the certificates sent from servers.
     *                            {@code null} to use the default.
     * @param ciphers the cipher suites to enable, in the order of preference.
     *                {@code null} to use the default cipher suites.
     * @param nextProtocols the application layer protocols to accept, in the order of preference.
     *                      {@code null} to disable TLS NPN/ALPN extension.
     * @param sessionCacheSize the size of the cache used for storing SSL session objects.
     *                         {@code 0} to use the default value.
     * @param sessionTimeout the timeout for the cached SSL session objects, in seconds.
     *                       {@code 0} to use the default value.
     *
     * @return a new client-side {@link SslContext}
     */
    public static SslContext newClientContext(
            SslProvider provider,
            File certChainFile, TrustManagerFactory trustManagerFactory,
            Iterable<String> ciphers, Iterable<String> nextProtocols,
            long sessionCacheSize, long sessionTimeout) throws SSLException {

        if (provider != null && provider != SslProvider.JDK) {
            throw new SSLException("client context unsupported for: " + provider);
        }

        return new JdkSslClientContext(
                certChainFile, trustManagerFactory,
                ciphers, nextProtocols, sessionCacheSize, sessionTimeout);
    }

    SslContext() { }

    /**
     * Returns {@code true} if and only if this context is for server-side.
     */
    public final boolean isServer() {
        return !isClient();
    }

    /**
     * Returns the {@code true} if and only if this context is for client-side.
     */
    public abstract boolean isClient();

    /**
     * Returns the list of enabled cipher suites, in the order of preference.
     */
    public abstract List<String> cipherSuites();

    /**
     * Returns the size of the cache used for storing SSL session objects.
     */
    public abstract long sessionCacheSize();

    /**
     * Returns the timeout for the cached SSL session objects, in seconds.
     */
    public abstract long sessionTimeout();

    /**
     * Returns the list of application layer protocols for the TLS NPN/ALPN extension, in the order of preference.
     *
     * @return the list of application layer protocols.
     *         {@code null} if NPN/ALPN extension has been disabled.
     */
    public abstract List<String> nextProtocols();

    /**
     * Creates a new {@link SSLEngine}.
     *
     * @return a new {@link SSLEngine}
     */
    public abstract SSLEngine newEngine(ByteBufAllocator alloc);

    /**
     * Creates a new {@link SSLEngine} using advisory peer information.
     *
     * @param peerHost the non-authoritative name of the host
     * @param peerPort the non-authoritative port
     *
     * @return a new {@link SSLEngine}
     */
    public abstract SSLEngine newEngine(ByteBufAllocator alloc, String peerHost, int peerPort);

    /**
     * Creates a new {@link SslHandler}.
     *
     * @return a new {@link SslHandler}
     */
    public final SslHandler newHandler(ByteBufAllocator alloc) {
        return newHandler(newEngine(alloc));
    }

    /**
     * Creates a new {@link SslHandler} with advisory peer information.
     *
     * @param peerHost the non-authoritative name of the host
     * @param peerPort the non-authoritative port
     *
     * @return a new {@link SslHandler}
     */
    public final SslHandler newHandler(ByteBufAllocator alloc, String peerHost, int peerPort) {
        return newHandler(newEngine(alloc, peerHost, peerPort));
    }

    private static SslHandler newHandler(SSLEngine engine) {
        return new SslHandler(engine);
    }
}
