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
import java.nio.ByteBuffer;
import java.util.function.Supplier;

final class QuicheQuicConnection {
    private static final int TOTAL_RECV_INFO_SIZE = Quiche.SIZEOF_QUICHE_RECV_INFO + Quiche.SIZEOF_SOCKADDR_STORAGE;
    private final QuicheQuicSslEngine engine;
    final long ssl;
    private ReferenceCounted refCnt;

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
    private final ByteBuf recvInfoBuffer;
    private final ByteBuf sendInfoBuffer;

    private boolean recvInfoFirst = true;
    private boolean sendInfoFirst = true;
    private final ByteBuffer recvInfoBuffer1;
    private final ByteBuffer recvInfoBuffer2;
    private final ByteBuffer sendInfoBuffer1;
    private final ByteBuffer sendInfoBuffer2;

    private long connection;

    QuicheQuicConnection(long connection, long ssl, QuicheQuicSslEngine engine, ReferenceCounted refCnt) {
        this.connection = connection;
        this.ssl = ssl;
        this.engine = engine;
        this.refCnt = refCnt;
        // TODO: Maybe cache these per thread as we only use them temporary within a limited scope.
        recvInfoBuffer = Quiche.allocateNativeOrder(2 * TOTAL_RECV_INFO_SIZE);
        sendInfoBuffer = Quiche.allocateNativeOrder(2 * Quiche.SIZEOF_QUICHE_SEND_INFO);

        // Let's memset the memory.
        recvInfoBuffer.setZero(0, recvInfoBuffer.capacity());
        sendInfoBuffer.setZero(0, sendInfoBuffer.capacity());

        recvInfoBuffer1 = recvInfoBuffer.nioBuffer(0, TOTAL_RECV_INFO_SIZE);
        recvInfoBuffer2 = recvInfoBuffer.nioBuffer(TOTAL_RECV_INFO_SIZE, TOTAL_RECV_INFO_SIZE);

        sendInfoBuffer1 = sendInfoBuffer.nioBuffer(0, Quiche.SIZEOF_QUICHE_SEND_INFO);
        sendInfoBuffer2 = sendInfoBuffer.nioBuffer(Quiche.SIZEOF_QUICHE_SEND_INFO, Quiche.SIZEOF_QUICHE_SEND_INFO);
        this.engine.connection = this;
    }

    synchronized void reattach(ReferenceCounted refCnt) {
        this.refCnt.release();
        this.refCnt = refCnt;
    }

    void free() {
        boolean release = false;
        synchronized (this) {
            if (connection != -1) {
                try {
                    Quiche.quiche_conn_free(connection);
                    release = true;
                    refCnt.release();
                } finally {
                    connection = -1;
                }
            }
        }
        if (release) {
            recvInfoBuffer.release();
            sendInfoBuffer.release();
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

    void initInfo(InetSocketAddress address) {
        assert connection != -1;
        assert recvInfoBuffer.refCnt() != 0;
        assert sendInfoBuffer.refCnt() != 0;

        // Fill both quiche_recv_info structs with the same address.
        QuicheRecvInfo.setRecvInfo(recvInfoBuffer1, address);
        QuicheRecvInfo.setRecvInfo(recvInfoBuffer2, address);

        // Fill both quiche_send_info structs with the same address.
        QuicheSendInfo.setSendInfo(sendInfoBuffer1, address);
        QuicheSendInfo.setSendInfo(sendInfoBuffer2, address);
    }

    ByteBuffer nextRecvInfo() {
        assert recvInfoBuffer.refCnt() != 0;
        recvInfoFirst = !recvInfoFirst;
        return recvInfoFirst ? recvInfoBuffer1 : recvInfoBuffer2;
    }

    ByteBuffer nextSendInfo() {
        assert sendInfoBuffer.refCnt() != 0;
        sendInfoFirst = !sendInfoFirst;
        return sendInfoFirst ? sendInfoBuffer1 : sendInfoBuffer2;
    }

    boolean isSendInfoChanged() {
        assert sendInfoBuffer.refCnt() != 0;
        return !QuicheSendInfo.isSameAddress(sendInfoBuffer1, sendInfoBuffer2);
    }

    boolean isRecvInfoChanged() {
        assert recvInfoBuffer.refCnt() != 0;
        return !QuicheRecvInfo.isSameAddress(recvInfoBuffer1, recvInfoBuffer2);
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
