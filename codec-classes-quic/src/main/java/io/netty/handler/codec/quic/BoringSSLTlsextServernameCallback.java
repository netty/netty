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

import io.netty.util.Mapping;

final class BoringSSLTlsextServernameCallback {

    private final QuicheQuicSslEngineMap engineMap;
    private final Mapping<? super String, ? extends QuicSslContext> mapping;

    BoringSSLTlsextServernameCallback(QuicheQuicSslEngineMap engineMap,
                                      Mapping<? super String, ? extends QuicSslContext> mapping) {
        this.engineMap = engineMap;
        this.mapping = mapping;
    }

    @SuppressWarnings("unused")
    long selectCtx(long ssl, String serverName) {
        final QuicheQuicSslEngine engine = engineMap.get(ssl);
        if (engine == null) {
            // May be null if it was destroyed in the meantime.
            return -1;
        }

        QuicSslContext context = mapping.map(serverName);
        if (context == null) {
            return -1;
        }
        return engine.moveTo(serverName, (QuicheQuicSslContext) context);
    }
}
