/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.ssl;

import io.netty.buffer.ByteBufAllocator;

import java.security.cert.Certificate;

/**
 * Special {@link OpenSslEngine} which will ensure {@link #shutdown()} is called via finalizer.
 */
final class FinalizingOpenSslEngine extends OpenSslEngine {

    FinalizingOpenSslEngine(long sslCtx, ByteBufAllocator alloc, boolean clientMode,
                            OpenSslSessionContext sessionContext, OpenSslApplicationProtocolNegotiator apn,
                            OpenSslEngineMap engineMap, boolean rejectRemoteInitiatedRenegation, String peerHost,
                            int peerPort, Certificate[] localCerts, ClientAuth clientAuth) {
        super(sslCtx, alloc, clientMode, sessionContext, apn, engineMap, rejectRemoteInitiatedRenegation, peerHost,
                peerPort, localCerts, clientAuth);
    }

    @Override
    @SuppressWarnings("FinalizeDeclaration")
    protected void finalize() throws Throwable {
        super.finalize();
        // Call shutdown as the user may have created the OpenSslEngine and not used it at all.
        shutdown();
    }

}
