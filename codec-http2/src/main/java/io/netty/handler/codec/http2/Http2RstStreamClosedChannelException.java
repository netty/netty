/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import java.nio.channels.ClosedChannelException;

/**
 * A {@link ClosedChannelException} which signals that a {@link Http2StreamChannel} was closed because a
 * {@code RST_STREAM} frame was received from the peer for the corresponding HTTP/2 stream while it was still active.
 *
 * @see Http2StreamChannel#closeCause()
 */
public final class Http2RstStreamClosedChannelException extends ClosedChannelException {

    private static final long serialVersionUID = 4171742226598852908L;

    private final long errorCode;

    public Http2RstStreamClosedChannelException(long errorCode) {
        this.errorCode = errorCode;
    }

    /**
     * Returns the HTTP/2 error code that was carried by the {@code RST_STREAM} frame as defined by the
     * HTTP/2 specification.
     */
    public long errorCode() {
        return errorCode;
    }

    /**
     * Returns the {@link Http2Error} that corresponds to {@link #errorCode()}, or {@code null} if the error
     * code does not map to one of the standard HTTP/2 error codes.
     */
    public Http2Error error() {
        return Http2Error.valueOf(errorCode);
    }

    @Override
    public String getMessage() {
        return "Stream reset received with error code: " + errorCode;
    }
}
