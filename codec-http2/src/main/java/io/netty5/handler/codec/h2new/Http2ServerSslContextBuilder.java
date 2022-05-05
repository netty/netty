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

package io.netty5.handler.codec.h2new;

import io.netty5.channel.Channel;
import io.netty5.channel.ChannelInitializer;
import io.netty5.handler.ssl.ApplicationProtocolConfig;
import io.netty5.handler.ssl.CipherSuiteFilter;
import io.netty5.handler.ssl.ClientAuth;
import io.netty5.handler.ssl.SslContext;
import io.netty5.handler.ssl.SslContextBuilder;
import io.netty5.handler.ssl.SslContextOption;
import io.netty5.handler.ssl.SslProvider;
import io.netty5.handler.ssl.SupportedCipherSuiteFilter;

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

import static io.netty5.handler.codec.http2.Http2SecurityUtil.CIPHERS;
import static io.netty5.handler.ssl.ApplicationProtocolConfig.Protocol.ALPN;
import static io.netty5.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT;
import static io.netty5.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE;
import static io.netty5.handler.ssl.ApplicationProtocolNames.HTTP_1_1;
import static io.netty5.handler.ssl.ApplicationProtocolNames.HTTP_2;
import static io.netty5.handler.ssl.SslContextBuilder.forServer;
import static io.netty5.util.internal.ObjectUtil.checkNotNullWithIAE;

public final class Http2ServerSslContextBuilder {
    private final SslContextBuilder delegate;
    private ChannelInitializer<Channel> http1xPipelineInitializer;

    // TODO: Add other constructor variants.
    public Http2ServerSslContextBuilder(File keyCertChainFile, File keyFile) {
        delegate = forServer(keyCertChainFile, keyFile)
                .ciphers(CIPHERS, SupportedCipherSuiteFilter.INSTANCE);
    }

    public Http2ServerSslContextBuilder supportHttp1x(ChannelInitializer<Channel> http1xPipelineInitializer) {
        this.http1xPipelineInitializer = checkNotNullWithIAE(http1xPipelineInitializer, "http1xPipelineInitializer");
        return this;
    }

    public <T> Http2ServerSslContextBuilder option(SslContextOption<T> option, T value) {
        delegate.option(option, value);
        return this;
    }

    public Http2ServerSslContextBuilder sslProvider(SslProvider provider) {
        delegate.sslProvider(provider);
        return this;
    }

    public Http2ServerSslContextBuilder keyStoreType(String keyStoreType) {
        delegate.keyStoreType(keyStoreType);
        return this;
    }

    public Http2ServerSslContextBuilder sslContextProvider(Provider sslContextProvider) {
        delegate.sslContextProvider(sslContextProvider);
        return this;
    }

    public Http2ServerSslContextBuilder trustManager(File trustCertCollectionFile) {
        delegate.trustManager(trustCertCollectionFile);
        return this;
    }

    public Http2ServerSslContextBuilder trustManager(InputStream trustCertCollectionInputStream) {
        delegate.trustManager(trustCertCollectionInputStream);
        return this;
    }

    public Http2ServerSslContextBuilder trustManager(X509Certificate... trustCertCollection) {
        delegate.trustManager(trustCertCollection);
        return this;
    }

    public Http2ServerSslContextBuilder trustManager(Iterable<? extends X509Certificate> trustCertCollection) {
        delegate.trustManager(trustCertCollection);
        return this;
    }

    public Http2ServerSslContextBuilder trustManager(TrustManagerFactory trustManagerFactory) {
        delegate.trustManager(trustManagerFactory);
        return this;
    }

    public Http2ServerSslContextBuilder trustManager(TrustManager trustManager) {
        delegate.trustManager(trustManager);
        return this;
    }

    public Http2ServerSslContextBuilder keyManager(File keyCertChainFile, File keyFile) {
        delegate.keyManager(keyCertChainFile, keyFile);
        return this;
    }

    public Http2ServerSslContextBuilder keyManager(InputStream keyCertChainInputStream, InputStream keyInputStream) {
        delegate.keyManager(keyCertChainInputStream, keyInputStream);
        return this;
    }

    public Http2ServerSslContextBuilder keyManager(PrivateKey key, X509Certificate... keyCertChain) {
        delegate.keyManager(key, keyCertChain);
        return this;
    }

    public Http2ServerSslContextBuilder keyManager(PrivateKey key, Iterable<? extends X509Certificate> keyCertChain) {
        delegate.keyManager(key, keyCertChain);
        return this;
    }

    public Http2ServerSslContextBuilder keyManager(File keyCertChainFile, File keyFile, String keyPassword) {
        delegate.keyManager(keyCertChainFile, keyFile, keyPassword);
        return this;
    }

    public Http2ServerSslContextBuilder keyManager(InputStream keyCertChainInputStream, InputStream keyInputStream,
                                                   String keyPassword) {
        delegate.keyManager(keyCertChainInputStream, keyInputStream, keyPassword);
        return this;
    }

    public Http2ServerSslContextBuilder keyManager(PrivateKey key, String keyPassword,
                                                   X509Certificate... keyCertChain) {
        delegate.keyManager(key, keyPassword, keyCertChain);
        return this;
    }

    public Http2ServerSslContextBuilder keyManager(PrivateKey key, String keyPassword,
                                                   Iterable<? extends X509Certificate> keyCertChain) {
        delegate.keyManager(key, keyPassword, keyCertChain);
        return this;
    }

    public Http2ServerSslContextBuilder keyManager(KeyManagerFactory keyManagerFactory) {
        delegate.keyManager(keyManagerFactory);
        return this;
    }

    public Http2ServerSslContextBuilder keyManager(KeyManager keyManager) {
        delegate.keyManager(keyManager);
        return this;
    }

    public Http2ServerSslContextBuilder ciphers(Iterable<String> ciphers) {
        delegate.ciphers(ciphers);
        return this;
    }

    public Http2ServerSslContextBuilder ciphers(Iterable<String> ciphers, CipherSuiteFilter cipherFilter) {
        delegate.ciphers(ciphers, cipherFilter);
        return this;
    }

    public Http2ServerSslContextBuilder sessionCacheSize(long sessionCacheSize) {
        delegate.sessionCacheSize(sessionCacheSize);
        return this;
    }

    public Http2ServerSslContextBuilder sessionTimeout(long sessionTimeout) {
        delegate.sessionTimeout(sessionTimeout);
        return this;
    }

    public Http2ServerSslContextBuilder clientAuth(ClientAuth clientAuth) {
        delegate.clientAuth(clientAuth);
        return this;
    }

    public Http2ServerSslContextBuilder protocols(Iterable<String> protocols) {
        delegate.protocols(protocols);
        return this;
    }

    public Http2ServerSslContextBuilder startTls(boolean startTls) {
        delegate.startTls(startTls);
        return this;
    }

    @io.netty.util.internal.UnstableApi
    public Http2ServerSslContextBuilder enableOcsp(boolean enableOcsp) {
        delegate.enableOcsp(enableOcsp);
        return this;
    }

    public Http2ServerSslContext build() throws SSLException {
        if (http1xPipelineInitializer != null) {
            delegate.applicationProtocolConfig(applicationProtocolConfig(HTTP_2, HTTP_1_1));
        } else {
            delegate.applicationProtocolConfig(applicationProtocolConfig(HTTP_2));
        }
        final SslContext sslContext = delegate.build();
        return new Http2ServerSslContext(sslContext, http1xPipelineInitializer);
    }

    private static ApplicationProtocolConfig applicationProtocolConfig(String... protocolNames) {
        return new ApplicationProtocolConfig(
                ALPN,
                // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
                NO_ADVERTISE,
                // ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
                ACCEPT,
                protocolNames);
    }
}
