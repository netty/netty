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

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;


/**
 * Utility class to handle access to {@code quiche_recv_info}.
 */
final class QuicheRecvInfo {

    private QuicheRecvInfo() { }

    /**
     * Set the {@link InetSocketAddress} into the {@code quiche_recv_info} struct.
     *
     * <pre>
     * typedef struct {
     *     struct sockaddr *from;
     *     socklen_t from_len;
     *     struct sockaddr *to;
     *     socklen_t to_len;
     * } quiche_recv_info;
     * </pre>
     *
     * @param memory the memory of {@code quiche_recv_info}.
     * @param from the {@link InetSocketAddress} to write into {@code quiche_recv_info}.
     * @param to the {@link InetSocketAddress} to write into {@code quiche_recv_info}.
     */
    static void setRecvInfo(ByteBuffer memory, InetSocketAddress from, InetSocketAddress to) {
        int position = memory.position();
        try {
            setAddress(memory, Quiche.SIZEOF_QUICHE_RECV_INFO, Quiche.QUICHE_RECV_INFO_OFFSETOF_FROM,
                    Quiche.QUICHE_RECV_INFO_OFFSETOF_FROM_LEN, from);
            setAddress(memory, Quiche.SIZEOF_QUICHE_RECV_INFO + Quiche.SIZEOF_SOCKADDR_STORAGE,
                    Quiche.QUICHE_RECV_INFO_OFFSETOF_TO, Quiche.QUICHE_RECV_INFO_OFFSETOF_TO_LEN, to);
        } finally {
            memory.position(position);
        }
    }

    private static void setAddress(ByteBuffer memory, int socketAddressOffset, int addrOffset, int lenOffset,
                                   InetSocketAddress address) {
        int position = memory.position();
        try {
            int sockaddrPosition = position + socketAddressOffset;
            memory.position(sockaddrPosition);
            long sockaddrMemoryAddress = Quiche.memoryAddressWithPosition(memory);
            int len = SockaddrIn.setAddress(memory, address);
            if (Quiche.SIZEOF_SIZE_T == 4) {
                memory.putInt(position + addrOffset, (int) sockaddrMemoryAddress);
            } else {
                memory.putLong(position + addrOffset, sockaddrMemoryAddress);
            }
            Quiche.setPrimitiveValue(memory, position + lenOffset, Quiche.SIZEOF_SOCKLEN_T, len);
        } finally {
            memory.position(position);
        }
    }
}
