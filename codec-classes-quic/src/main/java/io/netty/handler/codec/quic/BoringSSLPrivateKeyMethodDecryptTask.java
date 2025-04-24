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

final class BoringSSLPrivateKeyMethodDecryptTask extends BoringSSLPrivateKeyMethodTask {
    private final byte[] input;

    BoringSSLPrivateKeyMethodDecryptTask(long ssl, byte[] input, BoringSSLPrivateKeyMethod method) {
        super(ssl, method);
        // It's OK to not clone the arrays as we create these in JNI and not reuse.
        this.input = input;
    }

    @Override
    protected void runMethod(long ssl, BoringSSLPrivateKeyMethod method, BiConsumer<byte[], Throwable> consumer) {
        method.decrypt(ssl, input, consumer);
    }
}
