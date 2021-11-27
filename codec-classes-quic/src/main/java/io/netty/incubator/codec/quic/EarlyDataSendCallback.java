/*
 * Copyright 2021 The Netty Project
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
package io.netty.incubator.codec.quic;

/**
 * Implementations of this interface can be used to send early data for a {@link QuicChannel}.
 */
@FunctionalInterface
public interface EarlyDataSendCallback {
    /**
     * Allow to send early-data if possible. Please be aware that early data may be replayable and so may have other
     * security concerns then other data.
     *
     * @param quicChannel   the {@link QuicChannel} which will be used to send data on (if any).
     */
    void send(QuicChannel quicChannel);
}
