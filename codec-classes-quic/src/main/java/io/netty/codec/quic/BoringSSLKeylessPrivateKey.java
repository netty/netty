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
package io.netty.codec.quic;

import io.netty.util.internal.EmptyArrays;

import java.security.PrivateKey;

final class BoringSSLKeylessPrivateKey implements PrivateKey {

    static final BoringSSLKeylessPrivateKey INSTANCE = new BoringSSLKeylessPrivateKey();

    private BoringSSLKeylessPrivateKey() {
    }

    @Override
    public String getAlgorithm() {
        return "keyless";
    }

    @Override
    public String getFormat() {
        return "keyless";
    }

    @Override
    public byte[] getEncoded() {
        return EmptyArrays.EMPTY_BYTES;
    }
}
