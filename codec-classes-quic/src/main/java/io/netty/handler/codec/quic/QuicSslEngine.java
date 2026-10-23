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

import org.jetbrains.annotations.Nullable;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;

/**
 * An {@link SSLEngine} that can be used for QUIC.
 */
public abstract class QuicSslEngine extends SSLEngine {

    /**
     * Export keying material as described in <a href="https://datatracker.ietf.org/doc/html/rfc5705">RFC 5705</a>
     * (also known as a TLS <em>exporter</em>). The returned bytes are derived from the secrets of the current
     * TLS session and so are guaranteed to be identical on both peers of the connection, while being unique to
     * this session. This makes them useful for channel binding, e.g. to bind an application level proof of
     * possession to the underlying QUIC connection.
     *
     * @param label     the exporter label, encoded as US-ASCII. To avoid collisions applications should use a
     *                  label that is unique to them.
     * @param context   the application provided context value or {@code null} if no context should be used.
     *                  As QUIC always uses TLS 1.3, passing {@code null} and passing an empty array produce
     *                  the same keying material.
     * @param length    the number of bytes of keying material to generate. Must be non-negative.
     * @return          the exported keying material of the requested {@code length}.
     * @throws SSLException                  if the keying material could not be exported, for example because the
     *                                       handshake did not complete yet or the connection was already closed.
     * @throws IllegalArgumentException      if {@code length} is negative.
     * @throws NullPointerException          if {@code label} is {@code null}.
     * @throws UnsupportedOperationException if the underlying implementation does not support exporting keying
     *                                       material.
     */
    public byte[] exportKeyingMaterial(String label, byte @Nullable [] context, int length) throws SSLException {
        throw new UnsupportedOperationException();
    }
}
