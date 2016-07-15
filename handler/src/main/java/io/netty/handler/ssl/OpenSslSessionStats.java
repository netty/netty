/*
 * Copyright 2014 The Netty Project
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

import org.apache.tomcat.jni.SSLContext;

/**
 * Stats exposed by an OpenSSL session context.
 *
 * @see <a href="https://www.openssl.org/docs/ssl/SSL_CTX_sess_number.html"><code>SSL_CTX_sess_number</code></a>
 */
public final class OpenSslSessionStats {

    private final ReferenceCountedOpenSslContext context;

    // IMPORTANT: We take the OpenSslContext and not just the long (which points the native instance) to prevent
    //            the GC to collect OpenSslContext as this would also free the pointer and so could result in a
    //            segfault when the user calls any of the methods here that try to pass the pointer down to the native
    //            level.
    OpenSslSessionStats(ReferenceCountedOpenSslContext context) {
        this.context = context;
    }

    /**
     * Returns the current number of sessions in the internal session cache.
     */
    public long number() {
        return SSLContext.sessionNumber(context.ctx);
    }

    /**
     * Returns the number of started SSL/TLS handshakes in client mode.
     */
    public long connect() {
        return SSLContext.sessionConnect(context.ctx);
    }

    /**
     * Returns the number of successfully established SSL/TLS sessions in client mode.
     */
    public long connectGood() {
        return SSLContext.sessionConnectGood(context.ctx);
    }

    /**
     * Returns the number of start renegotiations in client mode.
     */
    public long connectRenegotiate() {
        return SSLContext.sessionConnectRenegotiate(context.ctx);
    }

    /**
     * Returns the number of started SSL/TLS handshakes in server mode.
     */
    public long accept() {
        return SSLContext.sessionAccept(context.ctx);
    }

    /**
     * Returns the number of successfully established SSL/TLS sessions in server mode.
     */
    public long acceptGood() {
        return SSLContext.sessionAcceptGood(context.ctx);
    }

    /**
     * Returns the number of start renegotiations in server mode.
     */
    public long acceptRenegotiate() {
        return SSLContext.sessionAcceptRenegotiate(context.ctx);
    }

    /**
     * Returns the number of successfully reused sessions. In client mode, a session set with {@code SSL_set_session}
     * successfully reused is counted as a hit. In server mode, a session successfully retrieved from internal or
     * external cache is counted as a hit.
     */
    public long hits() {
        return SSLContext.sessionHits(context.ctx);
    }

    /**
     * Returns the number of successfully retrieved sessions from the external session cache in server mode.
     */
    public long cbHits() {
        return SSLContext.sessionCbHits(context.ctx);
    }

    /**
     * Returns the number of sessions proposed by clients that were not found in the internal session cache
     * in server mode.
     */
    public long misses() {
        return SSLContext.sessionMisses(context.ctx);
    }

    /**
     * Returns the number of sessions proposed by clients and either found in the internal or external session cache
     * in server mode, but that were invalid due to timeout. These sessions are not included in the {@link #hits()}
     * count.
     */
    public long timeouts() {
        return SSLContext.sessionTimeouts(context.ctx);
    }

    /**
     * Returns the number of sessions that were removed because the maximum session cache size was exceeded.
     */
    public long cacheFull() {
        return SSLContext.sessionCacheFull(context.ctx);
    }

    /**
     * Returns the number of times a client presented a ticket that did not match any key in the list.
     */
    public long ticketKeyFail() {
        return SSLContext.sessionTicketKeyFail(context.ctx);
    }

    /**
     * Returns the number of times a client did not present a ticket and we issued a new one
     */
    public long ticketKeyNew() {
        return SSLContext.sessionTicketKeyNew(context.ctx);
    }

    /**
     * Returns the number of times a client presented a ticket derived from an older key,
     * and we upgraded to the primary key.
     */
    public long ticketKeyRenew() {
        return SSLContext.sessionTicketKeyRenew(context.ctx);
    }

    /**
     * Returns the number of times a client presented a ticket derived from the primary key.
     */
    public long ticketKeyResume() {
        return SSLContext.sessionTicketKeyResume(context.ctx);
    }
}
