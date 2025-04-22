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

final class BoringSSLHandshakeCompleteCallback {

    private final QuicheQuicSslEngineMap map;

    BoringSSLHandshakeCompleteCallback(QuicheQuicSslEngineMap map) {
        this.map = map;
    }

    @SuppressWarnings("unused")
    void handshakeComplete(long ssl, byte[] id, String cipher, String protocol, byte[] peerCertificate,
                           byte[][] peerCertificateChain, long creationTime, long timeout, byte[] applicationProtocol,
                           boolean sessionReused) {
        QuicheQuicSslEngine engine = map.get(ssl);
        if (engine != null) {
            engine.handshakeFinished(id, cipher, protocol, peerCertificate, peerCertificateChain, creationTime,
                    timeout, applicationProtocol, sessionReused);
        }
    }
}
