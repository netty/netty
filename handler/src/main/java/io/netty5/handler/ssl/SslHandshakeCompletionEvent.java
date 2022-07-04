/*
 * Copyright 2013 The Netty Project
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

import javax.net.ssl.SSLSession;

/**
 * Event that is fired once the SSL handshake is complete, which may be because it was successful or there
 * was an error.
 */
public final class SslHandshakeCompletionEvent extends SslCompletionEvent {

    private final String applicationProtocol;

    /**
     * Creates a new event that indicates a successful handshake.
     */
    public SslHandshakeCompletionEvent(SSLSession session, String applicationProtocol) {
        super(session);
        this.applicationProtocol = applicationProtocol;
    }

    /**
     * Returns the application protocol (if any).
     *
     * @return application protocol.
     */
    public String applicationProtocol() {
        return applicationProtocol;
    }

    /**
     * Creates a new event that indicates an unsuccessful handshake.
     */
    public SslHandshakeCompletionEvent(SSLSession session, Throwable cause) {
        super(session, cause);
        this.applicationProtocol = null;
    }
}
