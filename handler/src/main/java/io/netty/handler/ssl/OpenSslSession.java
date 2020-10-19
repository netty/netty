/*
 * Copyright 2018 The Netty Project
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

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

interface OpenSslSession extends SSLSession {

    /**
     * Finish the handshake and so init everything in the {@link OpenSslSession} that should be accessible by
     * the user.
     */
    void handshakeFinished() throws SSLException;

    /**
     * Expand (or increase) the value returned by {@link #getApplicationBufferSize()} if necessary.
     * <p>
     * This is only called in a synchronized block, so no need to use atomic operations.
     * @param packetLengthDataOnly The packet size which exceeds the current {@link #getApplicationBufferSize()}.
     */
    void tryExpandApplicationBufferSize(int packetLengthDataOnly);
}
