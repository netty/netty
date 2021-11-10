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
package io.netty.incubator.codec.quic;

/**
 * Statistics about the {@code QUIC} connection.
 */
public interface QuicConnectionStats {
    /**
     * @return The number of QUIC packets received on the connection.
     */
    long recv();

    /**
     * @return The number of QUIC packets sent on this connection.
     */
    long sent();

    /**
     * @return The number of QUIC packets that were lost.
     */
    long lost();

    /**
     * @return The estimated round-trip time of the connection in nanoseconds.
     */
    long rttNanos();

    /**
     * @return The size of the connection's congestion window in bytes.
     */
    long congestionWindow();

    /**
     * @return The estimated data delivery rate in bytes/s.
     */
    long deliveryRate();
}
