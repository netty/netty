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

import io.netty.util.concurrent.FastThreadLocal;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

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

    private QuicheSendInfo() { }

    /**
     * Get the {@link InetSocketAddress} out of the {@code quiche_send_info} struct.
     *
     * @param memory the memory of {@code quiche_send_info}.
     * @return the address that was read.
     */
    static InetSocketAddress getAddress(ByteBuffer memory) {
        int position = memory.position();
        try {
            long len = getLen(memory, position + Quiche.QUICHE_SEND_INFO_OFFSETOF_TO_LEN);

            memory.position(position + Quiche.QUICHE_SEND_INFO_OFFSETOF_TO);

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
        switch (Quiche.SIZEOF_SOCKLEN_T) {
            case 1:
                return memory.get(index);
            case 2:
                return memory.getShort(index);
            case 4:
                return memory.getInt(index);
            case 8:
                return memory.getLong(index);
            default:
                throw new IllegalStateException();
        }
    }

    /**
     * Set the {@link InetSocketAddress} into the {@code quiche_send_info} struct.
     * <pre>
     *
     * typedef struct {
     *     // The address the packet should be sent to.
     *     struct sockaddr_storage to;
     *     socklen_t to_len;
     * } quiche_send_info;
     * </pre>
     *
     * @param memory the memory of {@code quiche_send_info}.
     * @param address the {@link InetSocketAddress} to write into {@code quiche_send_info}.
     */
    static void setSendInfo(ByteBuffer memory, InetSocketAddress address) {
        int position = memory.position();
        int sockaddrPosition = position + Quiche.QUICHE_SEND_INFO_OFFSETOF_TO;
        try {
            memory.position(sockaddrPosition);
            int len = SockaddrIn.setAddress(memory, address);
            switch (Quiche.SIZEOF_SOCKLEN_T) {
                case 1:
                    memory.put(position + Quiche.QUICHE_SEND_INFO_OFFSETOF_TO_LEN, (byte) len);
                    break;
                case 2:
                    memory.putShort(position + Quiche.QUICHE_SEND_INFO_OFFSETOF_TO_LEN, (short) len);
                    break;
                case 4:
                    memory.putInt(position + Quiche.QUICHE_SEND_INFO_OFFSETOF_TO_LEN, len);
                    break;
                case 8:
                    memory.putLong(position + Quiche.QUICHE_SEND_INFO_OFFSETOF_TO_LEN, len);
                    break;
                default:
                    throw new IllegalStateException();
            }
        } finally {
            memory.position(position);
        }
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
        long address1 = Quiche.memoryAddressWithPosition(memory) + Quiche.QUICHE_SEND_INFO_OFFSETOF_TO;
        long address2 = Quiche.memoryAddressWithPosition(memory2) + Quiche.QUICHE_SEND_INFO_OFFSETOF_TO;
        return SockaddrIn.cmp(address1, address2) == 0;
    }
}
