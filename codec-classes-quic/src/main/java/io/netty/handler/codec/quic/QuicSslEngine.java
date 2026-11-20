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

/**
 * An {@link SSLEngine} that can be used for QUIC.
 */
public abstract class QuicSslEngine extends SSLEngine {

    /**
     * Returns the server name indicated by the peer, if it was captured for this connection.
     * <p>
     * On the server side, SNI is captured when the {@link QuicSslContext} was configured with an SNI mapping,
     * or when it uses a dynamic {@link BoringSSLKeylessManagerFactory}. The hostname is available during
     * {@link javax.net.ssl.X509ExtendedKeyManager#chooseEngineServerAlias(String, java.security.Principal[],
     * javax.net.ssl.SSLEngine)} and remains available after the handshake. A {@code null} result does not
     * necessarily mean the peer sent no SNI extension: the context may not have enabled SNI capture.
     * <p>
     * This method returns {@code null} for client engines.
     *
     * @return the captured SNI hostname, or {@code null} if it is unavailable.
     */
    @Nullable
    public String getRequestedSniHostname() {
        return null;
    }
}
