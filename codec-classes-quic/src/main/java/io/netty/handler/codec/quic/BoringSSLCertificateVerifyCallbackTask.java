/*
 * Copyright 2022 The Netty Project
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
package io.netty.handler.codec.quic;


/**
 * Execute {@link BoringSSLCertificateVerifyCallback#verify(long, byte[][], String)}.
 */
final class BoringSSLCertificateVerifyCallbackTask extends BoringSSLTask {
    private final byte[][] x509;
    private final String authAlgorithm;
    private final BoringSSLCertificateVerifyCallback verifier;

    BoringSSLCertificateVerifyCallbackTask(long ssl, byte[][] x509, String authAlgorithm,
                                           BoringSSLCertificateVerifyCallback verifier) {
        super(ssl);
        this.x509 = x509;
        this.authAlgorithm = authAlgorithm;
        this.verifier = verifier;
    }

    @Override
    protected void runTask(long ssl, TaskCallback callback) {
        int result = verifier.verify(ssl, x509, authAlgorithm);
        callback.onResult(ssl, result);
    }
}
