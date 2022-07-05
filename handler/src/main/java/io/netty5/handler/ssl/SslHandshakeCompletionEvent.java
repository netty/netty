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
import java.util.Objects;

/**
 * Event that is fired once the SSL handshake is complete, which may be because it was successful or there
 * was an error.
 */
public final class SslHandshakeCompletionEvent
        extends SslCompletionEvent<SslHandshakeCompletionEvent.SslHandshakeData> {

    /**
     * Creates a new event that indicates a successful handshake.
     */
    public SslHandshakeCompletionEvent(SSLSession session, String applicationProtocol) {
        super(new SslHandshakeData(Objects.requireNonNull(session, "session"), applicationProtocol));
    }

    /**
     * Creates a new event that indicates an unsuccessful handshake.
     */
    public SslHandshakeCompletionEvent(SSLSession session, String applicationProtocol, Throwable cause) {
        super(session == null && applicationProtocol == null ? null :
                new SslHandshakeData(session, applicationProtocol), cause);
    }

    /**
     * Creates a new event that indicates an unsuccessful handshake.
     */
    public SslHandshakeCompletionEvent(Throwable cause) {
        this(null, null, cause);
    }

    public static final class SslHandshakeData {
        private final SSLSession session;
        private final String applicationProtocol;

        SslHandshakeData(SSLSession session, String applicationProtocol) {
            this.session = session;
            this.applicationProtocol = applicationProtocol;
        }

        public SSLSession session() {
            return session;
        }

        public String applicationProtocol() {
            return applicationProtocol;
        }
    }
}
