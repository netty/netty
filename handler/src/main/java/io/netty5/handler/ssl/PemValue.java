/*
 * Copyright 2016 The Netty Project
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

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.BufferHolder;

import java.security.cert.X509Certificate;

/**
 * A PEM encoded value.
 *
 * @see PemEncoded
 * @see #toPEM(java.security.PrivateKey)
 * @see PemX509Certificate#toPEM(BufferAllocator, X509Certificate...)
 */
class PemValue extends BufferHolder<PemValue> implements PemEncoded {

    PemValue(Buffer content) {
        super(content.makeReadOnly());
    }

    @Override
    public Buffer content() {
        if (!isAccessible()) {
            throw new IllegalStateException("PemValue is closed.");
        }

        return getBuffer();
    }

    @Override
    public PemValue copy() {
        Buffer buffer = getBuffer();
        return new PemValue(buffer.copy(buffer.readerOffset(), buffer.readableBytes(), true));
    }

    @Override
    protected PemValue receive(Buffer buf) {
        return new PemValue(buf);
    }
}
