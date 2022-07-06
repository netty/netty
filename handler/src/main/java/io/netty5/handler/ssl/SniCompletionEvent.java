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


/**
 * Event that is fired once we did a selection of a {@link SslContext} based on the {@code SNI hostname},
 * which may be because it was successful or there was an error.
 */
public final class SniCompletionEvent extends SslCompletionEvent {
    private final String hostname;

    /**
     * Creates a new event that indicates a successful processing of the SNI extension.
     *
     * @param hostname      the hostname that was used for SNI.
     */
    public SniCompletionEvent(String hostname) {
        super(null);
        this.hostname = hostname;
    }

    /**
     * Creates a new event that indicates a failed processing of the SNI extension.
     *
     * @param hostname      the hostname that was used for SNI.
     * @param cause         the cause of the failure.
     */
    public SniCompletionEvent(String hostname, Throwable cause) {
        super(null, cause);
        this.hostname = hostname;
    }

    /**
     * Creates a new event that indicates a failed processing of the SNI extension.
     *
     * @param cause         the cause of the failure.
     */
    public SniCompletionEvent(Throwable cause) {
        this(null, cause);
    }

    /**
     * Returns the SNI hostname send by the client if we were able to parse it, {@code null} otherwise.
     */
    public String hostname() {
        return hostname;
    }

    @Override
    public String toString() {
        final Throwable cause = cause();
        return cause == null ? getClass().getSimpleName() + "(SUCCESS='"  + hostname() + "'\")":
                getClass().getSimpleName() +  '(' + cause + ')';
    }
}
