/*
 * Copyright 2021 The Netty Project
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

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.SslContext;

import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Special {@link SslContext} that can be used for {@code QUIC}.
 */
public abstract class QuicSslContext extends SslContext {

    @Override
    public abstract QuicSslEngine newEngine(ByteBufAllocator alloc);

    @Override
    public abstract QuicSslEngine newEngine(ByteBufAllocator alloc, String peerHost, int peerPort);

    @Override
    public abstract QuicSslSessionContext sessionContext();

    static X509Certificate[] toX509Certificates0(InputStream stream)
            throws CertificateException {
        return SslContext.toX509Certificates(stream);
    }
}
