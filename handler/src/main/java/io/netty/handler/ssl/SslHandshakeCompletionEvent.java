/*
 * Copyright 2013 The Netty Project
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


/**
 * Event that is fired once the SSL handshake is complete, which may be because it was successful or there
 * was an error.
 */
public final class SslHandshakeCompletionEvent {

    public static final SslHandshakeCompletionEvent SUCCESS = new SslHandshakeCompletionEvent();

    private final Throwable cause;

    /**
     * Creates a new event that indicates a successful handshake.
     */
    private SslHandshakeCompletionEvent() {
        cause = null;
    }

    /**
     * Creates a new event that indicates an unsuccessful handshake.
     * Use {@link #SUCCESS} to indicate a successful handshake.
     */
    public SslHandshakeCompletionEvent(Throwable cause) {
        if (cause == null) {
            throw new NullPointerException("cause");
        }
        this.cause = cause;
    }

    /**
     * Return {@code true} if the handshake was successful
     */
    public boolean isSuccess() {
        return cause == null;
    }

    /**
     * Return the {@link Throwable} if {@link #isSuccess()} returns {@code false}
     * and so the handshake failed.
     */
    public Throwable cause() {
        return cause;
    }

    @Override
    public String toString() {
        final Throwable cause = cause();
        return cause == null? "SslHandshakeCompletionEvent(SUCCESS)" : "SslHandshakeCompletionEvent(" + cause + ')';
    }
}
