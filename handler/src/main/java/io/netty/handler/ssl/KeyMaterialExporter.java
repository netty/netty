/*
 * Copyright 2026 The Netty Project
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
package io.netty.handler.ssl;

import io.netty.util.internal.UnstableApi;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;

/**
 * Implemented by {@link SSLEngine}s that support exporting keying material as described in
 * <a href="https://datatracker.ietf.org/doc/html/rfc5705">RFC 5705</a> and
 * <a href="https://datatracker.ietf.org/doc/html/rfc8446#section-7.5">RFC 8446, section 7.5</a>
 * (also known as a TLS <em>exporter</em>).
 * <p>
 * The exported bytes are derived from the secrets of the current TLS session, so they are identical on both peers
 * of the connection and unique to this session. This makes them useful for channel binding, e.g. to bind an
 * application level proof of possession to the underlying connection.
 * <p>
 * Use an {@code instanceof} check to find out if an {@link SSLEngine} supports this. Implementations can still
 * fail at runtime, for example when the handshake did not complete yet.
 */
@UnstableApi
public interface KeyMaterialExporter {

    /**
     * Export keying material into {@code out}. The number of bytes to export is {@code out.length}.
     *
     * @param label     the exporter label, encoded as US-ASCII. To avoid collisions applications should use a
     *                  label that is unique to them.
     * @param context   the application provided context value or {@code null} if no context should be used.
     *                  For TLS 1.3 passing {@code null} and passing an empty array produce the same keying
     *                  material, for earlier TLS versions they do not.
     * @param out       the array to fill with the exported keying material.
     * @throws SSLException             if the keying material could not be exported, for example because the
     *                                  handshake did not complete yet or the connection was already closed.
     * @throws IllegalStateException    if the implementation does not allow this method to be called from the
     *                                  current thread.
     * @throws NullPointerException     if {@code label} or {@code out} is {@code null}.
     */
    void exportKeyingMaterial(String label, byte[] context, byte[] out) throws SSLException;
}
