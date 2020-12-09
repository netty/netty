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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ConnectTimeoutException;
import io.netty.channel.DefaultChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.AttributeKey;
import io.netty.util.collection.LongObjectHashMap;
import io.netty.util.collection.LongObjectMap;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ConnectionPendingException;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
/**
 * {@link QuicChannel} implementation that uses <a href="https://github.com/cloudflare/quiche">quiche</a>.
 */
final class QuicheQuicChannel extends AbstractChannel implements QuicChannel {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(QuicheQuicChannel.class);

    enum StreamRecvResult {
        /**
         * Nothing more to read from the stream.
         */
        DONE,
        /**
         * FIN flag received.
         */
        FIN,
        /**
         * Normal read without FIN flag.
         */
        OK
    }

    enum StreamSendResult {
        /**
         * Nothing more to sent and no FIN flag
         */
        DONE,
        /**
         * FIN flag sent.
         */
        FIN,
        /**
         * No more space, need to retry
         */
        NO_SPACE
    }

    private static final class CloseData implements ChannelFutureListener {
        final boolean applicationClose;
        final int err;
        final ByteBuf reason;

        CloseData(boolean applicationClose, int err, ByteBuf reason) {
            this.applicationClose = applicationClose;
            this.err = err;
            this.reason = reason;
        }

        @Override
        public void operationComplete(ChannelFuture future) {
            reason.release();
        }
    }

    private static final ChannelMetadata METADATA = new ChannelMetadata(false);
    private final long[] readableStreams = new long[128];
    private final LongObjectMap<QuicheQuicStreamChannel> streams = new LongObjectHashMap<>();
    private final Queue<Long> flushPendingQueue = new ArrayDeque<>();
    private final QuicChannelConfig config;
    private final boolean server;
    private final QuicStreamIdGenerator idGenerator;
    private final ChannelHandler streamHandler;
    private final Map.Entry<ChannelOption<?>, Object>[] streamOptionsArray;
    private final Map.Entry<AttributeKey<?>, Object>[] streamAttrsArray;
    private final TimeoutHandler timeoutHandler = new TimeoutHandler();
    private final InetSocketAddress remote;

    private long connAddr;
    private boolean inFireChannelReadCompleteQueue;
    private boolean fireChannelReadCompletePending;
    private boolean connectionSendNeeded;
    private ByteBuf finBuffer;
    private ChannelPromise connectPromise;
    private ScheduledFuture<?> connectTimeoutFuture;
    private QuicConnectionAddress connectAddress;
    private ByteBuffer key;
    private CloseData closeData;
    private QuicConnectionStats statsAtClose;

    private static final int CLOSED = 0;
    private static final int OPEN = 1;
    private static final int ACTIVE = 2;
    private volatile int state;
    private volatile String traceId;

    private QuicheQuicChannel(Channel parent, boolean server, ByteBuffer key, long connAddr, String traceId,
                      InetSocketAddress remote, ChannelHandler streamHandler,
                              Map.Entry<ChannelOption<?>, Object>[] streamOptionsArray,
                              Map.Entry<AttributeKey<?>, Object>[] streamAttrsArray) {
        super(parent);
        config = new DefaultQuicChannelConfig(this);
        this.server = server;
        this.idGenerator = new QuicStreamIdGenerator(server);
        this.key = key;
        state = OPEN;

        this.remote = remote;
        this.connAddr = connAddr;
        this.traceId = traceId;
        this.streamHandler = streamHandler;
        this.streamOptionsArray = streamOptionsArray;
        this.streamAttrsArray = streamAttrsArray;
    }

    static QuicheQuicChannel forClient(Channel parent, InetSocketAddress remote, ChannelHandler streamHandler,
                                       Map.Entry<ChannelOption<?>, Object>[] streamOptionsArray,
                                       Map.Entry<AttributeKey<?>, Object>[] streamAttrsArray) {
        return new QuicheQuicChannel(parent, false, null,
                -1, null, remote, streamHandler,
                streamOptionsArray, streamAttrsArray);
    }

    static QuicheQuicChannel forServer(Channel parent, ByteBuffer key,
                                       long connAddr, String traceId, InetSocketAddress remote,
                                       ChannelHandler streamHandler,
                                       Map.Entry<ChannelOption<?>, Object>[] streamOptionsArray,
                                       Map.Entry<AttributeKey<?>, Object>[] streamAttrsArray) {
        return new QuicheQuicChannel(parent, true, key, connAddr, traceId, remote,
                streamHandler, streamOptionsArray, streamAttrsArray);
    }

    private void connect(long configAddr, int localConnIdLength) throws Exception {
        assert this.connAddr == -1;
        assert this.traceId == null;
        assert this.key == null;
        QuicConnectionAddress address = this.connectAddress;
        if (address == QuicConnectionAddress.EPHEMERAL) {
            address = QuicConnectionAddress.random(localConnIdLength);
        } else {
            if (address.connId.remaining() != localConnIdLength) {
                failConnectPromiseAndThrow(new IllegalArgumentException("connectionAddress has length "
                        + address.connId.remaining()
                        + " instead of " + localConnIdLength));
            }
        }
        ByteBuffer connectId = address.connId.duplicate();
        ByteBuf idBuffer = alloc().directBuffer(connectId.remaining()).writeBytes(connectId.duplicate());
        final String serverName = config().getPeerCertServerName();
        try {
            long connection = Quiche.quiche_connect(serverName, Quiche.memoryAddress(idBuffer) + idBuffer.readerIndex(),
                    idBuffer.readableBytes(), configAddr);
            if (connection == -1) {
                failConnectPromiseAndThrow(new ConnectException());
            }
            this.traceId = Quiche.traceId(connection, idBuffer);
            this.connAddr = connection;

            connectionSendNeeded = true;
            key = connectId;
        } finally {
            idBuffer.release();
        }
    }

    private void failConnectPromiseAndThrow(Exception e) throws Exception {
        ChannelPromise promise = connectPromise;
        if (promise != null) {
            connectPromise = null;
            promise.tryFailure(e);
        }
        throw e;
    }

    ByteBuffer key() {
        return key;
    }

    private boolean closeAllIfConnectionClosed() {
        if (Quiche.quiche_conn_is_closed(connAddr)) {
            forceClose();
            return true;
        }
        return false;
    }

    boolean markInFireChannelReadCompleteQueue() {
        if (inFireChannelReadCompleteQueue) {
            return false;
        }
        inFireChannelReadCompleteQueue = true;
        return true;
    }

    void forceClose() {
        if (isConnDestroyed()) {
            // Just return if we already destroyed the underlying connection.
            return;
        }
        unsafe().close(voidPromise());
        // making sure that connection statistics is avaliable
        // even after channel is closed
        statsAtClose = collectStats0(eventLoop().newPromise());
        Quiche.quiche_conn_free(connAddr);
        connAddr = -1;
        state = CLOSED;

        closeStreams();
        flushPendingQueue.clear();

        if (finBuffer != null) {
            finBuffer.release();
            finBuffer = null;
        }
        state = CLOSED;

        timeoutHandler.cancel();
    }

    @Override
    protected DefaultChannelPipeline newChannelPipeline() {
        return new DefaultChannelPipeline(this) {
            @Override
            protected void onUnhandledInboundMessage(ChannelHandlerContext ctx, Object msg) {
                QuicStreamChannel channel = (QuicStreamChannel) msg;
                Quic.setupChannel(channel, streamOptionsArray, streamAttrsArray, streamHandler, logger);
                ctx.channel().eventLoop().register(channel);
            }
        };
    }

    @Override
    public Future<QuicStreamChannel> createStream(QuicStreamType type, ChannelHandler handler,
                                                  Promise<QuicStreamChannel> promise) {
        if (eventLoop().inEventLoop()) {
            ((QuicChannelUnsafe) unsafe()).connectStream(type, handler, promise);
        } else {
            eventLoop().execute(() -> ((QuicChannelUnsafe) unsafe()).connectStream(type, handler, promise));
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
    public ChannelFuture close(boolean applicationClose, int error, ByteBuf reason, ChannelPromise promise) {
        if (eventLoop().inEventLoop()) {
            close0(applicationClose, error, reason, promise);
        } else {
            eventLoop().execute(() -> close0(applicationClose, error, reason, promise));
        }
        return promise;
    }

    private void close0(boolean applicationClose, int error, ByteBuf reason, ChannelPromise promise) {
        if (closeData == null) {
            if (!reason.hasMemoryAddress()) {
                // Copy to direct buffer as that's what we need.
                ByteBuf copy = alloc().directBuffer(reason.readableBytes()).writeBytes(reason);
                reason.release();
                reason = copy;
            }
            closeData = new CloseData(applicationClose, error, reason);
            promise.addListener(closeData);
        } else {
            // We already have a close scheduled that uses a close data. Lets release the buffer early.
            reason.release();
        }
        close(promise);
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

        final boolean app;
        final int err;
        final ByteBuf reason;
        if (closeData == null) {
            app = false;
            err = 0;
            reason = Unpooled.EMPTY_BUFFER;
        } else {
            app = closeData.applicationClose;
            err = closeData.err;
            reason = closeData.reason;
            closeData = null;
        }
        Quiche.throwIfError(Quiche.quiche_conn_close(connectionAddressChecked(), app, err,
                Quiche.memoryAddress(reason) + reason.readerIndex(), reason.readableBytes()));

        // As we called quiche_conn_close(...) we need to ensure we will call quiche_conn_send(...) either
        // now or we will do so once we see the channelReadComplete event.
        //
        // See https://docs.rs/quiche/0.6.0/quiche/struct.Connection.html#method.close
        tryConnectionSend();
    }

    @Override
    protected void doBeginRead() {
        // TODO: handle this
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer channelOutboundBuffer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public QuicChannelConfig config() {
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

    private void flushParent() {
        parent().flush();
    }

    private long connectionAddressChecked() throws ClosedChannelException {
        if (isConnDestroyed()) {
            throw new ClosedChannelException();
        }
        return connAddr;
    }

    boolean freeIfClosed() {
        if (isConnDestroyed()) {
            return true;
        }
        return closeAllIfConnectionClosed();
    }

    private void closeStreams() {
        for (QuicheQuicStreamChannel stream: streams.values()) {
            stream.unsafe().close(voidPromise());
        }
        streams.clear();
    }

    void streamClosed(long streamId) {
        streams.remove(streamId);
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

        // As we called quiche_conn_stream_shutdown(...) we need to ensure we will call quiche_conn_send(...) either
        // now or we will do so once we see the channelReadComplete event.
        //
        // See https://docs.rs/quiche/0.6.0/quiche/struct.Connection.html#method.send
        tryConnectionSend();
        Quiche.notifyPromise(res, promise);
    }

    StreamSendResult streamSendMultiple(long streamId, ByteBufAllocator allocator,
                                        ChannelOutboundBuffer streamOutboundBuffer, Runnable finSent) throws Exception {
        boolean sendSomething = false;
        try {
            for (;;) {
                Object current =  streamOutboundBuffer.current();
                if (current == null) {
                    break;
                }
                final ByteBuf buffer;
                final boolean fin;
                if (current instanceof ByteBuf) {
                    buffer = (ByteBuf) current;
                    fin = false;
                } else {
                    QuicStreamFrame streamFrame = (QuicStreamFrame) current;
                    buffer = streamFrame.content();
                    fin = streamFrame.hasFin();
                }
                int readable = buffer.readableBytes();
                if (readable == 0 && !fin) {
                    // Skip empty buffers, but only if its not a FIN.
                    streamOutboundBuffer.remove();
                    continue;
                }

                final int res;
                if (!buffer.isDirect()) {
                    ByteBuf tmpBuffer = allocator.directBuffer(readable);
                    try {
                        tmpBuffer.writeBytes(buffer, buffer.readerIndex(), readable);
                        res = streamSend(streamId, tmpBuffer, fin);
                    } finally {
                        tmpBuffer.release();
                    }
                } else if (buffer.nioBufferCount() > 1) {
                    ByteBuffer[] nioBuffers  = buffer.nioBuffers();
                    int lastIdx = nioBuffers.length - 1;
                    for (int i = 0; i < lastIdx; i++) {
                        ByteBuffer nioBuffer = nioBuffers[i];
                        while (nioBuffer.hasRemaining()) {
                            int localRes = streamSend(streamId, nioBuffer, false);
                            if (Quiche.throwIfError(localRes) || localRes == 0) {
                                // stream has no capacity left stop trying to send.
                                return StreamSendResult.NO_SPACE;
                            }
                            nioBuffer.position(nioBuffer.position() + localRes);

                            sendSomething = true;
                            streamOutboundBuffer.removeBytes(localRes);
                        }
                    }
                    res = streamSend(streamId, nioBuffers[lastIdx], fin);
                } else {
                    res = streamSend(streamId, buffer, fin);
                }

                if (Quiche.throwIfError(res)) {
                    // stream has no capacity left stop trying to send.
                    return StreamSendResult.NO_SPACE;
                }

                if (res == 0) {
                    // If readable == 0 we know it was because of a FIN as otherwise we would have skipped the write
                    // in the first place.
                    if (readable == 0) {
                        sendSomething = true;
                        return handleFin(streamOutboundBuffer, -1, finSent);
                    }
                    // We couldn't send the data as there was not enough space.
                    return StreamSendResult.NO_SPACE;
                }

                sendSomething = true;
                if (fin) {
                    return handleFin(streamOutboundBuffer, res, finSent);
                }
                streamOutboundBuffer.removeBytes(res);
            }
            return StreamSendResult.DONE;
        } finally {
            if (sendSomething) {
                // As we called quiche_conn_stream_send(...) we need to ensure we will call quiche_conn_send(...) either
                // now or we will do so once we see the channelReadComplete event.
                //
                // See https://docs.rs/quiche/0.6.0/quiche/struct.Connection.html#method.send
                tryConnectionSend();
            }
        }
    }

    private static StreamSendResult handleFin(ChannelOutboundBuffer streamOutboundBuffer, int res, Runnable finSent) {
        // We need to let the caller know we did sent a FIN before we actually remove the bytes from the
        // outbound buffer. This is required as removeBytes(...) will notify the promise of the writes
        // which may try to send a FIN again which would produce a FINAL_SIZE error.
        finSent.run();

        if (res == -1) {
            // -1 means we did write an empty frame with a FIN.
            streamOutboundBuffer.remove();
        } else {
            streamOutboundBuffer.removeBytes(res);
        }
        return StreamSendResult.FIN;
    }

    void streamSendFin(long streamId) throws Exception {
        try {
            // Just write an empty buffer and set fin to true.
            Quiche.throwIfError(streamSend(streamId, Unpooled.EMPTY_BUFFER, true));
        } finally {
            // As we called quiche_conn_stream_send(...) we need to ensure we will call quiche_conn_send(...) either
            // now or we will do so once we see the channelReadComplete event.
            //
            // See https://docs.rs/quiche/0.6.0/quiche/struct.Connection.html#method.send
            tryConnectionSend();
        }
    }

    private int streamSend(long streamId, ByteBuf buffer, boolean fin) throws Exception {
        return Quiche.quiche_conn_stream_send(connectionAddressChecked(), streamId,
                Quiche.memoryAddress(buffer) + buffer.readerIndex(), buffer.readableBytes(), fin);
    }

    private int streamSend(long streamId, ByteBuffer buffer, boolean fin) throws Exception {
        return Quiche.quiche_conn_stream_send(connectionAddressChecked(), streamId,
                Quiche.memoryAddress(buffer) + buffer.position(), buffer.remaining(), fin);
    }

    StreamRecvResult streamRecv(long streamId, ByteBuf buffer) throws Exception {
        if (finBuffer == null) {
            finBuffer = alloc().directBuffer(1);
        }
        int writerIndex = buffer.writerIndex();
        long memoryAddress = Quiche.memoryAddress(buffer);
        int recvLen = Quiche.quiche_conn_stream_recv(connectionAddressChecked(), streamId,
                memoryAddress + writerIndex, buffer.writableBytes(), Quiche.memoryAddress(finBuffer));
        if (recvLen == Quiche.QUICHE_ERR_INVALID_STREAM_STATE) {
            // Remove this workaround as soon there is a quiche release that pulled in:
            // https://github.com/cloudflare/quiche/pull/742
            assert isStreamFinished(streamId);
            return StreamRecvResult.FIN;
        }

        if (Quiche.throwIfError(recvLen)) {
            return StreamRecvResult.DONE;
        } else {
            buffer.writerIndex(writerIndex + recvLen);
        }
        return finBuffer.getBoolean(0) ? StreamRecvResult.FIN : StreamRecvResult.OK;
    }

    private void tryConnectionSend() {
        connectionSendNeeded = true;
        if (!fireChannelReadCompletePending) {
            if (connectionSend()) {
                flushParent();
            }
        }
    }

    /**
     * Receive some data on a QUIC connection.
     */
    void recv(ByteBuf buffer) {
        ((QuicChannelUnsafe) unsafe()).connectionRecv(buffer);
    }

    boolean writable() {
        if (handleWritableStreams()) {
            return connectionSend();
        }
        return false;
    }

    void streamHasPendingWrites(long streamId) {
        flushPendingQueue.add(streamId);
    }

    private boolean handleWritableStreams() {
        int pending = flushPendingQueue.size();
        if (isConnDestroyed() || pending == 0) {
            return false;
        }

        boolean mayNeedWrite = false;
        if (Quiche.quiche_conn_is_established(connAddr) ||
                Quiche.quiche_conn_is_in_early_data(connAddr)) {
            // We only want to process the number of channels that were in the queue when we entered
            // handleWritableStreams(). Otherwise we may would loop forever as a channel may add itself again
            // if the write was again partial.
            for (int i = 0; i < pending; i++) {
                Long streamId = flushPendingQueue.poll();
                if (streamId == null) {
                    break;
                }
                // Checking quiche_conn_stream_capacity(...) is cheaper then calling channel.writable() just
                // to notice that we can not write again.
                int capacity = Quiche.quiche_conn_stream_capacity(connAddr, streamId);
                if (capacity == 0) {
                    // Still not writable, put back in the queue.
                    flushPendingQueue.add(streamId);
                } else {
                    long sid = streamId;
                    QuicheQuicStreamChannel channel = streams.get(sid);
                    if (channel != null) {
                        if (capacity > 0) {
                            mayNeedWrite = true;
                            channel.writable();
                        } else {
                            if (!Quiche.quiche_conn_stream_finished(connAddr, sid)) {
                                // Only fire an exception if the error was not caused because the stream is considered
                                // finished.
                                channel.pipeline().fireExceptionCaught(Quiche.newException(capacity));
                            }
                            // Let's close the channel if quiche_conn_stream_capacity(...) returns an error.
                            channel.forceClose();
                        }
                    }
                }
            }
        }
        return mayNeedWrite;
    }

    /**
     * Called once we receive a channelReadComplete event. This method will take care of calling
     * {@link ChannelPipeline#fireChannelReadComplete()} if needed and also to handle pending flushes of
     * writable {@link QuicheQuicStreamChannel}s.
     *
     * If this methods returns {@code true} we need to call {@link Channel#flush()} on the {@link #parent()}
     * {@link Channel} once we notified all {@link QuicheQuicChannel} that are handled by the same parent
     * {@link Channel}.
     */
    boolean channelReadComplete() {
        inFireChannelReadCompleteQueue = false;
        if (isConnDestroyed()) {
            return false;
        }
        if (fireChannelReadCompletePending) {
            fireChannelReadCompletePending = false;
            pipeline().fireChannelReadComplete();
            // If we had called recv we need to ensure we call send as well.
            // See https://docs.rs/quiche/0.6.0/quiche/struct.Connection.html#method.send
            connectionSendNeeded = true;
        }

        // Try flush more streams that had some flushes pending.
        handleWritableStreams();

        return connectionSend();
    }

    private boolean isConnDestroyed() {
        return connAddr == -1;
    }

    /**
     * Write datagrams if needed and return {@code true} if something was written and we need to call
     * {@link Channel#flush()} at some point.
     */
    private boolean connectionSend() {
        if (isConnDestroyed() || !connectionSendNeeded) {
            return false;
        }
        connectionSendNeeded = false;
        ChannelFuture lastFuture = null;

        for (;;) {
            ByteBuf out = alloc().directBuffer(Quic.MAX_DATAGRAM_SIZE);
            int writerIndex = out.writerIndex();
            int written = Quiche.quiche_conn_send(
                    connAddr, Quiche.memoryAddress(out) + writerIndex, out.writableBytes());

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

            if (written == 0) {
                // No need to create a new datagram packet. Just release and try again.
                out.release();
                continue;
            }
            out.writerIndex(writerIndex + written);
            lastFuture = parent().write(new DatagramPacket(out, remote));
        }
        if (lastFuture != null) {
            lastFuture.addListener(timeoutHandler);
            return true;
        }
        return false;
    }

    private final class QuicChannelUnsafe extends AbstractChannel.AbstractUnsafe {

        void connectStream(QuicStreamType type, ChannelHandler handler,
                           Promise<QuicStreamChannel> promise) {
            long streamId = idGenerator.nextStreamId(type == QuicStreamType.BIDIRECTIONAL);
            try {
                Quiche.throwIfError(streamSend(streamId, Unpooled.EMPTY_BUFFER, false));
            } catch (Exception e) {
                promise.setFailure(e);
                return;
            }
            QuicheQuicStreamChannel streamChannel = addNewStreamChannel(streamId);
            if (handler != null) {
                streamChannel.pipeline().addLast(handler);
            }
            eventLoop().register(streamChannel).addListener((ChannelFuture f) -> {
                if (f.isSuccess()) {
                    promise.setSuccess(streamChannel);
                } else {
                    promise.setFailure(f.cause());
                    streams.remove(streamId);
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

            if (remote instanceof QuicConnectionAddress) {
                if (key != null) {
                    // If a key is assigned we know this channel was already connected.
                    channelPromise.setFailure(new AlreadyConnectedException());
                    return;
                }

                QuicConnectionAddress address = (QuicConnectionAddress) remote;
                connectPromise = channelPromise;
                connectAddress = address;

                // Schedule connect timeout.
                int connectTimeoutMillis = config().getConnectTimeoutMillis();
                if (connectTimeoutMillis > 0) {
                    connectTimeoutFuture = eventLoop().schedule(() -> {
                        ChannelPromise connectPromise = QuicheQuicChannel.this.connectPromise;
                        if (connectPromise != null && !connectPromise.isDone()
                                && connectPromise.tryFailure(new ConnectTimeoutException(
                                "connection timed out: " + remote))) {
                            close(voidPromise());
                        }
                    }, connectTimeoutMillis, TimeUnit.MILLISECONDS);
                }

                connectPromise.addListener((ChannelFuture future) -> {
                    if (future.isCancelled()) {
                        if (connectTimeoutFuture != null) {
                            connectTimeoutFuture.cancel(false);
                        }
                        connectPromise = null;
                        close(voidPromise());
                    }
                });

                parent().connect(new QuicheQuicChannelAddress(QuicheQuicChannel.this));
                return;
            }

            channelPromise.setFailure(new UnsupportedOperationException());
        }

        void connectionRecv(ByteBuf buffer) {
            if (isConnDestroyed()) {
                return;
            }

            ByteBuf tmpBuffer = null;
            // We need to make a copy if the buffer is read only as recv(...) may modify the input buffer as well.
            // See https://docs.rs/quiche/0.6.0/quiche/struct.Connection.html#method.recv
            if (buffer.isReadOnly()) {
                tmpBuffer = alloc().directBuffer(buffer.readableBytes());
                tmpBuffer.writeBytes(buffer);
                buffer = tmpBuffer;
            }
            int bufferReadable = buffer.readableBytes();
            int bufferReaderIndex = buffer.readerIndex();
            long memoryAddress = Quiche.memoryAddress(buffer) + bufferReaderIndex;

            // We need to call quiche_conn_send(...),
            // see https://docs.rs/quiche/0.6.0/quiche/struct.Connection.html#method.send
            connectionSendNeeded = true;
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

                    // Handle pending channelActive if needed.
                    if (handlePendingChannelActive()) {
                        // Connection was closed right away.
                        return;
                    }

                    if (Quiche.quiche_conn_is_established(connAddr) || Quiche.quiche_conn_is_in_early_data(connAddr)) {
                        long readableIterator = Quiche.quiche_conn_readable(connAddr);
                        if (readableIterator != -1) {
                            try {
                                for (;;) {
                                    int readable = Quiche.quiche_stream_iter_next(readableIterator, readableStreams);
                                    for (int i = 0; i < readable; i++) {
                                        long streamId = readableStreams[i];
                                        QuicheQuicStreamChannel streamChannel = streams.get(streamId);
                                        if (streamChannel == null) {
                                            // We create a new channel and fire it through the pipeline which
                                            // means we also need to ensure we call fireChannelReadCompletePending.
                                            fireChannelReadCompletePending = true;
                                            streamChannel = addNewStreamChannel(streamId);
                                            streamChannel.readable();
                                            pipeline().fireChannelRead(streamChannel);
                                        } else {
                                            streamChannel.readable();
                                        }
                                    }
                                    if (readable < readableStreams.length) {
                                        break;
                                    }
                                }
                            } finally {
                                Quiche.quiche_stream_iter_free(readableIterator);
                            }
                        }
                    }

                    if (done) {
                        break;
                    } else {
                        memoryAddress += res;
                        bufferReadable -= res;
                    }
                } while (bufferReadable > 0);
            } finally {
                buffer.skipBytes((int) (memoryAddress - Quiche.memoryAddress(buffer)));
                if (tmpBuffer != null) {
                    tmpBuffer.release();
                }
            }
        }

        private boolean handlePendingChannelActive() {
            if (server) {
                if (state == OPEN && Quiche.quiche_conn_is_established(connAddr)) {
                    // We didnt notify before about channelActive... Update state and fire the event.
                    state = ACTIVE;
                    pipeline().fireChannelActive();
                }
            } else if (connectPromise != null && Quiche.quiche_conn_is_established(connAddr)) {
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
            return false;
        }

        private QuicheQuicStreamChannel addNewStreamChannel(long streamId) {
            QuicheQuicStreamChannel streamChannel = new QuicheQuicStreamChannel(
                    QuicheQuicChannel.this, streamId);
            QuicheQuicStreamChannel old = streams.put(streamId, streamChannel);
            assert old == null;
            return streamChannel;
        }
    }

    /**
     * Finish the connect of a client channel.
     */
    void finishConnect() {
        assert !server;
        if (connectionSend()) {
            flushParent();
        }
    }

    // TODO: Come up with something better.
    static QuicheQuicChannel handleConnect(SocketAddress address, long config, int localConnIdLength) throws Exception {
        if (address instanceof QuicheQuicChannel.QuicheQuicChannelAddress) {
            QuicheQuicChannel.QuicheQuicChannelAddress addr = (QuicheQuicChannel.QuicheQuicChannelAddress) address;
            QuicheQuicChannel channel = addr.channel;
            channel.connect(config, localConnIdLength);
            return channel;
        }
        return null;
    }

    /**
     * Just a container to pass the {@link QuicheQuicChannel} to {@link QuicheQuicClientCodec}.
     */
    private static final class QuicheQuicChannelAddress extends SocketAddress {

        final QuicheQuicChannel channel;

        QuicheQuicChannelAddress(QuicheQuicChannel channel) {
            this.channel = channel;
        }
    }

    private final class TimeoutHandler implements ChannelFutureListener, Runnable {
        private Future<?> timeoutFuture;

        @Override
        public void operationComplete(ChannelFuture future) {
            if (future.isSuccess()) {
                // Schedule timeout.
                // See https://docs.rs/quiche/0.6.0/quiche/#generating-outgoing-packets
                timeoutFuture = null;
                scheduleTimeout();
            }
        }

        @Override
        public void run() {
            if (!isConnDestroyed()) {
                // Notify quiche there was a timeout.
                Quiche.quiche_conn_on_timeout(connAddr);

                if (Quiche.quiche_conn_is_closed(connAddr)) {
                    timeoutFuture = null;
                    forceClose();
                } else {
                    // We need to set connectionSendNeeded to true as we always need to call this method when
                    // a timeout was triggered.
                    // See https://docs.rs/quiche/0.6.0/quiche/struct.Connection.html#method.send.
                    connectionSendNeeded = true;
                    boolean send = connectionSend();
                    if (send) {
                        flushParent();
                    }
                    if (!closeAllIfConnectionClosed() && send) {
                        // The connection is alive, reschedule.
                        timeoutFuture = null;
                        scheduleTimeout();
                    }
                }
            }
        }

        private void scheduleTimeout() {
            cancel();
            if (isConnDestroyed()) {
                return;
            }
            long nanos = Quiche.quiche_conn_timeout_as_nanos(connAddr);
            timeoutFuture = eventLoop().schedule(this,
                    nanos, TimeUnit.NANOSECONDS);
        }

        void cancel() {
            if (timeoutFuture != null) {
                timeoutFuture.cancel(false);
                timeoutFuture = null;
            }
        }
    }

    @Override
    public Future<QuicConnectionStats> collectStats(Promise<QuicConnectionStats> promise) {
        if (eventLoop().inEventLoop()) {
            collectStats0(promise);
        } else {
            eventLoop().execute(() -> collectStats0(promise));
        }
        return promise;
    }

    private QuicConnectionStats collectStats0(Promise<QuicConnectionStats> promise) {
        if (isConnDestroyed()) {
            promise.setSuccess(statsAtClose);
            return statsAtClose;
        }

        final long[] stats = Quiche.quiche_conn_stats(connAddr);
        if (stats == null) {
            promise.setFailure(new IllegalStateException("native quiche_conn_stats(...) failed"));
            return null;
        }

        final DefaultQuicConnectionStats connStats =
            new DefaultQuicConnectionStats(stats[0], stats[1], stats[2], stats[3], stats[4], stats[5]);
        promise.setSuccess(connStats);
        return connStats;
    }
}
