/*
 * Copyright 2018 The Netty Project
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

import io.netty.internal.tcnative.SSL;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.IllegalReferenceCountException;
import io.netty.util.internal.EmptyArrays;

import javax.security.auth.Destroyable;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

final class OpenSslPrivateKey extends AbstractReferenceCounted implements PrivateKey {

    private long privateKeyAddress;

    OpenSslPrivateKey(long privateKeyAddress) {
        this.privateKeyAddress = privateKeyAddress;
    }

    @Override
    public String getAlgorithm() {
        return "unknown";
    }

    @Override
    public String getFormat() {
        // As we do not support encoding we should return null as stated in the javadocs of PrivateKey.
        return null;
    }

    @Override
    public byte[] getEncoded() {
        return null;
    }

    private long privateKeyAddress() {
        if (refCnt() <= 0) {
            throw new IllegalReferenceCountException();
        }
        return privateKeyAddress;
    }

    @Override
    protected void deallocate() {
        SSL.freePrivateKey(privateKeyAddress);
        privateKeyAddress = 0;
    }

    @Override
    public OpenSslPrivateKey retain() {
        super.retain();
        return this;
    }

    @Override
    public OpenSslPrivateKey retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public OpenSslPrivateKey touch() {
        super.touch();
        return this;
    }

    @Override
    public OpenSslPrivateKey touch(Object hint) {
        return this;
    }

    /**
     * NOTE: This is a JDK8 interface/method. Due to backwards compatibility
     * reasons it's not possible to slap the {@code @Override} annotation onto
     * this method.
     *
     * @see Destroyable#destroy()
     */
    @Override
    public void destroy() {
        release(refCnt());
    }

    /**
     * NOTE: This is a JDK8 interface/method. Due to backwards compatibility
     * reasons it's not possible to slap the {@code @Override} annotation onto
     * this method.
     *
     * @see Destroyable#isDestroyed()
     */
    @Override
    public boolean isDestroyed() {
        return refCnt() == 0;
    }

    /**
     * Create a new {@link OpenSslKeyMaterial} which uses the private key that is held by {@link OpenSslPrivateKey}.
     *
     * When the material is created we increment the reference count of the enclosing {@link OpenSslPrivateKey} and
     * decrement it again when the reference count of the {@link OpenSslKeyMaterial} reaches {@code 0}.
     */
    OpenSslKeyMaterial newKeyMaterial(long certificateChain, X509Certificate[] chain) {
        return new OpenSslPrivateKeyMaterial(certificateChain, chain);
    }

    // Package-private for unit-test only
    final class OpenSslPrivateKeyMaterial extends AbstractReferenceCounted implements OpenSslKeyMaterial {

        // Package-private for unit-test only
        long certificateChain;
        private final X509Certificate[] x509CertificateChain;

        OpenSslPrivateKeyMaterial(long certificateChain, X509Certificate[] x509CertificateChain) {
            this.certificateChain = certificateChain;
            this.x509CertificateChain = x509CertificateChain == null ?
                    EmptyArrays.EMPTY_X509_CERTIFICATES : x509CertificateChain;
            OpenSslPrivateKey.this.retain();
        }

        @Override
        public X509Certificate[] certificateChain() {
            return x509CertificateChain.clone();
        }

        @Override
        public long certificateChainAddress() {
            if (refCnt() <= 0) {
                throw new IllegalReferenceCountException();
            }
            return certificateChain;
        }

        @Override
        public long privateKeyAddress() {
            if (refCnt() <= 0) {
                throw new IllegalReferenceCountException();
            }
            return OpenSslPrivateKey.this.privateKeyAddress();
        }

        @Override
        public OpenSslKeyMaterial touch(Object hint) {
            OpenSslPrivateKey.this.touch(hint);
            return this;
        }

        @Override
        public OpenSslKeyMaterial retain() {
            super.retain();
            return this;
        }

        @Override
        public OpenSslKeyMaterial retain(int increment) {
            super.retain(increment);
            return this;
        }

        @Override
        public OpenSslKeyMaterial touch() {
            OpenSslPrivateKey.this.touch();
            return this;
        }

        @Override
        protected void deallocate() {
            releaseChain();
            OpenSslPrivateKey.this.release();
        }

        private void releaseChain() {
            SSL.freeX509Chain(certificateChain);
            certificateChain = 0;
        }
    }
}
