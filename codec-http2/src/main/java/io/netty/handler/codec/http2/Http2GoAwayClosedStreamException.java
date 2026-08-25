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
 * {@code GOAWAY} frame was received from the peer which indicated that the corresponding HTTP/2 stream will
 * never be processed.
 *
 * @see Http2StreamChannel#closeCause()
 */
public final class Http2GoAwayClosedStreamException extends ClosedChannelException {

    private static final long serialVersionUID = -8118885433489741466L;

    private final int lastStreamId;
    private final long errorCode;
    private final byte[] debugData;

    public Http2GoAwayClosedStreamException(int lastStreamId, long errorCode, byte[] debugData) {
        this.lastStreamId = lastStreamId;
        this.errorCode = errorCode;
        this.debugData = debugData.clone();
    }

    /**
     * Returns the last stream identifier carried by the {@code GOAWAY} frame, as defined by the HTTP/2
     * specification.
     */
    public int lastStreamId() {
        return lastStreamId;
    }

    /**
     * Returns the HTTP/2 error code that was carried by the {@code GOAWAY} frame as defined by the HTTP/2
     * specification.
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

    /**
     * Returns the (possibly empty) debug data that was carried by the {@code GOAWAY} frame.
     */
    public byte[] debugData() {
        return debugData.clone();
    }

    @Override
    public String getMessage() {
        return "Stream closed because of GOAWAY received with lastStreamId=" + lastStreamId
                + ", errorCode=" + errorCode;
    }
}
