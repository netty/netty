/*
 * Copyright 2025 The Netty Project
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

/**
 * Signals a stream reset.
 */
public final class QuicStreamResetException extends QuicStreamException {

    private final long applicationProtocolCode;

    public QuicStreamResetException(String message, long applicationProtocolCode) {
        super(message);

        this.applicationProtocolCode = applicationProtocolCode;
    }

    /**
     * Returns the optional application protocol error code set on {@code RESET_STREAM} frames.
     *
     * Note: this is not yet implemented for {@code STOP_SENDING} frames.
     *
     * @return the optional application protocol error code or {@code -1} when no such code is provided.
     */
    public long applicationProtocolCode() {
        return applicationProtocolCode;
    }
}
