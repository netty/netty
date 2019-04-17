/*
 * Copyright 2019 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

/**
 * Notification that an <a href="https://tools.ietf.org/html/rfc7540#section-6.8">GOAWAY</a> frame write attempt was
 * made. This doesn't necessarily mean the write was successful but instead is an indication that the corresponding
 * channel is shutting down.
 */
public final class Http2GoAwayWriteEvent {
    private final int lastKnownStreamId;
    private final long errorCode;

    /**
     * Create a new instance.
     * @param lastKnownStreamId The last known stream identifier.
     * @param errorCode The errorCode see {@link Http2Error}.
     */
    Http2GoAwayWriteEvent(int lastKnownStreamId, long errorCode) {
        this.lastKnownStreamId = lastKnownStreamId;
        this.errorCode = errorCode;
    }

    /**
     * Gets the last known stream identifier.
     * @return the last known stream identifier.
     */
    public int lastKnownStreamId() {
        return lastKnownStreamId;
    }

    /**
     * Gets the errorCode (see {@link Http2Error}).
     * @return the errorCode (see {@link Http2Error}).
     */
    public long errorCode() {
        return errorCode;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " lastKnownStreamId: " + lastKnownStreamId + " errorCode: " + errorCode;
    }
}
