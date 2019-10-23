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

import io.netty.util.ReferenceCounted;
import io.netty.util.internal.UnstableApi;

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
}
