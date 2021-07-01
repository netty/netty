/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.h2new;

import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.CipherSuiteFilter;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslContextOption;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.InputStream;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.cert.X509Certificate;

import static io.netty.handler.codec.http2.Http2SecurityUtil.CIPHERS;
import static io.netty.handler.ssl.ApplicationProtocolConfig.Protocol.ALPN;
import static io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT;
import static io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE;
import static io.netty.handler.ssl.ApplicationProtocolNames.HTTP_2;

public final class Http2ClientSslContextBuilder {
    private final SslContextBuilder delegate;

    // TODO: Add other constructor variants.
    public Http2ClientSslContextBuilder() {
        delegate = SslContextBuilder.forClient()
                .applicationProtocolConfig(new ApplicationProtocolConfig(
                        ALPN,
                        // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
                        NO_ADVERTISE,
                        // ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
                        ACCEPT,
                        HTTP_2))
                .ciphers(CIPHERS, SupportedCipherSuiteFilter.INSTANCE);
    }

    public <T> Http2ClientSslContextBuilder option(SslContextOption<T> option, T value) {
        delegate.option(option, value);
        return this;
    }

    public Http2ClientSslContextBuilder sslProvider(SslProvider provider) {
        delegate.sslProvider(provider);
        return this;
    }

    public Http2ClientSslContextBuilder keyStoreType(String keyStoreType) {
        delegate.keyStoreType(keyStoreType);
        return this;
    }

    public Http2ClientSslContextBuilder sslContextProvider(Provider sslContextProvider) {
        delegate.sslContextProvider(sslContextProvider);
        return this;
    }

    public Http2ClientSslContextBuilder trustManager(File trustCertCollectionFile) {
        delegate.trustManager(trustCertCollectionFile);
        return this;
    }

    public Http2ClientSslContextBuilder trustManager(InputStream trustCertCollectionInputStream) {
        delegate.trustManager(trustCertCollectionInputStream);
        return this;
    }

    public Http2ClientSslContextBuilder trustManager(X509Certificate... trustCertCollection) {
        delegate.trustManager(trustCertCollection);
        return this;
    }

    public Http2ClientSslContextBuilder trustManager(Iterable<? extends X509Certificate> trustCertCollection) {
        delegate.trustManager(trustCertCollection);
        return this;
    }

    public Http2ClientSslContextBuilder trustManager(TrustManagerFactory trustManagerFactory) {
        delegate.trustManager(trustManagerFactory);
        return this;
    }

    public Http2ClientSslContextBuilder trustManager(TrustManager trustManager) {
        delegate.trustManager(trustManager);
        return this;
    }

    public Http2ClientSslContextBuilder keyManager(File keyCertChainFile, File keyFile) {
        delegate.keyManager(keyCertChainFile, keyFile);
        return this;
    }

    public Http2ClientSslContextBuilder keyManager(InputStream keyCertChainInputStream, InputStream keyInputStream) {
        delegate.keyManager(keyCertChainInputStream, keyInputStream);
        return this;
    }

    public Http2ClientSslContextBuilder keyManager(PrivateKey key, X509Certificate... keyCertChain) {
        delegate.keyManager(key, keyCertChain);
        return this;
    }

    public Http2ClientSslContextBuilder keyManager(PrivateKey key, Iterable<? extends X509Certificate> keyCertChain) {
        delegate.keyManager(key, keyCertChain);
        return this;
    }

    public Http2ClientSslContextBuilder keyManager(File keyCertChainFile, File keyFile, String keyPassword) {
        delegate.keyManager(keyCertChainFile, keyFile, keyPassword);
        return this;
    }

    public Http2ClientSslContextBuilder keyManager(InputStream keyCertChainInputStream, InputStream keyInputStream,
                                                   String keyPassword) {
        delegate.keyManager(keyCertChainInputStream, keyInputStream, keyPassword);
        return this;
    }

    public Http2ClientSslContextBuilder keyManager(PrivateKey key, String keyPassword,
                                                   X509Certificate... keyCertChain) {
        delegate.keyManager(key, keyPassword, keyCertChain);
        return this;
    }

    public Http2ClientSslContextBuilder keyManager(PrivateKey key, String keyPassword,
                                                   Iterable<? extends X509Certificate> keyCertChain) {
        delegate.keyManager(key, keyPassword, keyCertChain);
        return this;
    }

    public Http2ClientSslContextBuilder keyManager(KeyManagerFactory keyManagerFactory) {
        delegate.keyManager(keyManagerFactory);
        return this;
    }

    public Http2ClientSslContextBuilder keyManager(KeyManager keyManager) {
        delegate.keyManager(keyManager);
        return this;
    }

    public Http2ClientSslContextBuilder ciphers(Iterable<String> ciphers) {
        delegate.ciphers(ciphers);
        return this;
    }

    public Http2ClientSslContextBuilder ciphers(Iterable<String> ciphers, CipherSuiteFilter cipherFilter) {
        delegate.ciphers(ciphers, cipherFilter);
        return this;
    }

    public Http2ClientSslContextBuilder sessionCacheSize(long sessionCacheSize) {
        delegate.sessionCacheSize(sessionCacheSize);
        return this;
    }

    public Http2ClientSslContextBuilder sessionTimeout(long sessionTimeout) {
        delegate.sessionTimeout(sessionTimeout);
        return this;
    }

    public Http2ClientSslContextBuilder protocols(Iterable<String> protocols) {
        delegate.protocols(protocols);
        return this;
    }

    public Http2ClientSslContextBuilder startTls(boolean startTls) {
        delegate.startTls(startTls);
        return this;
    }

    @io.netty.util.internal.UnstableApi
    public Http2ClientSslContextBuilder enableOcsp(boolean enableOcsp) {
        delegate.enableOcsp(enableOcsp);
        return this;
    }
    public Http2ClientSslContext build() throws SSLException {
        return new Http2ClientSslContext(delegate.build());
    }
}
