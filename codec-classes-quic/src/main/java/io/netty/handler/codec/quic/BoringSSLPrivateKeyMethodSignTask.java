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

import java.util.function.BiConsumer;

final class BoringSSLPrivateKeyMethodSignTask extends BoringSSLPrivateKeyMethodTask {
    private final int signatureAlgorithm;
    private final byte[] digest;

    BoringSSLPrivateKeyMethodSignTask(
            long ssl, int signatureAlgorithm, byte[] digest, BoringSSLPrivateKeyMethod method) {
        super(ssl, method);
        this.signatureAlgorithm = signatureAlgorithm;
        // It's OK to not clone the arrays as we create these in JNI and not reuse.
        this.digest = digest;
    }

    @Override
    protected void runMethod(long ssl, BoringSSLPrivateKeyMethod method, BiConsumer<byte[], Throwable> callback) {
        method.sign(ssl, signatureAlgorithm, digest, callback);
    }
}
