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
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.AttributeKey;
import io.netty.util.collection.LongObjectHashMap;
import io.netty.util.collection.LongObjectMap;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.File;
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
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.Function;

/**
 * {@link QuicChannel} implementation that uses <a href="https://github.com/cloudflare/quiche">quiche</a>.
 */
final class QuicheQuicChannel extends AbstractChannel implements QuicChannel {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(QuicheQuicChannel.class);
    private static final String QLOG_FILE_EXTENSION = ".qlog";

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
    private final QuicheQuicChannelConfig config;
    private final boolean server;
    private final QuicStreamIdGenerator idGenerator;
    private final ChannelHandler streamHandler;
    private final Map.Entry<ChannelOption<?>, Object>[] streamOptionsArray;
    private final Map.Entry<AttributeKey<?>, Object>[] streamAttrsArray;
    private final TimeoutHandler timeoutHandler = new TimeoutHandler();
    private final InetSocketAddress remote;

    private QuicheQuicConnection connection;
    private boolean inFireChannelReadCompleteQueue;
    private boolean fireChannelReadCompletePending;
    private ByteBuf finBuffer;
    private ChannelPromise connectPromise;
    private ScheduledFuture<?> connectTimeoutFuture;
    private QuicConnectionAddress connectAddress;
    private ByteBuffer key;
    private CloseData closeData;
    private QuicConnectionStats statsAtClose;

    private boolean supportsDatagram;
    private boolean recvDatagramPending;
    private boolean datagramReadable;

    private boolean recvStreamPending;
    private boolean streamReadable;
    private boolean inRecv;
    private boolean inConnectionSend;

    private static final int CLOSED = 0;
    private static final int OPEN = 1;
    private static final int ACTIVE = 2;
    private volatile int state;
    private volatile String traceId;

    private static final AtomicLongFieldUpdater<QuicheQuicChannel> UNI_STREAMS_LEFT_UPDATER =
            AtomicLongFieldUpdater.newUpdater(QuicheQuicChannel.class, "uniStreamsLeft");
    private volatile long uniStreamsLeft;

    private static final AtomicLongFieldUpdater<QuicheQuicChannel> BIDI_STREAMS_LEFT_UPDATER =
            AtomicLongFieldUpdater.newUpdater(QuicheQuicChannel.class, "bidiStreamsLeft");
    private volatile long bidiStreamsLeft;

    private QuicheQuicChannel(Channel parent, boolean server, ByteBuffer key,
                      InetSocketAddress remote, boolean supportsDatagram, ChannelHandler streamHandler,
                              Map.Entry<ChannelOption<?>, Object>[] streamOptionsArray,
                              Map.Entry<AttributeKey<?>, Object>[] streamAttrsArray) {
        super(parent);
        config = new QuicheQuicChannelConfig(this);
        this.server = server;
        this.idGenerator = new QuicStreamIdGenerator(server);
        this.key = key;
        state = OPEN;

        this.supportsDatagram = supportsDatagram;
        this.remote = remote;
        this.streamHandler = streamHandler;
        this.streamOptionsArray = streamOptionsArray;
        this.streamAttrsArray = streamAttrsArray;
    }

    static QuicheQuicChannel forClient(Channel parent, InetSocketAddress remote, ChannelHandler streamHandler,
                                       Map.Entry<ChannelOption<?>, Object>[] streamOptionsArray,
                                       Map.Entry<AttributeKey<?>, Object>[] streamAttrsArray) {
        return new QuicheQuicChannel(parent, false, null, remote, false, streamHandler,
                streamOptionsArray, streamAttrsArray);
    }

    static QuicheQuicChannel forServer(Channel parent, ByteBuffer key, InetSocketAddress remote,
                                       boolean supportsDatagram, ChannelHandler streamHandler,
                                       Map.Entry<ChannelOption<?>, Object>[] streamOptionsArray,
                                       Map.Entry<AttributeKey<?>, Object>[] streamAttrsArray) {
        return new QuicheQuicChannel(parent, true, key, remote, supportsDatagram,
                streamHandler, streamOptionsArray, streamAttrsArray);
    }

    @Override
    public long peerAllowedStreams(QuicStreamType type) {
        switch (type) {
            case BIDIRECTIONAL:
                return bidiStreamsLeft;
            case UNIDIRECTIONAL:
                return uniStreamsLeft;
            default:
                return 0;
        }
    }

    void attachQuicheConnection(QuicheQuicConnection connection) {
        this.connection = connection;
        byte[] traceId = Quiche.quiche_conn_trace_id(connection.address());
        if (traceId != null) {
            this.traceId = new String(traceId);
        }

        // Setup QLOG if needed.
        QLogConfiguration configuration = config.getQLogConfiguration();
        if (configuration != null) {
            final String fileName;
            File file = new File(configuration.path());
            if (file.isDirectory()) {
                // Create directory if needed.
                file.mkdir();
                if (this.traceId != null) {
                    fileName = configuration.path() + File.separatorChar + this.traceId + "-" +
                            id().asShortText() + QLOG_FILE_EXTENSION;
                } else {
                    fileName = configuration.path() + File.separatorChar + id().asShortText() + QLOG_FILE_EXTENSION;
                }
            } else {
                fileName = configuration.path();
            }

            if (!Quiche.quiche_conn_set_qlog_path(connection.address(), fileName,
                    configuration.logTitle(), configuration.logDescription())) {
                logger.info("Unable to create qlog file: {} ", fileName);
            }
        }
    }

    private void connect(Function<QuicChannel, ? extends QuicSslEngine> engineProvider,
                         long configAddr, int localConnIdLength,
                         boolean supportsDatagram) throws Exception {
        assert this.connection == null;
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
        QuicSslEngine engine = engineProvider.apply(this);
        if (!(engine instanceof QuicheQuicSslEngine)) {
            failConnectPromiseAndThrow(new IllegalArgumentException("QuicSslEngine is not of type "
                    + QuicheQuicSslEngine.class.getSimpleName()));
            return;
        }
        if (!engine.getUseClientMode()) {
            failConnectPromiseAndThrow(new IllegalArgumentException("QuicSslEngine is not create in client mode"));
        }
        QuicheQuicSslEngine quicheEngine = (QuicheQuicSslEngine) engine;
        ByteBuffer connectId = address.connId.duplicate();
        ByteBuf idBuffer = alloc().directBuffer(connectId.remaining()).writeBytes(connectId.duplicate());
        try {
            QuicheQuicConnection connection = quicheEngine.createConnection(ssl ->
                    Quiche.quiche_conn_new_with_tls(Quiche.memoryAddress(idBuffer) + idBuffer.readerIndex(),
                            idBuffer.readableBytes(), -1, -1, configAddr, ssl, false));
            if (connection == null) {
                failConnectPromiseAndThrow(new ConnectException());
                return;
            }
            attachQuicheConnection(connection);
            this.supportsDatagram = supportsDatagram;
            key = connectId;
        } finally {
            idBuffer.release();
        }
    }

    private void failConnectPromiseAndThrow(Exception e) throws Exception {
        tryFailConnectPromise(e);
        throw e;
    }

    private boolean tryFailConnectPromise(Exception e) {
        ChannelPromise promise = connectPromise;
        if (promise != null) {
            connectPromise = null;
            promise.tryFailure(e);
            return true;
        }
        return false;
    }

    ByteBuffer key() {
        return key;
    }

    private boolean closeAllIfConnectionClosed() {
        if (connection.isClosed()) {
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
        QuicheQuicConnection conn = connection;
        try {
            connection = null;
            state = CLOSED;

            closeStreams();
            flushPendingQueue.clear();

            if (finBuffer != null) {
                finBuffer.release();
                finBuffer = null;
            }
            state = CLOSED;

            timeoutHandler.cancel();
        } finally {
            flushParent();
            conn.free();
        }
    }

    @Override
    protected DefaultChannelPipeline newChannelPipeline() {
        return new DefaultChannelPipeline(this) {
            @Override
            protected void onUnhandledInboundMessage(ChannelHandlerContext ctx, Object msg) {
                if (msg instanceof QuicStreamChannel) {
                    QuicStreamChannel channel = (QuicStreamChannel) msg;
                    Quic.setupChannel(channel, streamOptionsArray, streamAttrsArray, streamHandler, logger);
                    ctx.channel().eventLoop().register(channel);
                } else {
                    super.onUnhandledInboundMessage(ctx, msg);
                }
            }
        };
    }

    @Override
    public QuicChannel flush() {
        super.flush();
        return this;
    }

    @Override
    public QuicChannel read() {
        super.read();
        return this;
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

        // Call connectionSend() so we ensure we send all that is queued before we close the channel
        boolean written = connectionSend();

        Quiche.throwIfError(Quiche.quiche_conn_close(connectionAddressChecked(), app, err,
                Quiche.memoryAddress(reason) + reason.readerIndex(), reason.readableBytes()));

        // As we called quiche_conn_close(...) we need to ensure we will call quiche_conn_send(...) either
        // now or we will do so once we see the channelReadComplete event.
        //
        // See https://docs.rs/quiche/0.6.0/quiche/struct.Connection.html#method.close
        written |= connectionSend();
        if (written) {
            // As this is the close let us flush it asap.
            forceFlushParent();
        }
    }

    @Override
    protected void doBeginRead() {
        recvDatagramPending = true;
        recvStreamPending = true;
        if (datagramReadable || streamReadable) {
            ((QuicChannelUnsafe) unsafe()).recv();
        }
    }

    @Override
    protected Object filterOutboundMessage(Object msg) {
        if (msg instanceof ByteBuf) {
            return msg;
        }
        if (msg instanceof DatagramPacket) {
            DatagramPacket packet = (DatagramPacket) msg;
            if (remote == null) {
                if (packet.recipient() == null) {
                    return extractBuffer(packet);
                }
            } else if (remote.equals(packet.recipient())) {
                return extractBuffer(packet);
            }
            throw new UnsupportedOperationException("DatagramPacket recipient is not valid");
        }
        throw new UnsupportedOperationException("Unsupported message type: " + StringUtil.simpleClassName(msg));
    }

    private static ByteBuf extractBuffer(DatagramPacket packet) {
        ByteBuf content = packet.content().retain();
        packet.release();
        return content;
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer channelOutboundBuffer) throws Exception {
        if (!supportsDatagram) {
            throw new UnsupportedOperationException("Datagram extension is not supported");
        }
        boolean sendSomething = false;
        boolean retry = false;
        try {
            for (;;) {
                ByteBuf buffer = (ByteBuf) channelOutboundBuffer.current();
                if (buffer == null) {
                    break;
                }

                int readable = buffer.readableBytes();
                if (readable == 0) {
                    // Skip empty buffers.
                    channelOutboundBuffer.remove();
                    continue;
                }

                final int res;
                if (!buffer.isDirect() || buffer.nioBufferCount() > 1) {
                    ByteBuf tmpBuffer = alloc().directBuffer(readable);
                    try {
                        tmpBuffer.writeBytes(buffer, buffer.readerIndex(), readable);
                        res = sendDatagram(tmpBuffer);
                    } finally {
                        tmpBuffer.release();
                    }
                } else {
                    res = sendDatagram(buffer);
                }
                if (res >= 0) {
                    channelOutboundBuffer.remove();
                    sendSomething = true;
                    retry = false;
                } else {
                    if (res == Quiche.QUICHE_ERR_BUFFER_TOO_SHORT) {
                        retry = false;
                        channelOutboundBuffer.remove(Quiche.newException(res));
                    } else if (res == Quiche.QUICHE_ERR_INVALID_STATE) {
                        throw new UnsupportedOperationException("Remote peer does not support Datagram extension",
                                Quiche.newException(res));
                    } else if (Quiche.throwIfError(res)) {
                        if (retry) {
                            // We already retried and it didn't work. Let's drop the datagrams on the floor.
                            for (;;) {
                                if (!channelOutboundBuffer.remove()) {
                                    // The buffer is empty now.
                                    return;
                                }
                            }
                        }
                        // Set sendSomething to false a we will call connectionSend() now.
                        sendSomething = false;
                        // If this returned DONE we couldn't write anymore. This happens if the internal queue
                        // is full. In this case we should call quiche_conn_send(...) and so make space again.
                        if (connectionSend()) {
                            forceFlushParent();
                        }
                        // Let's try again to write the message.
                        retry = true;
                    }
                }
            }
        } finally {
            if (sendSomething && connectionSend()) {
                flushParent();
            }
        }
    }

    private int sendDatagram(ByteBuf buf) throws ClosedChannelException {
        return Quiche.quiche_conn_dgram_send(connectionAddressChecked(),
                Quiche.memoryAddress(buf) + buf.readerIndex(), buf.readableBytes());
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

    /**
     * This may call {@link #flush()} on the parent channel if needed. The flush may delayed until the read loop
     * is over.
     */
    private void flushParent() {
        if (!inFireChannelReadCompleteQueue) {
            forceFlushParent();
        }
    }

    /**
     * Call @link #flush()} on the parent channel.
     */
    private void forceFlushParent() {
        parent().flush();
    }

    private long connectionAddressChecked() throws ClosedChannelException {
        if (isConnDestroyed()) {
            throw new ClosedChannelException();
        }
        return connection.address();
    }

    boolean freeIfClosed() {
        if (isConnDestroyed()) {
            return true;
        }
        return closeAllIfConnectionClosed();
    }

    private void closeStreams() {
        // Make a copy to ensure we not run into a situation when we change the underlying iterator from
        // another method and so run in an assert error.
        for (QuicheQuicStreamChannel stream: streams.values().toArray(new QuicheQuicStreamChannel[0])) {
            stream.unsafe().close(voidPromise());
        }
        streams.clear();
    }

    void streamPriority(long streamId, byte priority, boolean incremental) throws Exception {
       Quiche.throwIfError(Quiche.quiche_conn_stream_priority(connectionAddressChecked(), streamId,
               priority, incremental));
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
        if (connectionSend()) {
            // Force the flush so the shutdown can be seen asap.
            forceFlushParent();
        }
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
            // As we called quiche_conn_stream_send(...) we need to ensure we will call quiche_conn_send(...) either
            // now or we will do so once we see the channelReadComplete event.
            //
            // See https://docs.rs/quiche/0.6.0/quiche/struct.Connection.html#method.send
            if (sendSomething && connectionSend()) {
                flushParent();
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
            if (connectionSend()) {
                flushParent();
            }
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
        if (Quiche.throwIfError(recvLen)) {
            return StreamRecvResult.DONE;
        } else {
            buffer.writerIndex(writerIndex + recvLen);
        }
        return finBuffer.getBoolean(0) ? StreamRecvResult.FIN : StreamRecvResult.OK;
    }

    /**
     * Receive some data on a QUIC connection.
     */
    void recv(ByteBuf buffer) {
        ((QuicChannelUnsafe) unsafe()).connectionRecv(buffer);
    }

    void writable() {
        boolean written = connectionSend();
        handleWritableStreams();
        written |= connectionSend();

        if (written) {
            // The writability changed so lets flush as fast as possible.
            forceFlushParent();
        }
    }

    void streamHasPendingWrites(long streamId) {
        flushPendingQueue.add(streamId);
    }

    private boolean handleWritableStreams() {
        int pending = flushPendingQueue.size();
        if (isConnDestroyed() || pending == 0) {
            return false;
        }
        long connAddr = connection.address();
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
     */
    void recvComplete() {
        try {
            if (isConnDestroyed()) {
                // Ensure we flush all pending writes.
                forceFlushParent();
                return;
            }
            fireChannelReadCompleteIfNeeded();

            // If we had called recv we need to ensure we call send as well.
            // See https://docs.rs/quiche/0.6.0/quiche/struct.Connection.html#method.send
            connectionSend();

            // We are done with the read loop, flush all pending writes now.
            forceFlushParent();
        } finally {
            inFireChannelReadCompleteQueue = false;
        }
    }

    private void fireChannelReadCompleteIfNeeded() {
        if (fireChannelReadCompletePending) {
            fireChannelReadCompletePending = false;
            pipeline().fireChannelReadComplete();
        }
    }

    private boolean isConnDestroyed() {
        return connection == null;
    }

    /**
     * Write datagrams if needed and return {@code true} if something was written and we need to call
     * {@link Channel#flush()} at some point.
     */
    private boolean connectionSend() {
        if (isConnDestroyed() || inConnectionSend) {
            return false;
        }

        inConnectionSend = true;

        try {
            long connAddr = connection.address();
            boolean packetWasWritten = false;
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
                parent().write(new DatagramPacket(out, remote));
                packetWasWritten = true;
            }
            if (packetWasWritten) {
                timeoutHandler.scheduleTimeout();
                return true;
            }
            return false;
        } finally {
            inConnectionSend = false;
        }
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
            if (type == QuicStreamType.UNIDIRECTIONAL) {
                UNI_STREAMS_LEFT_UPDATER.decrementAndGet(QuicheQuicChannel.this);
            } else {
                BIDI_STREAMS_LEFT_UPDATER.decrementAndGet(QuicheQuicChannel.this);
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
            int bufferReadable = buffer.readableBytes();
            if (bufferReadable == 0) {
                // Nothing to do here. Just return...
                // See also https://github.com/cloudflare/quiche/issues/817
                return;
            }
            inRecv = true;
            try {
                ByteBuf tmpBuffer = null;
                // We need to make a copy if the buffer is read only as recv(...) may modify the input buffer as well.
                // See https://docs.rs/quiche/0.6.0/quiche/struct.Connection.html#method.recv
                if (buffer.isReadOnly()) {
                    tmpBuffer = alloc().directBuffer(buffer.readableBytes());
                    tmpBuffer.writeBytes(buffer);
                    buffer = tmpBuffer;
                }
                int bufferReaderIndex = buffer.readerIndex();
                long memoryAddress = Quiche.memoryAddress(buffer) + bufferReaderIndex;

                long connAddr = connection.address();
                try {
                    do  {
                        // Call quiche_conn_recv(...) until we consumed all bytes or we did receive some error.
                        int res = Quiche.quiche_conn_recv(connAddr, memoryAddress, bufferReadable);
                        boolean done;
                        try {
                            done = Quiche.throwIfError(res);
                        } catch (Exception e) {
                            if (tryFailConnectPromise(e)) {
                                break;
                            }
                            pipeline().fireExceptionCaught(e);
                            done = true;
                        }

                        // Handle pending channelActive if needed.
                        if (handlePendingChannelActive()) {
                            // Connection was closed right away.
                            return;
                        }

                        if (Quiche.quiche_conn_is_established(connAddr) ||
                                Quiche.quiche_conn_is_in_early_data(connAddr)) {
                            long uniLeftOld = uniStreamsLeft;
                            long bidiLeftOld = bidiStreamsLeft;
                            // Only fetch new stream info when we used all our credits
                            if (uniLeftOld == 0 || bidiLeftOld == 0) {
                                long uniLeft = Quiche.quiche_conn_peer_streams_left_uni(connAddr);
                                long bidiLeft = Quiche.quiche_conn_peer_streams_left_bidi(connAddr);
                                uniStreamsLeft = uniLeft;
                                bidiStreamsLeft = bidiLeft;
                                if (uniLeftOld != uniLeft || bidiLeftOld != bidiLeft) {
                                    pipeline().fireUserEventTriggered(QuicStreamLimitChangedEvent.INSTANCE);
                                }
                            }

                            if (handleWritableStreams()) {
                                // Some data was produced, let's flush.
                                flushParent();
                            }

                            datagramReadable = true;
                            streamReadable = true;
                            recvDatagram();
                            recvStream();
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
            } finally {
                inRecv = false;
            }
        }

        void recv() {
            if (inRecv || isConnDestroyed()) {
                return;
            }

            long connAddr = connection.address();
            // Check if we can read anything yet.
            if (!Quiche.quiche_conn_is_established(connAddr) &&
                    !Quiche.quiche_conn_is_in_early_data(connAddr)) {
                return;
            }

            inRecv = true;
            try {
                recvDatagram();
                recvStream();
            } finally {
                fireChannelReadCompleteIfNeeded();
                inRecv = false;
            }
        }

        private void recvStream() {
            long connAddr = connection.address();
            long readableIterator = Quiche.quiche_conn_readable(connAddr);
            if (readableIterator != -1) {
                try {
                    // For streams we always process all streams when at least on read was requested.
                    if (recvStreamPending && streamReadable) {
                        for (;;) {
                            int readable = Quiche.quiche_stream_iter_next(
                                    readableIterator, readableStreams);
                            for (int i = 0; i < readable; i++) {
                                long streamId = readableStreams[i];
                                QuicheQuicStreamChannel streamChannel = streams.get(streamId);
                                if (streamChannel == null) {
                                    recvStreamPending = false;
                                    fireChannelReadCompletePending = true;
                                    streamChannel = addNewStreamChannel(streamId);
                                    streamChannel.readable();
                                    pipeline().fireChannelRead(streamChannel);
                                } else {
                                    streamChannel.readable();
                                }
                            }
                            if (readable < readableStreams.length) {
                                // We did consome all readable streams.
                                streamReadable = false;
                                break;
                            }
                        }
                    }
                } finally {
                    Quiche.quiche_stream_iter_free(readableIterator);
                }
            }
        }

        private void recvDatagram() {
            if (!supportsDatagram) {
                return;
            }
            long connAddr = connection.address();
            while (recvDatagramPending && datagramReadable) {
                @SuppressWarnings("deprecation")
                RecvByteBufAllocator.Handle recvHandle = recvBufAllocHandle();
                recvHandle.reset(config());

                int numMessagesRead = 0;
                do {
                    int len = Quiche.quiche_conn_dgram_recv_front_len(connAddr);
                    if (len == Quiche.QUICHE_ERR_DONE) {
                        datagramReadable = false;
                        return;
                    }

                    ByteBuf datagramBuffer = alloc().ioBuffer(len);
                    int writerIndex = datagramBuffer.writerIndex();
                    int written = Quiche.quiche_conn_dgram_recv(connAddr,
                            datagramBuffer.memoryAddress() + writerIndex, datagramBuffer.writableBytes());
                    try {
                        if (Quiche.throwIfError(written)) {
                            datagramBuffer.release();
                            // We did consume all datagram packets.
                            datagramReadable = false;
                            break;
                        }
                    } catch (Exception e) {
                        datagramBuffer.release();
                        pipeline().fireExceptionCaught(e);
                    }
                    recvHandle.lastBytesRead(written);
                    recvHandle.incMessagesRead(1);
                    numMessagesRead++;
                    datagramBuffer.writerIndex(writerIndex + written);
                    recvDatagramPending = false;
                    fireChannelReadCompletePending = true;

                    pipeline().fireChannelRead(datagramBuffer);
                } while (recvHandle.continueReading());
                recvHandle.readComplete();

                // Check if we produced any messages.
                if (numMessagesRead > 0) {
                    fireChannelReadCompleteIfNeeded();
                }
            }
        }

        private boolean handlePendingChannelActive() {
            long connAddr = connection.address();
            if (server) {
                if (state == OPEN && Quiche.quiche_conn_is_established(connAddr)) {
                    // We didn't notify before about channelActive... Update state and fire the event.
                    state = ACTIVE;
                    pipeline().fireChannelActive();
                    fireDatagramExtensionEvent();
                }
            } else if (connectPromise != null && Quiche.quiche_conn_is_established(connAddr)) {
                ChannelPromise promise = connectPromise;
                connectPromise = null;
                state = ACTIVE;

                boolean promiseSet = promise.trySuccess();
                pipeline().fireChannelActive();
                fireDatagramExtensionEvent();
                if (!promiseSet) {
                    this.close(this.voidPromise());
                    return true;
                }
            }
            return false;
        }

        private void fireDatagramExtensionEvent() {
            long connAddr = connection.address();
            int len = Quiche.quiche_conn_dgram_max_writable_len(connAddr);
            // QUICHE_ERR_DONE means the remote peer does not support the extension.
            if (len != Quiche.QUICHE_ERR_DONE) {
                pipeline().fireUserEventTriggered(new QuicDatagramExtensionEvent(len));
            }
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
    static QuicheQuicChannel handleConnect(Function<QuicChannel, ? extends QuicSslEngine> sslEngineProvider,
                                           SocketAddress address, long config, int localConnIdLength,
                                           boolean supportsDatagram) throws Exception {
        if (address instanceof QuicheQuicChannel.QuicheQuicChannelAddress) {
            QuicheQuicChannel.QuicheQuicChannelAddress addr = (QuicheQuicChannel.QuicheQuicChannelAddress) address;
            QuicheQuicChannel channel = addr.channel;
            channel.connect(sslEngineProvider, config, localConnIdLength, supportsDatagram);
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

    private final class TimeoutHandler implements Runnable {
        private ScheduledFuture<?> timeoutFuture;

        @Override
        public void run() {
            if (!isConnDestroyed()) {
                long connAddr = connection.address();
                timeoutFuture = null;
                // Notify quiche there was a timeout.
                Quiche.quiche_conn_on_timeout(connAddr);

                if (Quiche.quiche_conn_is_closed(connAddr)) {
                    forceClose();
                } else {
                    // We need to call connectionSend when a timeout was triggered.
                    // See https://docs.rs/quiche/0.6.0/quiche/struct.Connection.html#method.send.
                    boolean send = connectionSend();
                    if (send) {
                        flushParent();
                    }
                    if (!closeAllIfConnectionClosed()) {
                        // The connection is alive, reschedule.
                        scheduleTimeout();
                    }
                }
            }
        }

        // Schedule timeout.
        // See https://docs.rs/quiche/0.6.0/quiche/#generating-outgoing-packets
        void scheduleTimeout() {
            if (isConnDestroyed()) {
                cancel();
                return;
            }
            long nanos = Quiche.quiche_conn_timeout_as_nanos(connection.address());
            if (timeoutFuture == null) {
                timeoutFuture = eventLoop().schedule(this,
                        nanos, TimeUnit.NANOSECONDS);
            } else {
                long remaining = timeoutFuture.getDelay(TimeUnit.NANOSECONDS);
                if (remaining <= 0 || remaining > nanos) {
                    cancel();
                    timeoutFuture = eventLoop().schedule(this, nanos, TimeUnit.NANOSECONDS);
                }
            }
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

        final long[] stats = Quiche.quiche_conn_stats(connection.address());
        if (stats == null) {
            promise.setFailure(new IllegalStateException("native quiche_conn_stats(...) failed"));
            return null;
        }

        final QuicheQuicConnectionStats connStats =
            new QuicheQuicConnectionStats(stats[0], stats[1], stats[2], stats[3], stats[4], stats[5]);
        promise.setSuccess(connStats);
        return connStats;
    }
}
