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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.collection.LongObjectHashMap;
import io.netty.util.collection.LongObjectMap;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ConnectionPendingException;
import java.util.concurrent.TimeUnit;

final class QuicheQuicChannel extends AbstractChannel implements QuicChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(false);
    private final long[] readableStreams = new long[1024];
    private final long[] writableStreams = new long[1024];
    // TODO: Consider using quiche_conn_stream_init_application_data(...) and quiche_conn_stream_application_data(...)
    private final LongObjectMap<QuicheQuicStreamChannel> streams = new LongObjectHashMap<>();
    private final InetSocketAddress remote;
    private final ChannelConfig config;
    private final boolean server;

    private final ChannelFutureListener flushListener = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture channelFuture) {
            // Try to flush more streams that have outstanding writes.
            if (flushStreams()) {
                // TODO: Maybe optimize this
                parent().flush();
            }
        }
    };

    private final Runnable timeout = new Runnable() {
        @Override
        public void run() {
            if (connAddr != -1) {
                // Notify quiche there was a timeout.
                Quiche.quiche_conn_on_timeout(connAddr);

                writeAndFlushEgress();

                if (!closeAllIfConnectionClosed()) {
                    // The connection is alive, reschedule.
                    timeoutFuture = null;
                    scheduleTimeout();
                }
            }
        }
    };
    private Future<?> timeoutFuture;
    private long connAddr;
    private boolean fireChannelReadCompletePending;
    private boolean writeEgressNeeded;
    private ByteBuf finBuffer;
    private ChannelPromise connectPromise;
    private byte[] connectId;

    private static final int CLOSED = 0;
    private static final int OPEN = 1;
    private static final int ACTIVE = 2;
    private volatile int state;
    private volatile String traceId;

    private QuicheQuicChannel(Channel parent, boolean server, long connAddr, String traceId,
                      InetSocketAddress remote) {
        super(parent);
        config = new DefaultChannelConfig(this);
        this.server = server;
        if (server) {
            state = ACTIVE;
        } else {
            state = OPEN;
        }
        this.remote = remote;
        this.connAddr = connAddr;
        this.traceId = traceId;
    }

    static QuicheQuicChannel forClient(Channel parent) {
        return new QuicheQuicChannel(parent, false, -1, null, (InetSocketAddress) parent.remoteAddress());
    }

    static QuicheQuicChannel forServer(Channel parent, long connAddr, String traceId, InetSocketAddress remote) {
        return new QuicheQuicChannel(parent, true, connAddr, traceId, remote);
    }

    ByteBuffer connect(long configAddr) throws Exception {
        assert this.connAddr == -1;
        assert this.traceId == null;

        ByteBuf idBuffer = alloc().directBuffer(connectId.length).writeBytes(connectId);
        try {
            long connection = Quiche.quiche_connect(null, idBuffer.memoryAddress() + idBuffer.readerIndex(),
                    idBuffer.readableBytes(), configAddr);
            if (connection == -1) {
                ConnectException connectException = new ConnectException();
                ChannelPromise promise = connectPromise;
                if (promise != null) {
                    connectPromise = null;
                    promise.tryFailure(connectException);
                }
                throw connectException;
            }
            this.traceId = Quiche.traceId(connection, idBuffer);
            this.connAddr = connection;
            writeEgressNeeded = true;
            return ByteBuffer.wrap(connectId);
        } finally {
            idBuffer.release();
        }
    }

    private boolean closeAllIfConnectionClosed() {
        if (Quiche.quiche_conn_is_closed(connAddr)) {
            forceClose();
            state = CLOSED;
            return true;
        }
        return false;
    }

    void forceClose() {
        unsafe().close(voidPromise());
        Quiche.quiche_conn_free(connAddr);
        connAddr = -1;

        closeStreams();
        if (finBuffer != null) {
            finBuffer.release();
            finBuffer = null;
        }
        state = CLOSED;
    }

    private void scheduleTimeout() {
        if (timeoutFuture != null) {
            timeoutFuture.cancel(false);
        }
        if (isConnDestroyed()) {
            return;
        }
        long nanos = Quiche.quiche_conn_timeout_as_nanos(connAddr);
        timeoutFuture = eventLoop().schedule(timeout,
                nanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public Future<QuicStreamChannel> createStream(QuicStreamType type, ChannelHandler handler) {
        return createStream(type, handler, eventLoop().newPromise());
    }

    @Override
    public Future<QuicStreamChannel> createStream(QuicStreamType type, ChannelHandler handler,
                                                  Promise<QuicStreamChannel> promise) {
        if (eventLoop().inEventLoop()) {
            ((QuicChannelUnsafe) unsafe()).connectStream(type, handler, promise);
        } else {
            eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    ((QuicChannelUnsafe) unsafe()).connectStream(type, handler, promise);
                }
            });
        }
        return promise;
    }

    @Override
    public byte[] applicationProtocol() {
        if (isConnDestroyed()) {
            return null;
        }
        return Quiche.quiche_conn_application_proto(connAddr);
    }

    @Override
    public String toString() {
        String traceId = this.traceId;
        if (traceId == null) {
            return "()" + super.toString();
        } else {
            return '(' + traceId + ')' + super.toString();
        }
    }

    @Override
    protected AbstractUnsafe newUnsafe() {
        return new QuicChannelUnsafe();
    }

    @Override
    protected boolean isCompatible(EventLoop eventLoop) {
        return parent().eventLoop() == eventLoop;
    }

    @Override
    protected SocketAddress localAddress0() {
        return parent().localAddress();
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return remote;
    }

    @Override
    protected void doBind(SocketAddress socketAddress) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void doDisconnect() throws Exception {
        doClose();
    }

    @Override
    protected void doClose() throws Exception {
        state = CLOSED;
        Quiche.throwIfError(Quiche.quiche_conn_close(connectionAddressChecked(), false, 0,
                Unpooled.EMPTY_BUFFER.memoryAddress(), 0));
    }

    @Override
    protected void doBeginRead() throws Exception {
        // TODO: handle this
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer channelOutboundBuffer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelConfig config() {
        return config;
    }

    @Override
    public boolean isOpen() {
        return state >= OPEN;
    }

    @Override
    public boolean isActive() {
        return state == ACTIVE;
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    boolean freeIfClosed() {
        if (isConnDestroyed()) {
            return true;
        }
        if (closeAllIfConnectionClosed()) {
            return true;
        } else {
            // Check for all streams if there is one we need to remove from the internal map.
            // TODO: This may not be the most efficient way of handling this.
            int num = Quiche.quiche_conn_readable(connAddr, readableStreams);
            for (int i = 0; i < num; i++) {
                long stream = readableStreams[i];
                if (Quiche.quiche_conn_stream_finished(connAddr, stream)) {
                    streamClosed(stream);
                }
            }
        }
        return false;
    }

    private void closeStreams() {
        for (QuicheQuicStreamChannel stream: streams.values()) {
            stream.unsafe().close(voidPromise());
        }
        streams.clear();
    }

    void streamClosed(long streamId) {
        //streams.remove(streamId);
    }

    boolean isStreamLocalCreated(long streamId) {
        return (streamId & 0x1) == (server ? 1 : 0);
    }

    QuicStreamType streamType(long streamId) {
        return (streamId & 0x2) == 0 ? QuicStreamType.BIDIRECTIONAL : QuicStreamType.UNIDIRECTIONAL;
    }

    boolean isStreamFinished(long streamId) {
        if (isConnDestroyed()) {
            return true;
        }
        return Quiche.quiche_conn_stream_finished(connAddr, streamId);
    }

    void streamShutdownRead(long streamId, ChannelPromise promise) {
        streamShutdown0(streamId, true, false, 0, promise);
    }

    void streamShutdownWrite(long streamId, ChannelPromise promise) {
        streamShutdown0(streamId, false, true, 0, promise);
    }

    void streamShutdownReadAndWrite(long streamId, ChannelPromise promise) {
        streamShutdown0(streamId, true, true, 0, promise);
    }

    private void streamShutdown0(long streamId, boolean read, boolean write, int err, ChannelPromise promise) {
        final long connectionAddress;
        try {
            connectionAddress = connectionAddressChecked();
        } catch (ClosedChannelException e) {
            promise.setFailure(e);
            return;
        }
        int res = 0;
        if (read) {
            res |= Quiche.quiche_conn_stream_shutdown(connectionAddress, streamId, Quiche.QUICHE_SHUTDOWN_READ, err);
        }
        if (write) {
            res |= Quiche.quiche_conn_stream_shutdown(connectionAddress, streamId, Quiche.QUICHE_SHUTDOWN_WRITE, err);
        }
        handleWriteEgress();
        Quiche.notifyPromise(res, promise);
    }

    boolean streamSendMultiple(long streamId, ByteBufAllocator allocator, ChannelOutboundBuffer streamOutboundBuffer)
            throws Exception {
        boolean sendSomething = false;
        try {
            for (;;) {
                ByteBuf buffer = (ByteBuf) streamOutboundBuffer.current();
                if (buffer == null) {
                    break;
                }
                final int res;
                if (!buffer.hasMemoryAddress()) {
                    ByteBuf tmpBuffer = allocator.directBuffer(buffer.readableBytes());
                    try {
                        tmpBuffer.writeBytes(buffer, buffer.readerIndex(), buffer.readableBytes());
                        res = streamSend(streamId, tmpBuffer, false);
                    } finally {
                        tmpBuffer.release();
                    }
                } else {
                    res = streamSend(streamId, buffer, false);
                }

                if (Quiche.throwIfError(res) || res == 0) {
                    // stream has no capacity left stop trying to send.
                    return false;
                }
                streamOutboundBuffer.removeBytes(res);
                sendSomething = true;
            }
            return true;
        } finally {
            if (sendSomething) {
                handleWriteEgress();
            }
        }
    }

    void streamClose(long streamId) throws Exception {
        try {
            // Just write an empty buffer and set fin to true.
            Quiche.throwIfError(streamSend(streamId, Unpooled.EMPTY_BUFFER, true));
        } finally {
            handleWriteEgress();
        }
    }

    private long connectionAddressChecked() throws ClosedChannelException {
        if (isConnDestroyed()) {
            throw new ClosedChannelException();
        }
        return connAddr;
    }

    private int streamSend(long streamId, ByteBuf buffer, boolean fin) throws Exception {
        return Quiche.quiche_conn_stream_send(connectionAddressChecked(), streamId,
                buffer.memoryAddress() + buffer.readerIndex(), buffer.readableBytes(), fin);
    }

    boolean streamRecv(long streamId, ByteBuf buffer) throws Exception {
        if (finBuffer == null) {
            finBuffer = alloc().directBuffer(1);
        }
        int writerIndex = buffer.writerIndex();
        long memoryAddress = buffer.memoryAddress();
        int recvLen = Quiche.quiche_conn_stream_recv(connectionAddressChecked(), streamId,
                memoryAddress + writerIndex, buffer.writableBytes(), finBuffer.memoryAddress());
        if (!Quiche.throwIfError(recvLen)) {
            buffer.setIndex(0, writerIndex + recvLen);
        }
        return finBuffer.getBoolean(0);
    }

    private void handleWriteEgress() {
        writeEgressNeeded = true;
        if (!fireChannelReadCompletePending) {
            writeAndFlushEgress();
        }
    }

    /**
     * Receive some data on a QUIC connection.
     */
    void recv(ByteBuf buffer) {
        fireChannelReadCompletePending |= ((QuicChannelUnsafe) unsafe()).recvForConnection(buffer);
    }

    boolean flushStreams() {
        return flushStreams0() && writeEgress();
    }

    private boolean flushStreams0() {
        if (isConnDestroyed()) {
            return false;
        }

        if (Quiche.quiche_conn_is_established(connAddr) ||
                Quiche.quiche_conn_is_in_early_data(connAddr)) {
            int writable = Quiche.quiche_conn_writable(connAddr, writableStreams);

            for (int i = 0; i < writable; i++) {
                long stream = writableStreams[writable];
                QuicheQuicStreamChannel streamChannel = streams.get(stream);
                if (streamChannel != null) {
                    streamChannel.forceFlush();
                }
            }
            return writable > 0;
        }
        return false;
    }

    boolean handleChannelReadComplete() {
        if (isConnDestroyed()) {
            return false;
        }
        if (fireChannelReadCompletePending) {
            fireChannelReadCompletePending = false;
        }

        // Try flush more streams that had some flushes pending.
        flushStreams0();

        return writeEgress();
    }

    private boolean isConnDestroyed() {
        return connAddr == -1;
    }

    private void writeAndFlushEgress() {
        if (writeEgress()) {
            parent().flush();
        }
    }

    boolean writeEgress() {
        if (isConnDestroyed() || !writeEgressNeeded) {
            return false;
        }
        writeEgressNeeded = false;
        ChannelFuture lastFuture = null;

        // Use the datagram size that was advertised by the remote peer, or if none was fallback to some safe default.
        int len = Quiche.quiche_conn_dgram_max_writable_len(connAddr);
        if (len <= 0) {
            len = Quic.MAX_DATAGRAM_SIZE;
        }

        for (;;) {
            ByteBuf out = alloc().directBuffer(len);
            int writerIndex = out.writerIndex();
            int written = Quiche.quiche_conn_send(connAddr, out.memoryAddress() + writerIndex, out.writableBytes());

            try {
                if (Quiche.throwIfError(written)) {
                    out.release();
                    break;
                }
            } catch (Exception e) {
                out.release();
                pipeline().fireExceptionCaught(e);
                break;
            }

            out.writerIndex(writerIndex + written);
            lastFuture = parent().write(new DatagramPacket(out, remote));
        }
        if (lastFuture != null) {
            lastFuture.addListener(flushListener);
            // Schedule timeout.
            // See https://docs.rs/quiche/0.6.0/quiche/#generating-outgoing-packets
            scheduleTimeout();
            return true;
        }
        return false;
    }

    private final class QuicChannelUnsafe extends AbstractChannel.AbstractUnsafe {
        // See https://tools.ietf.org/html/draft-ietf-quic-transport-32#section-2.1
        private long nextBidirectionalStreamId = server ? 1 : 0;
        private long nextUnidirectionalStreamId = server ? 3 : 2;

        private long nextStreamId(boolean bidirectional) {
            if (bidirectional) {
                long stream = nextBidirectionalStreamId;
                nextBidirectionalStreamId += 4;
                return stream;
            } else {
                long stream = nextUnidirectionalStreamId;
                nextUnidirectionalStreamId += 4;
                return stream;
            }
        }

        void connectStream(QuicStreamType type, ChannelHandler handler,
                           Promise<QuicStreamChannel> promise) {
            long streamId = nextStreamId(type == QuicStreamType.BIDIRECTIONAL);
            try {
                // TODO: We could make this a bit more efficient by waiting until we have some data to send.
                streamSend(streamId, Unpooled.EMPTY_BUFFER, false);
            } catch (Exception e) {
                promise.setFailure(e);
                return;
            }
            QuicheQuicStreamChannel streamChannel = addNewStreamChannel(streamId);
            if (handler != null) {
                streamChannel.pipeline().addLast(handler);
            }
            eventLoop().register(streamChannel).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture f) {
                    if (f.isSuccess()) {
                        promise.setSuccess(streamChannel);
                    } else {
                        promise.setFailure(f.cause());
                        streams.remove(streamId);
                    }
                }
            });
        }

        @Override
        public void connect(SocketAddress remote, SocketAddress local, ChannelPromise channelPromise) {
            assert eventLoop().inEventLoop();
            if (server) {
                channelPromise.setFailure(new UnsupportedOperationException());
                return;
            }

            if (connectPromise != null) {
                channelPromise.setFailure(new ConnectionPendingException());
                return;
            }
            if (remote instanceof QuicConnectionIdAddress) {
                if (connAddr != -1) {
                    channelPromise.setFailure(new AlreadyConnectedException());
                    return;
                }

                connectPromise = channelPromise;
                connectId = ((QuicConnectionIdAddress) remote).connId;

                parent().connect(new QuicheQuicChannelAddress(QuicheQuicChannel.this));
                return;
            }

            channelPromise.setFailure(new UnsupportedOperationException());
        }

        boolean recvForConnection(ByteBuf buffer) {
            if (isConnDestroyed()) {
                return false;
            }

            ByteBuf tmpBuffer = null;
            // We need to make a copy if the buffer is read only as recv(...) may modify the input buffer as well.
            // See https://docs.rs/quiche/0.6.0/quiche/struct.Connection.html#method.recv
            if (buffer.isReadOnly() || !buffer.hasMemoryAddress()) {
                buffer = tmpBuffer = alloc().directBuffer(buffer.readableBytes()).writeBytes(buffer);
            }
            int bufferReadable = buffer.readableBytes();
            int bufferReaderIndex = buffer.readerIndex();
            long memoryAddress = buffer.memoryAddress() + bufferReaderIndex;
            boolean fireChannelRead = false;
            writeEgressNeeded = true;
            try {
                do  {
                    // Call quiche_conn_recv(...) until we consumed all bytes or we did receive some error.
                    int res = Quiche.quiche_conn_recv(connAddr, memoryAddress, bufferReadable);
                    boolean done;
                    try {
                        done = Quiche.throwIfError(res);
                    } catch (Exception e) {
                        pipeline().fireExceptionCaught(e);
                        done = true;
                    }

                    // Notify if we are a client and the connection was not established before.
                    if (notifyConnectPromise()) {
                        break;
                    }

                    fireChannelRead |= handleReadableStreams();
                    if (done) {
                        break;
                    } else {
                        memoryAddress += res;
                        bufferReadable -= res;
                    }
                } while (bufferReadable > 0);
                return fireChannelRead;
            } finally {
                buffer.skipBytes((int) (memoryAddress - buffer.memoryAddress()));
                if (tmpBuffer != null) {
                    tmpBuffer.release();
                }
            }
        }

        private boolean notifyConnectPromise() {
            if (!server) {
                if (connectPromise != null && Quiche.quiche_conn_is_established(connAddr)) {
                    ChannelPromise promise = connectPromise;
                    connectPromise = null;
                    state = ACTIVE;
                    boolean promiseSet = promise.trySuccess();
                    pipeline().fireChannelActive();
                    if (!promiseSet) {
                        this.close(this.voidPromise());
                        return true;
                    }
                }
            }
            return false;
        }

        // TODO: Improve the way how we handle channelReadComplete() for the streams to batch as many
        //       fireChannelRead(...) as possible per stream
        private boolean handleReadableStreams() {
            if (isConnDestroyed()) {
                return false;
            }
            boolean fireChannelRead = false;
            if (Quiche.quiche_conn_is_established(connAddr) || Quiche.quiche_conn_is_in_early_data(connAddr)) {
                int readable = Quiche.quiche_conn_readable(connAddr, readableStreams);
                for (int i = 0; i < readable; i++) {
                    long streamId = readableStreams[i];
                    QuicheQuicStreamChannel streamChannel = streams.get(streamId);
                    if (streamChannel == null) {
                        fireChannelRead = true;
                        streamChannel = addNewStreamChannel(streamId);
                        pipeline().fireChannelRead(streamChannel);
                    } else {
                        streamChannel.recvIfPending();
                    }
                }
            }
            return fireChannelRead;
        }

        private QuicheQuicStreamChannel addNewStreamChannel(long streamId) {
            QuicheQuicStreamChannel streamChannel = new QuicheQuicStreamChannel(
                    QuicheQuicChannel.this, streamId);
            streams.put(streamId, streamChannel);
            return streamChannel;
        }
    }
}
