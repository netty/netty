/*
 * Copyright 2014 The Netty Project
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

import io.netty.util.ReferenceCounted;
import io.netty.util.internal.UnstableApi;

import java.security.Provider;

/**
 * An enumeration of SSL/TLS protocol providers.
 */
public enum SslProvider {
    /**
     * JDK's default implementation.
     */
    JDK,
    /**
     * OpenSSL-based implementation.
     */
    OPENSSL,
    /**
     * OpenSSL-based implementation which does not have finalizers and instead implements {@link ReferenceCounted}.
     */
    @UnstableApi
    OPENSSL_REFCNT;

    /**
     * Returns {@code true} if the specified {@link SslProvider} supports
     * <a href="https://tools.ietf.org/html/rfc7301#section-6">TLS ALPN Extension</a>, {@code false} otherwise.
     */
    @SuppressWarnings("deprecation")
    public static boolean isAlpnSupported(final SslProvider provider) {
        switch (provider) {
            case JDK:
                return JdkAlpnApplicationProtocolNegotiator.isAlpnSupported();
            case OPENSSL:
            case OPENSSL_REFCNT:
                return OpenSsl.isAlpnSupported();
            default:
                throw new Error("Unknown SslProvider: " + provider);
        }
    }

    /**
     * Returns {@code true} if the specified {@link SslProvider} supports
     * <a href="https://tools.ietf.org/html/rfc8446">TLS 1.3</a>, {@code false} otherwise.
     */
    public static boolean isTlsv13Supported(final SslProvider sslProvider) {
        return isTlsv13Supported(sslProvider, null);
    }

    /**
     * Returns {@code true} if the specified {@link SslProvider} supports
     * <a href="https://tools.ietf.org/html/rfc8446">TLS 1.3</a>, {@code false} otherwise.
     */
    public static boolean isTlsv13Supported(final SslProvider sslProvider, Provider provider) {
        switch (sslProvider) {
            case JDK:
                return SslUtils.isTLSv13SupportedByJDK(provider);
            case OPENSSL:
            case OPENSSL_REFCNT:
                return OpenSsl.isTlsv13Supported();
            default:
                throw new Error("Unknown SslProvider: " + sslProvider);
        }
    }

    /**
     * Returns {@code true} if the specified {@link SslProvider} supports the specified {@link SslContextOption},
     * {@code false} otherwise.
     */
    public static boolean isOptionSupported(SslProvider sslProvider, SslContextOption<?> option) {
        switch (sslProvider) {
            case JDK:
                // We currently don't support any SslContextOptions when using the JDK implementation
                return false;
            case OPENSSL:
            case OPENSSL_REFCNT:
                return OpenSsl.isOptionSupported(option);
            default:
                throw new Error("Unknown SslProvider: " + sslProvider);
        }
    }

    /**
     * Returns {@code true} if the specified {@link SslProvider} enables
     * <a href="https://tools.ietf.org/html/rfc8446">TLS 1.3</a> by default, {@code false} otherwise.
     */
    static boolean isTlsv13EnabledByDefault(final SslProvider sslProvider, Provider provider) {
        switch (sslProvider) {
            case JDK:
                return SslUtils.isTLSv13EnabledByJDK(provider);
            case OPENSSL:
            case OPENSSL_REFCNT:
                return OpenSsl.isTlsv13Supported();
            default:
                throw new Error("Unknown SslProvider: " + sslProvider);
        }
    }
}
