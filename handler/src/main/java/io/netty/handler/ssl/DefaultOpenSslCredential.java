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

import io.netty.internal.tcnative.SSLCredential;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.IllegalReferenceCountException;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;

/**
 * Default implementation of {@link OpenSslCredential}.
 *
 * <p>This class manages the lifecycle of a native BoringSSL {@code SSL_CREDENTIAL} object.
 */
final class DefaultOpenSslCredential extends AbstractReferenceCounted implements OpenSslCredentialPointer {

    private static final ResourceLeakDetector<DefaultOpenSslCredential> leakDetector =
            ResourceLeakDetectorFactory.instance().newResourceLeakDetector(DefaultOpenSslCredential.class);

    private final ResourceLeakTracker<DefaultOpenSslCredential> leak;
    private final CredentialType type;
    private long credential;

    /**
     * Creates a new credential instance.
     *
     * @param credential the native SSL_CREDENTIAL pointer
     * @param type the credential type
     */
    DefaultOpenSslCredential(long credential, CredentialType type) {
        this.credential = credential;
        this.type = type;
        this.leak = leakDetector.track(this);
    }

    @Override
    public long credentialAddress() {
        if (refCnt() <= 0) {
            throw new IllegalReferenceCountException();
        }
        return credential;
    }

    @Override
    public CredentialType type() {
        return type;
    }

    @Override
    protected void deallocate() {
        try {
            SSLCredential.free(credential);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to free SSL_CREDENTIAL", e);
        } finally {
            credential = 0;
            if (leak != null) {
                boolean closed = leak.close(this);
                assert closed;
            }
        }
    }

    @Override
    public DefaultOpenSslCredential retain() {
        if (leak != null) {
            leak.record();
        }
        super.retain();
        return this;
    }

    @Override
    public DefaultOpenSslCredential retain(int increment) {
        if (leak != null) {
            leak.record();
        }
        super.retain(increment);
        return this;
    }

    @Override
    public DefaultOpenSslCredential touch() {
        if (leak != null) {
            leak.record();
        }
        super.touch();
        return this;
    }

    @Override
    public DefaultOpenSslCredential touch(Object hint) {
        if (leak != null) {
            leak.record(hint);
        }
        return this;
    }

    @Override
    public boolean release() {
        if (leak != null) {
            leak.record();
        }
        return super.release();
    }

    @Override
    public boolean release(int decrement) {
        if (leak != null) {
            leak.record();
        }
        return super.release(decrement);
    }
}
