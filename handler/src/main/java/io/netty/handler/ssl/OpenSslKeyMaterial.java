/*
 * Copyright 2018 The Netty Project
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

import java.security.cert.X509Certificate;

/**
 * Holds references to the native key-material that is used by OpenSSL.
 */
interface OpenSslKeyMaterial extends ReferenceCounted {

    /**
     * Returns the configured {@link X509Certificate}s.
     */
    X509Certificate[] certificateChain();

    /**
     * Returns the pointer to the {@code STACK_OF(X509)} which holds the certificate chain.
     */
    long certificateChainAddress();

    /**
     * Returns the pointer to the {@code EVP_PKEY}.
     */
    long privateKeyAddress();

    @Override
    OpenSslKeyMaterial retain();

    @Override
    OpenSslKeyMaterial retain(int increment);

    @Override
    OpenSslKeyMaterial touch();

    @Override
    OpenSslKeyMaterial touch(Object hint);

    @Override
    boolean release();

    @Override
    boolean release(int decrement);
}
