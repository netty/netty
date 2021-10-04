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

import java.net.SocketAddress;

/**
 * {@link QuicEvent} which is fired when an QUIC connection creation or migration was detected.
 */
public final class QuicConnectionEvent implements QuicEvent {

    private final SocketAddress oldAddress;
    private final SocketAddress newAddress;

    QuicConnectionEvent(SocketAddress oldAddress, SocketAddress newAddress) {
        this.oldAddress = oldAddress;
        this.newAddress = newAddress;
    }

    /**
     * The old {@link SocketAddress} of the connection or {@code null} if the connection was just created.
     *
     * @return the old {@link SocketAddress} of the connection.
     */
    public SocketAddress oldAddress() {
        return oldAddress;
    }

    /**
     * The new {@link SocketAddress} of the connection.
     *
     * @return the new {@link SocketAddress} of the connection.
     */
    public SocketAddress newAddress() {
        return newAddress;
    }
}
