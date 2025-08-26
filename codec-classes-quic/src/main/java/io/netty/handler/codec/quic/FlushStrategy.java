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
package io.netty.handler.codec.quic;

import io.netty.util.internal.ObjectUtil;

/**
 * Allows to configure a strategy for when flushes should be happening.
 */
public interface FlushStrategy {

    /**
     * Default {@link FlushStrategy} implementation.
     */
    FlushStrategy DEFAULT = afterNumBytes(20 * Quic.MAX_DATAGRAM_SIZE);

    /**
     * Returns {@code true} if a flush should happen now, {@code false} otherwise.
     *
     * @param numPackets    the number of packets that were written since the last flush.
     * @param numBytes      the number of bytes that were written since the last flush.
     * @return              {@code true} if a flush should be done now, {@code false} otherwise.
     */
    boolean shouldFlushNow(int numPackets, int numBytes);

    /**
     * Implementation that flushes after a number of bytes.
     *
     * @param bytes the number of bytes after which we should issue a flush.
     * @return the {@link FlushStrategy}.
     */
    static FlushStrategy afterNumBytes(int bytes) {
        ObjectUtil.checkPositive(bytes, "bytes");
        return (numPackets, numBytes) -> numBytes > bytes;
    }

    /**
     * Implementation that flushes after a number of packets.
     *
     * @param packets the number of packets after which we should issue a flush.
     * @return the {@link FlushStrategy}.
     */
    static FlushStrategy afterNumPackets(int packets) {
        ObjectUtil.checkPositive(packets, "packets");
        return (numPackets, numBytes) -> numPackets > packets;
    }
}
