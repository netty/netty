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

import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCounted;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class QuicheQuicConnection {
    private static final int TOTAL_RECV_INFO_SIZE = Quiche.SIZEOF_QUICHE_RECV_INFO +
            Quiche.SIZEOF_SOCKADDR_STORAGE + Quiche.SIZEOF_SOCKADDR_STORAGE;
    private static final ResourceLeakDetector<QuicheQuicConnection> leakDetector =
            ResourceLeakDetectorFactory.instance().newResourceLeakDetector(QuicheQuicConnection.class);
    private final QuicheQuicSslEngine engine;

    private final ResourceLeakTracker<QuicheQuicConnection> leakTracker;

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

    private boolean sendInfoFirst = true;
    private final ByteBuffer recvInfoBuffer1;
    private final ByteBuffer sendInfoBuffer1;
    private final ByteBuffer sendInfoBuffer2;

    private long connection;

    QuicheQuicConnection(long connection, long ssl, QuicheQuicSslEngine engine, ReferenceCounted refCnt) {
        this.connection = connection;
        this.ssl = ssl;
        this.engine = engine;
        this.refCnt = refCnt;
        // TODO: Maybe cache these per thread as we only use them temporary within a limited scope.
        recvInfoBuffer = Quiche.allocateNativeOrder(TOTAL_RECV_INFO_SIZE);
        sendInfoBuffer = Quiche.allocateNativeOrder(2 * Quiche.SIZEOF_QUICHE_SEND_INFO);

        // Let's memset the memory.
        recvInfoBuffer.setZero(0, recvInfoBuffer.capacity());
        sendInfoBuffer.setZero(0, sendInfoBuffer.capacity());

        recvInfoBuffer1 = recvInfoBuffer.nioBuffer(0, TOTAL_RECV_INFO_SIZE);
        sendInfoBuffer1 = sendInfoBuffer.nioBuffer(0, Quiche.SIZEOF_QUICHE_SEND_INFO);
        sendInfoBuffer2 = sendInfoBuffer.nioBuffer(Quiche.SIZEOF_QUICHE_SEND_INFO, Quiche.SIZEOF_QUICHE_SEND_INFO);
        this.engine.connection = this;
        leakTracker = leakDetector.track(this);
    }

    synchronized void reattach(ReferenceCounted refCnt) {
        this.refCnt.release();
        this.refCnt = refCnt;
    }

    void free() {
        free(true);
    }

    boolean isFreed() {
        return connection == -1;
    }

    private void free(boolean closeLeakTracker) {
        boolean release = false;
        synchronized (this) {
            if (connection != -1) {
                try {
                    BoringSSL.SSL_cleanup(ssl);
                    Quiche.quiche_conn_free(connection);
                    engine.ctx.remove(engine);
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
            if (closeLeakTracker && leakTracker != null) {
                leakTracker.close(this);
            }
        }
    }

    @Nullable
    Runnable sslTask() {
        final Runnable task;
        synchronized (this) {
            if (connection != -1) {
                task = BoringSSL.SSL_getTask(ssl);
            } else {
                task = null;
            }
        }
        if (task == null) {
            return null;
        }

        return () -> {
            if (connection == -1) {
                return;
            }

            task.run();
        };
    }

    @Nullable
    QuicConnectionAddress sourceId() {
        return connectionId(() -> Quiche.quiche_conn_source_id(connection));
    }

    @Nullable
    QuicConnectionAddress destinationId() {
        return connectionId(() -> Quiche.quiche_conn_destination_id(connection));
    }

    @Nullable
    QuicConnectionAddress connectionId(Supplier<byte[]> idSupplier) {
        final byte[] id;
        synchronized (this) {
            if (connection == -1) {
                return null;
            }
            id = idSupplier.get();
        }
        return id == null ? QuicConnectionAddress.NULL_LEN : new QuicConnectionAddress(id);
    }

    @Nullable
    QuicheQuicTransportParameters peerParameters() {
        final long[] ret;
        synchronized (this) {
            if (connection == -1) {
                return null;
            }
            ret = Quiche.quiche_conn_peer_transport_params(connection);
        }
        if (ret == null) {
            return null;
        }
        return new QuicheQuicTransportParameters(ret);
    }

    QuicheQuicSslEngine engine() {
        return engine;
    }

    long address() {
        assert connection != -1;
        return connection;
    }

    void init(InetSocketAddress local, InetSocketAddress remote, Consumer<String> sniSelectedCallback) {
        assert connection != -1;
        assert recvInfoBuffer.refCnt() != 0;
        assert sendInfoBuffer.refCnt() != 0;

        // Fill quiche_recv_info struct with the addresses.
        QuicheRecvInfo.setRecvInfo(recvInfoBuffer1, remote, local);

        // Fill both quiche_send_info structs with the same addresses.
        QuicheSendInfo.setSendInfo(sendInfoBuffer1, local, remote);
        QuicheSendInfo.setSendInfo(sendInfoBuffer2, local, remote);
        engine.sniSelectedCallback = sniSelectedCallback;
    }

    ByteBuffer nextRecvInfo() {
        assert recvInfoBuffer.refCnt() != 0;
        return recvInfoBuffer1;
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

    boolean isClosed() {
        assert connection != -1;
        return Quiche.quiche_conn_is_closed(connection);
    }

    // Let's override finalize() as we want to ensure we never leak memory even if the user will miss to close
    // Channel that uses this connection and just let it get GC'ed
    @Override
    protected void finalize() throws Throwable {
        try {
            free(false);
        } finally {
            super.finalize();
        }
    }
}
