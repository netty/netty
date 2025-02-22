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
package io.netty5.handler.ssl;

import io.netty5.buffer.BufferAllocator;

import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import java.security.cert.Certificate;
import java.util.List;
import java.util.Map;

/**
 * This class will use a finalizer to ensure native resources are automatically cleaned up. To avoid finalizers
 * and manually release the native memory see {@link ReferenceCountedOpenSslContext}.
 */
public abstract class OpenSslContext extends ReferenceCountedOpenSslContext {
    OpenSslContext(Iterable<String> ciphers, CipherSuiteFilter cipherFilter, ApplicationProtocolConfig apnCfg,
                   int mode, Certificate[] keyCertChain,
                   ClientAuth clientAuth, String[] protocols, boolean startTls,
                   boolean enableOcsp, String endpointIdentificationAlgorithm,
                   List<SNIServerName> serverNames,
                   ResumptionController resumptionController,
                   Map.Entry<SslContextOption<?>, Object>... options)
            throws SSLException {
        super(ciphers, cipherFilter, toNegotiator(apnCfg), mode, keyCertChain,
                clientAuth, protocols, startTls, enableOcsp, false, endpointIdentificationAlgorithm, serverNames,
                resumptionController, options);
    }

    OpenSslContext(Iterable<String> ciphers, CipherSuiteFilter cipherFilter, OpenSslApplicationProtocolNegotiator apn,
                   int mode, Certificate[] keyCertChain,
                   ClientAuth clientAuth, String[] protocols, boolean startTls, boolean enableOcsp,
                   String endpointIdentificationAlgorithm, List<SNIServerName> serverNames,
                   ResumptionController resumptionController,
                   Map.Entry<SslContextOption<?>, Object>... options)
            throws SSLException {
        super(ciphers, cipherFilter, apn, mode, keyCertChain,
                clientAuth, protocols, startTls, enableOcsp, false, endpointIdentificationAlgorithm, serverNames,
                resumptionController, options);
    }

    @Override
    final SSLEngine newEngine0(BufferAllocator alloc, String peerHost, int peerPort, boolean jdkCompatibilityMode) {
        return new OpenSslEngine(this, alloc, peerHost, peerPort, jdkCompatibilityMode,
                endpointIdentificationAlgorithm, serverNames);
    }

    @Override
    @SuppressWarnings("FinalizeDeclaration")
    protected final void finalize() throws Throwable {
        super.finalize();
        OpenSsl.releaseIfNeeded(this);
    }
}
