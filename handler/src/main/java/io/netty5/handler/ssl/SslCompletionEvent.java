/*
 * Copyright 2017 The Netty Project
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

import io.netty5.handler.codec.ProtocolEvent;

import javax.net.ssl.SSLSession;

import static java.util.Objects.requireNonNull;

/**
 * A {@link ProtocolEvent} for a completed SSL related event.
 */
public abstract class SslCompletionEvent implements ProtocolEvent {
    private final SSLSession session;
    private final Throwable cause;

    SslCompletionEvent(SSLSession session) {
        this.session = session;
        cause = null;
    }

    SslCompletionEvent(SSLSession session, Throwable cause) {
        this.session = session;
        this.cause = requireNonNull(cause, "cause");
    }

    /**
     * Return the {@link Throwable} if {@link #isSuccess()} returns {@code false}
     * and so the completion failed.
     */
    public final Throwable cause() {
        return cause;
    }

    /**
     * Returns the {@link SSLSession} or {@code null} if none existed yet.
     *
     * @return the session.
     */
    public SSLSession session() {
        return session;
    }

    @Override
    public String toString() {
        final Throwable cause = cause();
        return cause == null? getClass().getSimpleName() + "(SUCCESS)" :
                getClass().getSimpleName() +  '(' + cause + ')';
    }
}
