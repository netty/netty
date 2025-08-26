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

import io.netty.util.concurrent.FastThreadLocal;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * Utility class to handle access to {@code quiche_send_info}.
 */
final class QuicheSendInfo {

    private static final FastThreadLocal<byte[]> IPV4_ARRAYS = new FastThreadLocal<byte[]>() {
        @Override
        protected byte[] initialValue() {
            return new byte[SockaddrIn.IPV4_ADDRESS_LENGTH];
        }
    };

    private static final FastThreadLocal<byte[]> IPV6_ARRAYS = new FastThreadLocal<byte[]>() {
        @Override
        protected byte[] initialValue() {
            return new byte[SockaddrIn.IPV6_ADDRESS_LENGTH];
        }
    };

    private static final byte[] TIMESPEC_ZEROOUT = new byte[Quiche.SIZEOF_TIMESPEC];

    private QuicheSendInfo() { }

    /**
     * Get the {@link InetSocketAddress} out of the {@code quiche_send_info} struct.
     *
     * @param memory the memory of {@code quiche_send_info}.
     * @return the address that was read.
     */
    @Nullable
    static InetSocketAddress getToAddress(ByteBuffer memory) {
        return getAddress(memory, Quiche.QUICHE_SEND_INFO_OFFSETOF_TO_LEN, Quiche.QUICHE_SEND_INFO_OFFSETOF_TO);
    }

    @Nullable
    static InetSocketAddress getFromAddress(ByteBuffer memory) {
       return getAddress(memory, Quiche.QUICHE_SEND_INFO_OFFSETOF_FROM_LEN, Quiche.QUICHE_SEND_INFO_OFFSETOF_FROM);
    }

    @Nullable
    private static InetSocketAddress getAddress(ByteBuffer memory, int lenOffset, int addressOffset) {
        int position = memory.position();
        try {
            long len = getLen(memory, position + lenOffset);

            memory.position(position + addressOffset);

            if (len == Quiche.SIZEOF_SOCKADDR_IN) {
                return SockaddrIn.getIPv4(memory, IPV4_ARRAYS.get());
            }
            assert len == Quiche.SIZEOF_SOCKADDR_IN6;
            return SockaddrIn.getIPv6(memory, IPV6_ARRAYS.get(), IPV4_ARRAYS.get());
        } finally {
            memory.position(position);
        }
    }

    private static long getLen(ByteBuffer memory, int index) {
        return Quiche.getPrimitiveValue(memory, index, Quiche.SIZEOF_SOCKLEN_T);
    }

    /**
     * Set the {@link InetSocketAddress} into the {@code quiche_send_info} struct.
     * <pre>
     *
     * typedef struct {
     *     // The local address the packet should be sent from.
     *     struct sockaddr_storage from;
     *     socklen_t from_len;
     *
     *     // The address the packet should be sent to.
     *     struct sockaddr_storage to;
     *     socklen_t to_len;
     *
     *     // The time to send the packet out.
     *     struct timespec at;
     * } quiche_send_info;
     * </pre>
     *
     * @param memory the memory of {@code quiche_send_info}.
     * @param from the {@link InetSocketAddress} to write into {@code quiche_send_info}.
     * @param to the {@link InetSocketAddress} to write into {@code quiche_send_info}.
     */
    static void setSendInfo(ByteBuffer memory, InetSocketAddress from, InetSocketAddress to) {
        int position = memory.position();
        try {
            setAddress(memory, Quiche.QUICHE_SEND_INFO_OFFSETOF_FROM, Quiche.QUICHE_SEND_INFO_OFFSETOF_FROM_LEN, from);
            setAddress(memory, Quiche.QUICHE_SEND_INFO_OFFSETOF_TO, Quiche.QUICHE_SEND_INFO_OFFSETOF_TO_LEN, to);
            // Zero out the timespec.
            memory.position(position + Quiche.QUICHE_SEND_INFO_OFFSETOF_AT);
            memory.put(TIMESPEC_ZEROOUT);
        } finally {
            memory.position(position);
        }
    }

    private static void setAddress(ByteBuffer memory, int addrOffset, int lenOffset, InetSocketAddress addr) {
        int position = memory.position();
        try {
            memory.position(position + addrOffset);
            int len = SockaddrIn.setAddress(memory, addr);
            Quiche.setPrimitiveValue(memory, position + lenOffset, Quiche.SIZEOF_SOCKLEN_T, len);
        } finally {
            memory.position(position);
        }
    }

    /**
     * Get the {@code timespec} from the {@code quiche_send_info} struct in nanos.
     * <pre>
     *
     * typedef struct {
     *     // The local address the packet should be sent from.
     *     struct sockaddr_storage from;
     *     socklen_t from_len;
     *
     *     // The address the packet should be sent to.
     *     struct sockaddr_storage to;
     *     socklen_t to_len;
     *
     *     // The time to send the packet out.
     *     struct timespec at;
     * } quiche_send_info;
     * </pre>
     *
     * @param memory the memory of {@code quiche_send_info}.
     */
    static long getAtNanos(ByteBuffer memory) {
        long sec = Quiche.getPrimitiveValue(memory, Quiche.QUICHE_SEND_INFO_OFFSETOF_AT +
                Quiche.TIMESPEC_OFFSETOF_TV_SEC, Quiche.SIZEOF_TIME_T);
        long nsec = Quiche.getPrimitiveValue(memory, Quiche.QUICHE_SEND_INFO_OFFSETOF_AT +
                Quiche.TIMESPEC_OFFSETOF_TV_SEC, Quiche.SIZEOF_LONG);
        return TimeUnit.SECONDS.toNanos(sec) + nsec;
    }

    /**
     * Returns {@code true} if both {@link ByteBuffer}s have the same {@code sockaddr_storage} stored.
     *
     * @param memory    the first {@link ByteBuffer} which holds a {@code quiche_send_info}.
     * @param memory2   the second {@link ByteBuffer} which holds a {@code quiche_send_info}.
     * @return          {@code true} if both {@link ByteBuffer}s have the same {@code sockaddr_storage} stored,
     *                  {@code false} otherwise.
     */
    static boolean isSameAddress(ByteBuffer memory, ByteBuffer memory2) {
        return Quiche.isSameAddress(memory, memory2, Quiche.QUICHE_SEND_INFO_OFFSETOF_TO);
    }
}
