/*
 * Copyright 2024 The Netty Project
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

import java.net.InetSocketAddress;

/**
 * Statistics about a path of the {@code QUIC} connection.
 * If unknown by the implementation it might return {@code -1} values
 * for the various methods.
 */
public interface QuicConnectionPathStats {
    /**
     * @return The local address used by this path.
     */
    InetSocketAddress localAddress();

    /**
     * @return The peer address seen by this path.
     */
    InetSocketAddress peerAddress();

    /**
     * @return The validation state of the path.
     */
    long validationState();

    /**
     * @return Whether this path is active.
     */
    boolean active();

    /**
     * @return The number of QUIC packets received on this path.
     */
    long recv();

    /**
     * @return The number of QUIC packets sent on this path.
     */
    long sent();

    /**
     * @return The number of QUIC packets that were lost on this path.
     */
    long lost();

    /**
     * @return The number of sent QUIC packets with retransmitted data on this path.
     */
    long retrans();

    /**
     * @return The estimated round-trip time of the path (in nanoseconds).
     */
    long rtt();

    /**
     * @return The size of the path's congestion window in bytes.
     */
    long cwnd();

    /**
     * @return The number of sent bytes on this path.
     */
    long sentBytes();

    /**
     * @return The number of received bytes on this path.
     */
    long recvBytes();

    /**
     * @return The number of bytes lost on this path.
     */
    long lostBytes();

    /**
     * @return The number of stream bytes retransmitted on this path.
     */
    long streamRetransBytes();

    /**
     * @return The current PMTU for the path.
     */
    long pmtu();

    /**
     * @return The most recent data delivery rate estimate in bytes/s.
     */
    long deliveryRate();
}
