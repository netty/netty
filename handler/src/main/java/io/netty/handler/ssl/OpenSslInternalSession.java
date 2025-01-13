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
import java.security.cert.Certificate;
import java.util.Map;

/**
 * {@link SSLSession} that is specific to our native implementation.
 */
interface OpenSslInternalSession extends OpenSslSession {

    /**
     * Called on a handshake session before being exposed to a {@link javax.net.ssl.TrustManager}.
     * Session data must be cleared by this call.
     */
    void prepareHandshake();

    /**
     * Return the {@link OpenSslSessionId} that can be used to identify this session.
     */
    OpenSslSessionId sessionId();

    /**
     * Set the local certificate chain that is used. It is not expected that this array will be changed at all
     * and so its ok to not copy the array.
     */
    void setLocalCertificate(Certificate[] localCertificate);

    /**
     * Set the details for the session which might come from a cache.
     *
     * @param creationTime the time at which the session was created.
     * @param lastAccessedTime the time at which the session was last accessed via the session infrastructure (cache).
     * @param id the {@link OpenSslSessionId}
     * @param keyValueStorage the key value store. See {@link #keyValueStorage()}.
     */
    void setSessionDetails(long creationTime, long lastAccessedTime, OpenSslSessionId id,
                           Map<String, Object> keyValueStorage);

    /**
     * Return the underlying {@link Map} that is used by the following methods:
     *
     * <ul>
     *     <li>{@link #putValue(String, Object)}</li>
     *     <li>{@link #removeValue(String)}</li>
     *     <li>{@link #getValue(String)}</li>
     *     <li> {@link #getValueNames()}</li>
     * </ul>
     *
     * The {@link Map} must be thread-safe!
     *
     * @return storage
     */
    Map<String, Object> keyValueStorage();

    /**
     * Set the last access time which will be returned by {@link #getLastAccessedTime()}.
     *
     * @param time the time
     */
    void setLastAccessedTime(long time);

    /**
     * Expand (or increase) the value returned by {@link #getApplicationBufferSize()} if necessary.
     * <p>
     * This is only called in a synchronized block, so no need to use atomic operations.
     * @param packetLengthDataOnly The packet size which exceeds the current {@link #getApplicationBufferSize()}.
     */
    void tryExpandApplicationBufferSize(int packetLengthDataOnly);

    /**
     * Called once the handshake has completed.
     */
    void handshakeFinished(byte[] id, String cipher, String protocol, byte[] peerCertificate,
                           byte[][] peerCertificateChain, long creationTime, long timeout) throws SSLException;
}
