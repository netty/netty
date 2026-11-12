/*
 * Copyright 2026 The Netty Project
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

import io.netty.util.internal.ObjectUtil;

/**
 * Event that is fired on a {@link QuicStreamChannel} if the remote peer sends a
 * <a href="https://www.rfc-editor.org/rfc/rfc9000#name-stop_sending-frames">STOP_SENDING frame</a>, which means it
 * does not want to receive any more data on this stream. The output of the stream is shut down: what is written
 * afterwards fails.
 */
public final class QuicStreamStoppedEvent implements QuicEvent {

    private final long applicationProtocolCode;

    /**
     * Creates a new instance.
     *
     * @param applicationProtocolCode the application protocol error code set on the {@code STOP_SENDING} frame, or
     *                                {@code -1} when it is not known.
     * @throws IllegalArgumentException if {@code applicationProtocolCode} is lower than {@code -1}.
     */
    QuicStreamStoppedEvent(long applicationProtocolCode) {
        this.applicationProtocolCode = ObjectUtil.checkInRange(applicationProtocolCode, -1, Long.MAX_VALUE,
                "applicationProtocolCode");
    }

    /**
     * Returns the application protocol error code set on the {@code STOP_SENDING} frame.
     *
     * @return the application protocol error code or {@code -1} when it is not known.
     */
    public long applicationProtocolCode() {
        return applicationProtocolCode;
    }

    @Override
    public String toString() {
        return "QuicStreamStoppedEvent{applicationProtocolCode=" + applicationProtocolCode + '}';
    }
}
