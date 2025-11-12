/*
 * Copyright 2020 The Netty Project
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

import org.jetbrains.annotations.Nullable;

/**
 * Exception produced while processing {@code QUIC}.
 */
public final class QuicException extends Exception {

    private final QuicTransportError error;
    private final int applicationProtocolCode;

    QuicException(String message) {
        this(message, -1);
    }

    QuicException(String message, int applicationProtocolCode) {
        super(message);
        this.error = null;
        this.applicationProtocolCode = applicationProtocolCode;
    }

    public QuicException(QuicTransportError error) {
        super(error.name());
        this.error = error;
        this.applicationProtocolCode = -1;
    }

    public QuicException(String message, QuicTransportError error) {
        super(message);
        this.error = error;
        this.applicationProtocolCode = -1;
    }

    public QuicException(Throwable cause, QuicTransportError error) {
        super(cause);
        this.error = error;
        this.applicationProtocolCode = -1;
    }

    public QuicException(String message, Throwable cause, QuicTransportError error) {
        super(message, cause);
        this.error = error;
        this.applicationProtocolCode = -1;
    }

    /**
     * Returns the {@link QuicTransportError} which was the cause of the {@link QuicException}.
     *
     * @return  the {@link QuicTransportError} that caused this {@link QuicException} or {@code null} if
     *          it was caused by something different.
     */
    @Nullable
    public QuicTransportError error() {
        return error;
    }

    /**
     * Returns the optional application protocol error code set on {@code RESET_STREAM} frames.
     *
     * Note: this is not yet implemented for {@code STOP_SENDING} frames.
     *
     * @return the optional application protocol error code or {@code -1} when no such code is provided.
     */
    public int applicationProtocolCode() {
        return applicationProtocolCode;
    }
}
