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
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;

import java.security.cert.X509Certificate;

final class DefaultOpenSslKeyMaterial extends AbstractReferenceCounted implements OpenSslKeyMaterial {

    private static final ResourceLeakDetector<DefaultOpenSslKeyMaterial> leakDetector =
            ResourceLeakDetectorFactory.instance().newResourceLeakDetector(DefaultOpenSslKeyMaterial.class);
    private final ResourceLeakTracker<DefaultOpenSslKeyMaterial> leak;
    private final X509Certificate[] x509CertificateChain;
    private long chain;
    private long privateKey;

    DefaultOpenSslKeyMaterial(long chain, long privateKey, X509Certificate[] x509CertificateChain) {
        this.chain = chain;
        this.privateKey = privateKey;
        this.x509CertificateChain = x509CertificateChain;
        leak = leakDetector.track(this);
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
        return chain;
    }

    @Override
    public long privateKeyAddress() {
        if (refCnt() <= 0) {
            throw new IllegalReferenceCountException();
        }
        return privateKey;
    }

    @Override
    protected void deallocate() {
        SSL.freeX509Chain(chain);
        chain = 0;
        SSL.freePrivateKey(privateKey);
        privateKey = 0;
        if (leak != null) {
            boolean closed = leak.close(this);
            assert closed;
        }
    }

    @Override
    public DefaultOpenSslKeyMaterial retain() {
        if (leak != null) {
            leak.record();
        }
        super.retain();
        return this;
    }

    @Override
    public DefaultOpenSslKeyMaterial retain(int increment) {
        if (leak != null) {
            leak.record();
        }
        super.retain(increment);
        return this;
    }

    @Override
    public DefaultOpenSslKeyMaterial touch() {
        if (leak != null) {
            leak.record();
        }
        super.touch();
        return this;
    }

    @Override
    public DefaultOpenSslKeyMaterial touch(Object hint) {
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
