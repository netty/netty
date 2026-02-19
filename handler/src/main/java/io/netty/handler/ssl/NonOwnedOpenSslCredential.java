/*
 * Copyright 2026 The Netty Project
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

import io.netty.util.AbstractReferenceCounted;
import io.netty.util.IllegalReferenceCountException;

/**
 * A non-owning wrapper for an {@link OpenSslCredential} pointer.
 *
 * <p>This class is used when we need to expose an SSL_CREDENTIAL pointer that is managed
 * by OpenSSL itself (e.g., the credential selected during the handshake). Unlike
 * {@link DefaultOpenSslCredential}, this wrapper does not free the underlying credential
 * when its reference count reaches zero, as the lifetime is managed externally.
 *
 * <p>This is a BoringSSL-specific feature.
 */
final class NonOwnedOpenSslCredential extends AbstractReferenceCounted implements OpenSslCredentialPointer {

    private final long credential;
    private final CredentialType type;
    private volatile boolean released;

    /**
     * Creates a new non-owning credential wrapper.
     *
     * @param credential the native SSL_CREDENTIAL pointer (must not be 0)
     * @param type the credential type
     */
    NonOwnedOpenSslCredential(long credential, CredentialType type) {
        if (credential == 0) {
            throw new IllegalArgumentException("credential pointer must not be 0");
        }
        this.credential = credential;
        this.type = type;
    }

    @Override
    public long credentialAddress() {
        if (released) {
            throw new IllegalReferenceCountException();
        }
        return credential;
    }

    @Override
    public CredentialType type() {
        return type;
    }

    @Override
    public OpenSslCredential retain() {
        return (OpenSslCredential) super.retain();
    }

    @Override
    public OpenSslCredential retain(int increment) {
        return (OpenSslCredential) super.retain(increment);
    }

    @Override
    public OpenSslCredential touch() {
        return (OpenSslCredential) super.touch();
    }

    @Override
    public OpenSslCredential touch(Object hint) {
        return this;
    }

    @Override
    protected void deallocate() {
        released = true;
    }
}
