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

import io.netty.util.internal.ObjectUtil;

/**
 * Used when the remote peer supports the
 * <a href="https://tools.ietf.org/html/draft-ietf-quic-datagram-01">QUIC DATAGRAM extension.</a>
 */
public final class QuicDatagramExtensionEvent implements QuicExtensionEvent {

    private final int maxLength;

    QuicDatagramExtensionEvent(int maxLength) {
        this.maxLength = ObjectUtil.checkPositiveOrZero(maxLength, "maxLength");
    }

    /**
     * The maximum datagram payload length the peer will accept. If you try to write bigger datagrams the write will
     * fail.
     *
     * @return the max length.
     */
    public int maxLength() {
        return maxLength;
    }

    @Override
    public String toString() {
        return "QuicDatagramExtensionEvent{" +
                "maxLength=" + maxLength +
                '}';
    }
}
