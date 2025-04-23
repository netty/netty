/*
 * Copyright 2023 The Netty Project
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

import java.util.Arrays;

/**
 * Event that is generated if the remote peer sends a
 * <a href="https://www.rfc-editor.org/rfc/rfc9000#name-connection_close-frames">CLOSE_CONNECTION frame</a>.
 * This allows to inspect the various details of the cause of the close.
 */
public final class QuicConnectionCloseEvent implements QuicEvent {

    final boolean applicationClose;
    final int error;
    final byte[] reason;

    QuicConnectionCloseEvent(boolean applicationClose, int error, byte[] reason) {
        this.applicationClose = applicationClose;
        this.error = error;
        this.reason = reason;
    }

    /**
     * Return {@code true} if this was an application close, {@code false} otherwise.
     *
     * @return  if this is an application close.
     */
    public boolean isApplicationClose() {
        return applicationClose;
    }

    /**
     * Return the error that was provided for the close.
     *
     * @return the error.
     */
    public int error() {
        return error;
    }

    /**
     * Returns {@code true} if a <a href="https://www.rfc-editor.org/rfc/rfc9001#section-4.8">TLS error</a>
     * is contained.
     * @return {@code true} if this is an {@code TLS error}, {@code false} otherwise.
     */
    public boolean isTlsError() {
        return !applicationClose && error >= 0x0100;
    }

    /**
     * Returns the reason for the close, which may be empty if no reason was given as part of the close.
     *
     * @return  the reason.
     */
    public byte[] reason() {
        return reason.clone();
    }

    @Override
    public String toString() {
        return "QuicConnectionCloseEvent{" +
                "applicationClose=" + applicationClose +
                ", error=" + error +
                ", reason=" + Arrays.toString(reason) +
                '}';
    }

    /**
     * Extract the contained {@code TLS error} from the {@code QUIC error}. If the given {@code QUIC error} does not
     * contain a {@code TLS error} it will return {@code -1}.
     *
     * @param error the {@code QUIC error}
     * @return      the {@code TLS error} or {@code -1} if there was no {@code TLS error} contained.
     */
    public static int extractTlsError(int error) {
        int tlsError = error - 0x0100;
        if (tlsError < 0) {
            return -1;
        }
        return tlsError;
    }
}
