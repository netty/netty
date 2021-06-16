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

import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCounted;

import java.net.InetSocketAddress;
import java.util.function.Supplier;

final class QuicheQuicConnection {
    private static final int TOTAL_RECV_INFO_SIZE = Quiche.SIZEOF_QUICHE_RECV_INFO + Quiche.SIZEOF_SOCKADDR_STORAGE;
    private static final int QUICHE_SEND_INFOS_OFFSET = 2 * TOTAL_RECV_INFO_SIZE;
    private final ReferenceCounted refCnt;
    private final QuicheQuicSslEngine engine;

    // This block of memory is used to store the following structs (in this order):
    // - quiche_recv_info
    // - sockaddr_storage
    // - quiche_recv_info
    // - sockaddr_storage
    // - quiche_send_info
    // - quiche_send_info
    //
    // We need to have every stored 2 times as we need to check if the last sockaddr has changed between
    // quiche_conn_recv and quiche_conn_send calls. If this happens we know a QUIC connection migration did happen.
    private final ByteBuf infoBuffer;
    private long connection;

    QuicheQuicConnection(long connection, QuicheQuicSslEngine engine, ReferenceCounted refCnt) {
        this.connection = connection;
        this.engine = engine;
        this.refCnt = refCnt;
        // TODO: Maybe cache these per thread as we only use them temporary within a limited scope.
        infoBuffer = Quiche.allocateNativeOrder(QUICHE_SEND_INFOS_OFFSET +
                2 * Quiche.SIZEOF_QUICHE_SEND_INFO);
        // Let's memset the memory.
        infoBuffer.setZero(0, infoBuffer.capacity());
    }

    void free() {
        boolean release = false;
        synchronized (this) {
            if (connection != -1) {
                try {
                    Quiche.quiche_conn_free(connection);
                    release = true;
                } finally {
                    connection = -1;
                }
            }
        }
        if (release) {
            refCnt.release();
            infoBuffer.release();
        }
    }

    QuicConnectionAddress sourceId() {
        return connectionId(() -> Quiche.quiche_conn_source_id(connection));
    }

    QuicConnectionAddress destinationId() {
        return connectionId(() -> Quiche.quiche_conn_destination_id(connection));
    }

    QuicConnectionAddress connectionId(Supplier<byte[]> idSupplier) {
        final byte[] id;
        synchronized (this) {
            if (connection == -1) {
                return null;
            }
            id = idSupplier.get();
        }
        return id == null ? null : new QuicConnectionAddress(id);
    }

    QuicheQuicSslEngine engine() {
        return engine;
    }

    long address() {
        assert connection != -1;
        return connection;
    }

    private long sendInfosAddress() {
        return infoBuffer.memoryAddress() + QUICHE_SEND_INFOS_OFFSET;
    }

    void initInfoAddresses(InetSocketAddress address) {
        // Fill both quiche_recv_info structs with the same address.
        QuicheRecvInfo.write(infoBuffer.memoryAddress(), address);
        QuicheRecvInfo.write(infoBuffer.memoryAddress() + TOTAL_RECV_INFO_SIZE, address);

        // Fill both quiche_send_info structs with the same address.
        long sendInfosAddress = sendInfosAddress();
        QuicheSendInfo.write(sendInfosAddress, address);
        QuicheSendInfo.write(sendInfosAddress + Quiche.SIZEOF_QUICHE_SEND_INFO, address);
    }

    long recvInfoAddress() {
        return infoBuffer.memoryAddress();
    }

    long sendInfoAddress() {
        return sendInfosAddress();
    }

    long nextRecvInfoAddress(long previousRecvInfoAddress) {
        long memoryAddress = infoBuffer.memoryAddress();
        if (memoryAddress == previousRecvInfoAddress) {
            return memoryAddress + TOTAL_RECV_INFO_SIZE;
        }
        return memoryAddress;
    }

    long nextSendInfoAddress(long previousSendInfoAddress) {
        long memoryAddress = sendInfosAddress();
        if (memoryAddress == previousSendInfoAddress) {
            return memoryAddress + Quiche.SIZEOF_QUICHE_SEND_INFO;
        }
        return memoryAddress;
    }

    boolean isClosed() {
        assert connection != -1;
        return Quiche.quiche_conn_is_closed(connection);
    }

    // Let's override finalize() as we want to ensure we never leak memory even if the user will miss to close
    // Channel that uses this connection and just let it get GC'ed
    @Override
    protected void finalize() throws Throwable {
        try {
            free();
        } finally {
            super.finalize();
        }
    }

}
