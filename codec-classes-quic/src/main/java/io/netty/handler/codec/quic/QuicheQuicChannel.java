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
package io.netty.handler.codec.quic;

import io.netty.buffer.ByteBuf;
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
import io.netty.handler.ssl.SniCompletionEvent;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.util.AttributeKey;
import io.netty.util.collection.LongObjectHashMap;
import io.netty.util.collection.LongObjectMap;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.ImmediateExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import java.io.File;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ConnectionPendingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.Consumer;
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

    private enum ChannelState {
        OPEN,
        ACTIVE,
        CLOSED
    }

    private enum SendResult {
        SOME,
        NONE,
        CLOSE
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

    private static final ChannelMetadata METADATA = new ChannelMetadata(false, 16);
    private long[] readableStreams = new long[4];
    private long[] writableStreams = new long[4];
    private final LongObjectMap<QuicheQuicStreamChannel> streams = new LongObjectHashMap<>();
    private final QuicheQuicChannelConfig config;
    private final boolean server;
    private final QuicStreamIdGenerator idGenerator;
    private final ChannelHandler streamHandler;
    private final Map.Entry<ChannelOption<?>, Object>[] streamOptionsArray;
    private final Map.Entry<AttributeKey<?>, Object>[] streamAttrsArray;
    private final TimeoutHandler timeoutHandler;
    private final QuicConnectionIdGenerator connectionIdAddressGenerator;
    private final QuicResetTokenGenerator resetTokenGenerator;
    private final Set<ByteBuffer> sourceConnectionIds = new HashSet<>();

    private Consumer<QuicheQuicChannel> freeTask;
    private Executor sslTaskExecutor;
    private boolean inFireChannelReadCompleteQueue;
    private boolean fireChannelReadCompletePending;
    private ByteBuf finBuffer;
    private ChannelPromise connectPromise;
    private ScheduledFuture<?> connectTimeoutFuture;
    private QuicConnectionAddress connectAddress;
    private CloseData closeData;
    private QuicConnectionCloseEvent connectionCloseEvent;
    private QuicConnectionStats statsAtClose;
    private boolean supportsDatagram;
    private boolean recvDatagramPending;
    private boolean datagramReadable;
    private boolean recvStreamPending;
    private boolean streamReadable;
    private boolean handshakeCompletionNotified;
    private boolean earlyDataReadyNotified;
    private int reantranceGuard;
    private static final int IN_RECV = 1 << 1;
    private static final int IN_CONNECTION_SEND = 1 << 2;
    private static final int IN_HANDLE_WRITABLE_STREAMS = 1 << 3;
    private volatile ChannelState state = ChannelState.OPEN;
    private volatile boolean timedOut;
    private volatile String traceId;
    private volatile QuicheQuicConnection connection;
    private volatile InetSocketAddress local;
    private volatile InetSocketAddress remote;

    private final ChannelFutureListener continueSendingListener = f -> {
        if (connectionSend(connection) != SendResult.NONE) {
            flushParent();
        }
    };

    private static final AtomicLongFieldUpdater<QuicheQuicChannel> UNI_STREAMS_LEFT_UPDATER =
            AtomicLongFieldUpdater.newUpdater(QuicheQuicChannel.class, "uniStreamsLeft");
    private volatile long uniStreamsLeft;

    private static final AtomicLongFieldUpdater<QuicheQuicChannel> BIDI_STREAMS_LEFT_UPDATER =
            AtomicLongFieldUpdater.newUpdater(QuicheQuicChannel.class, "bidiStreamsLeft");
    private volatile long bidiStreamsLeft;

    private QuicheQuicChannel(Channel parent, boolean server, @Nullable ByteBuffer key, InetSocketAddress local,
                              InetSocketAddress remote, boolean supportsDatagram, ChannelHandler streamHandler,
                              Map.Entry<ChannelOption<?>, Object>[] streamOptionsArray,
                              Map.Entry<AttributeKey<?>, Object>[] streamAttrsArray,
                              @Nullable Consumer<QuicheQuicChannel> freeTask,
                              @Nullable Executor sslTaskExecutor,
                              @Nullable QuicConnectionIdGenerator connectionIdAddressGenerator,
                              @Nullable QuicResetTokenGenerator resetTokenGenerator) {
        super(parent);
        config = new QuicheQuicChannelConfig(this);
        this.freeTask = freeTask;
        this.server = server;
        this.idGenerator = new QuicStreamIdGenerator(server);
        this.connectionIdAddressGenerator = connectionIdAddressGenerator;
        this.resetTokenGenerator = resetTokenGenerator;
        if (key != null) {
            this.sourceConnectionIds.add(key);
        }

        this.supportsDatagram = supportsDatagram;
        this.local = local;
        this.remote = remote;

        this.streamHandler = streamHandler;
        this.streamOptionsArray = streamOptionsArray;
        this.streamAttrsArray = streamAttrsArray;
        timeoutHandler = new TimeoutHandler();
        this.sslTaskExecutor = sslTaskExecutor == null ? ImmediateExecutor.INSTANCE : sslTaskExecutor;
    }

    static QuicheQuicChannel forClient(Channel parent, InetSocketAddress local, InetSocketAddress remote,
                                       ChannelHandler streamHandler,
                                       Map.Entry<ChannelOption<?>, Object>[] streamOptionsArray,
                                       Map.Entry<AttributeKey<?>, Object>[] streamAttrsArray) {
        return new QuicheQuicChannel(parent, false, null, local, remote, false, streamHandler,
                streamOptionsArray, streamAttrsArray, null, null, null, null);
    }

    static QuicheQuicChannel forServer(Channel parent, ByteBuffer key, InetSocketAddress local,
                                       InetSocketAddress remote,
                                       boolean supportsDatagram, ChannelHandler streamHandler,
                                       Map.Entry<ChannelOption<?>, Object>[] streamOptionsArray,
                                       Map.Entry<AttributeKey<?>, Object>[] streamAttrsArray,
                                       Consumer<QuicheQuicChannel> freeTask, Executor sslTaskExecutor,
                                       QuicConnectionIdGenerator connectionIdAddressGenerator,
                                       QuicResetTokenGenerator resetTokenGenerator) {
        return new QuicheQuicChannel(parent, true, key, local, remote, supportsDatagram,
                streamHandler, streamOptionsArray, streamAttrsArray, freeTask,
                sslTaskExecutor, connectionIdAddressGenerator, resetTokenGenerator);
    }

    private static final int MAX_ARRAY_LEN = 128;

    private static long[] growIfNeeded(long[] array, int maxLength) {
        if (maxLength > array.length) {
            if (array.length == MAX_ARRAY_LEN) {
                return array;
            }
            // Increase by 4 until we reach MAX_ARRAY_LEN
            return new long[Math.min(MAX_ARRAY_LEN, array.length + 4)];
        }
        return array;
    }

    @Override
    public boolean isTimedOut() {
        return timedOut;
    }

    @Override
    public SSLEngine sslEngine() {
        QuicheQuicConnection connection = this.connection;
        return connection == null ? null : connection.engine();
    }

    private void notifyAboutHandshakeCompletionIfNeeded(QuicheQuicConnection conn,
                                                        @Nullable SSLHandshakeException cause) {
        if (handshakeCompletionNotified) {
            return;
        }
        if (cause != null) {
            pipeline().fireUserEventTriggered(new SslHandshakeCompletionEvent(cause));
            return;
        }
        if (conn.isFreed()) {
            return;
        }
        switch (connection.engine().getHandshakeStatus()) {
            case NOT_HANDSHAKING:
            case FINISHED:
                handshakeCompletionNotified = true;
                pipeline().fireUserEventTriggered(SslHandshakeCompletionEvent.SUCCESS);
                break;
            default:
                break;
        }
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

        connection.init(local, remote,
                sniHostname -> pipeline().fireUserEventTriggered(new SniCompletionEvent(sniHostname)));

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

    void connectNow(Function<QuicChannel, ? extends QuicSslEngine> engineProvider, Executor sslTaskExecutor,
                    Consumer<QuicheQuicChannel> freeTask, long configAddr, int localConnIdLength,
                    boolean supportsDatagram, ByteBuffer fromSockaddrMemory, ByteBuffer toSockaddrMemory)
            throws Exception {
        assert this.connection == null;
        assert this.traceId == null;
        assert this.sourceConnectionIds.isEmpty();

        this.sslTaskExecutor = sslTaskExecutor;
        this.freeTask = freeTask;

        QuicConnectionAddress address = this.connectAddress;

        if (address == QuicConnectionAddress.EPHEMERAL) {
            address = QuicConnectionAddress.random(localConnIdLength);
        }
        ByteBuffer connectId = address.id();
        if (connectId.remaining() != localConnIdLength) {
            failConnectPromiseAndThrow(new IllegalArgumentException("connectionAddress has length "
                    + connectId.remaining()
                    + " instead of " + localConnIdLength));
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
        ByteBuf idBuffer = alloc().directBuffer(connectId.remaining()).writeBytes(connectId.duplicate());
        try {
            int fromSockaddrLen = SockaddrIn.setAddress(fromSockaddrMemory, local);
            int toSockaddrLen = SockaddrIn.setAddress(toSockaddrMemory, remote);
            QuicheQuicConnection connection = quicheEngine.createConnection(ssl ->
                    Quiche.quiche_conn_new_with_tls(Quiche.readerMemoryAddress(idBuffer),
                            idBuffer.readableBytes(), -1, -1,
                            Quiche.memoryAddressWithPosition(fromSockaddrMemory), fromSockaddrLen,
                            Quiche.memoryAddressWithPosition(toSockaddrMemory), toSockaddrLen,
                            configAddr, ssl, false));
            if (connection == null) {
                failConnectPromiseAndThrow(new ConnectException());
                return;
            }
            attachQuicheConnection(connection);
            QuicClientSessionCache sessionCache = quicheEngine.ctx.getSessionCache();
            if (sessionCache != null) {
                byte[] sessionBytes = sessionCache
                        .getSession(quicheEngine.getSession().getPeerHost(), quicheEngine.getSession().getPeerPort());
                if (sessionBytes != null) {
                    Quiche.quiche_conn_set_session(connection.address(), sessionBytes);
                }
            }
            this.supportsDatagram = supportsDatagram;
            sourceConnectionIds.add(connectId);
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

    Set<ByteBuffer> sourceConnectionIds() {
        return sourceConnectionIds;
    }

    boolean markInFireChannelReadCompleteQueue() {
        if (inFireChannelReadCompleteQueue) {
            return false;
        }
        inFireChannelReadCompleteQueue = true;
        return true;
    }

    private void failPendingConnectPromise() {
        ChannelPromise promise = QuicheQuicChannel.this.connectPromise;
        if (promise != null) {
            QuicheQuicChannel.this.connectPromise = null;
            promise.tryFailure(new QuicClosedChannelException(this.connectionCloseEvent));
        }
    }

    void forceClose() {
        unsafe().close(voidPromise());
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
    public Future<QuicStreamChannel> createStream(QuicStreamType type, @Nullable ChannelHandler handler,
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
    @Nullable
    protected QuicConnectionAddress localAddress0() {
        QuicheQuicConnection connection = this.connection;
        return connection == null ? null : connection.sourceId();
    }

    @Override
    @Nullable
    protected QuicConnectionAddress remoteAddress0() {
        QuicheQuicConnection connection = this.connection;
        return connection == null ? null : connection.destinationId();
    }

    @Override
    @Nullable
    public QuicConnectionAddress localAddress() {
        // Override so we never cache as the sourceId() can change over life-time.
        return localAddress0();
    }

    @Override
    @Nullable
    public QuicConnectionAddress remoteAddress() {
        // Override so we never cache as the destinationId() can change over life-time.
        return remoteAddress0();
    }

    @Override
    @Nullable
    public SocketAddress localSocketAddress() {
        return local;
    }

    @Override
    @Nullable
    public SocketAddress remoteSocketAddress() {
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
        if (state == ChannelState.CLOSED) {
            return;
        }
        state = ChannelState.CLOSED;

        QuicheQuicConnection conn = this.connection;
        if (conn == null || conn.isFreed()) {
            if (closeData != null) {
                closeData.reason.release();
                closeData = null;
            }
            failPendingConnectPromise();
            return;
        }

        // Call connectionSend() so we ensure we send all that is queued before we close the channel
        SendResult sendResult = connectionSend(conn);

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

        failPendingConnectPromise();
        try {
            int res = Quiche.quiche_conn_close(conn.address(), app, err,
                    Quiche.readerMemoryAddress(reason), reason.readableBytes());
            if (res < 0 && res != Quiche.QUICHE_ERR_DONE) {
                throw Quiche.convertToException(res);
            }
            // As we called quiche_conn_close(...) we need to ensure we will call quiche_conn_send(...) either
            // now or we will do so once we see the channelReadComplete event.
            //
            // See https://docs.rs/quiche/0.6.0/quiche/struct.Connection.html#method.close
            if (connectionSend(conn) == SendResult.SOME) {
                sendResult = SendResult.SOME;
            }
        } finally {

            // making sure that connection statistics is available
            // even after channel is closed
            statsAtClose = collectStats0(conn, eventLoop().newPromise());
            try {
                timedOut = Quiche.quiche_conn_is_timed_out(conn.address());

                closeStreams();
                if (finBuffer != null) {
                    finBuffer.release();
                    finBuffer = null;
                }
            } finally {
                if (sendResult == SendResult.SOME) {
                    // As this is the close let us flush it asap.
                    forceFlushParent();
                } else {
                    flushParent();
                }
                conn.free();
                if (freeTask != null) {
                    freeTask.accept(this);
                }
                timeoutHandler.cancel();

                local = null;
                remote = null;
            }
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
        throw new UnsupportedOperationException("Unsupported message type: " + StringUtil.simpleClassName(msg));
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer channelOutboundBuffer) throws Exception {
        if (!supportsDatagram) {
            throw new UnsupportedOperationException("Datagram extension is not supported");
        }
        boolean sendSomething = false;
        boolean retry = false;
        QuicheQuicConnection conn = connection;
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
                        res = sendDatagram(conn, tmpBuffer);
                    } finally {
                        tmpBuffer.release();
                    }
                } else {
                    res = sendDatagram(conn, buffer);
                }
                if (res >= 0) {
                    channelOutboundBuffer.remove();
                    sendSomething = true;
                    retry = false;
                } else {
                    if (res == Quiche.QUICHE_ERR_BUFFER_TOO_SHORT) {
                        retry = false;
                        channelOutboundBuffer.remove(new BufferUnderflowException());
                    } else if (res == Quiche.QUICHE_ERR_INVALID_STATE) {
                        throw new UnsupportedOperationException("Remote peer does not support Datagram extension");
                    } else if (res == Quiche.QUICHE_ERR_DONE) {
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
                        if (connectionSend(conn) != SendResult.NONE) {
                            forceFlushParent();
                        }
                        // Let's try again to write the message.
                        retry = true;
                    } else {
                        throw Quiche.convertToException(res);
                    }
                }
            }
        } finally {
            if (sendSomething && connectionSend(conn) != SendResult.NONE) {
                flushParent();
            }
        }
    }

    private static int sendDatagram(QuicheQuicConnection conn, ByteBuf buf) throws ClosedChannelException {
        return Quiche.quiche_conn_dgram_send(connectionAddressChecked(conn),
                Quiche.readerMemoryAddress(buf), buf.readableBytes());
    }

    @Override
    public QuicChannelConfig config() {
        return config;
    }

    @Override
    public boolean isOpen() {
        return state != ChannelState.CLOSED;
    }

    @Override
    public boolean isActive() {
        return state == ChannelState.ACTIVE;
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    /**
     * This may call {@link #flush()} on the parent channel if needed. The flush may be delayed until the read loop
     * is over.
     */
    private void flushParent() {
        if (!inFireChannelReadCompleteQueue) {
            forceFlushParent();
        }
    }

    /**
     * Call {@link #flush()} on the parent channel.
     */
    private void forceFlushParent() {
        parent().flush();
    }

    private static long connectionAddressChecked(@Nullable QuicheQuicConnection conn) throws ClosedChannelException {
        if (conn == null || conn.isFreed()) {
            throw new ClosedChannelException();
        }
        return conn.address();
    }

    boolean freeIfClosed() {
        QuicheQuicConnection conn = connection;
        if (conn == null || conn.isFreed()) {
            return true;
        }
        if (conn.isClosed()) {
            unsafe().close(newPromise());
            return true;
        }
        return false;
    }

    private void closeStreams() {
        if (streams.isEmpty()) {
            return;
        }
        final ClosedChannelException closedChannelException;
        if (isTimedOut()) {
            // Close the streams because of a timeout.
            closedChannelException = new QuicTimeoutClosedChannelException();
        } else {
            closedChannelException = new ClosedChannelException();
        }
        // Make a copy to ensure we not run into a situation when we change the underlying iterator from
        // another method and so run in an assert error.
        for (QuicheQuicStreamChannel stream: streams.values().toArray(new QuicheQuicStreamChannel[0])) {
            stream.unsafe().close(closedChannelException, voidPromise());
        }
        streams.clear();
    }

    void streamPriority(long streamId, byte priority, boolean incremental) throws Exception {
       int res = Quiche.quiche_conn_stream_priority(connectionAddressChecked(connection), streamId,
               priority, incremental);
       if (res < 0 && res != Quiche.QUICHE_ERR_DONE) {
           throw Quiche.convertToException(res);
       }
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

    void streamShutdown(long streamId, boolean read, boolean write, int err, ChannelPromise promise) {
        QuicheQuicConnection conn = this.connection;
        final long connectionAddress;
        try {
            connectionAddress = connectionAddressChecked(conn);
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
        if (connectionSend(conn) != SendResult.NONE) {
            // Force the flush so the shutdown can be seen asap.
            forceFlushParent();
        }
        if (res < 0 && res != Quiche.QUICHE_ERR_DONE) {
            promise.setFailure(Quiche.convertToException(res));
        } else {
            promise.setSuccess();
        }
    }

    void streamSendFin(long streamId) throws Exception {
        QuicheQuicConnection conn = connection;
        try {
            // Just write an empty buffer and set fin to true.
            int res = streamSend0(conn, streamId, Unpooled.EMPTY_BUFFER, true);
            if (res < 0 && res != Quiche.QUICHE_ERR_DONE) {
                throw Quiche.convertToException(res);
            }
        } finally {
            // As we called quiche_conn_stream_send(...) we need to ensure we will call quiche_conn_send(...) either
            // now or we will do so once we see the channelReadComplete event.
            //
            // See https://docs.rs/quiche/0.6.0/quiche/struct.Connection.html#method.send
            if (connectionSend(conn) != SendResult.NONE) {
                flushParent();
            }
        }
    }

    int streamSend(long streamId, ByteBuf buffer, boolean fin) throws ClosedChannelException {
        QuicheQuicConnection conn = connection;
        if (buffer.nioBufferCount() == 1) {
            return streamSend0(conn, streamId, buffer, fin);
        }
        ByteBuffer[] nioBuffers  = buffer.nioBuffers();
        int lastIdx = nioBuffers.length - 1;
        int res = 0;
        for (int i = 0; i < lastIdx; i++) {
            ByteBuffer nioBuffer = nioBuffers[i];
            while (nioBuffer.hasRemaining()) {
                int localRes = streamSend(conn, streamId, nioBuffer, false);
                if (localRes <= 0) {
                    return res;
                }
                res += localRes;

                nioBuffer.position(nioBuffer.position() + localRes);
            }
        }
        int localRes = streamSend(conn, streamId, nioBuffers[lastIdx], fin);
        if (localRes > 0) {
            res += localRes;
        }
        return res;
    }

    void connectionSendAndFlush() {
        if (inFireChannelReadCompleteQueue || (reantranceGuard & IN_HANDLE_WRITABLE_STREAMS) != 0) {
            return;
        }
        if (connectionSend(connection) != SendResult.NONE) {
            flushParent();
        }
    }

    private int streamSend0(QuicheQuicConnection conn, long streamId, ByteBuf buffer, boolean fin)
            throws ClosedChannelException {
        return Quiche.quiche_conn_stream_send(connectionAddressChecked(conn), streamId,
                Quiche.readerMemoryAddress(buffer), buffer.readableBytes(), fin);
    }

    private int streamSend(QuicheQuicConnection conn, long streamId, ByteBuffer buffer, boolean fin)
            throws ClosedChannelException {
        return Quiche.quiche_conn_stream_send(connectionAddressChecked(conn), streamId,
                Quiche.memoryAddressWithPosition(buffer), buffer.remaining(), fin);
    }

    StreamRecvResult streamRecv(long streamId, ByteBuf buffer) throws Exception {
        QuicheQuicConnection conn = connection;
        long connAddr = connectionAddressChecked(conn);
        if (finBuffer == null) {
            finBuffer = alloc().directBuffer(1);
        }
        int writerIndex = buffer.writerIndex();
        int recvLen = Quiche.quiche_conn_stream_recv(connAddr, streamId,
                Quiche.writerMemoryAddress(buffer), buffer.writableBytes(), Quiche.writerMemoryAddress(finBuffer));
        if (recvLen == Quiche.QUICHE_ERR_DONE) {
            return StreamRecvResult.DONE;
        } else if (recvLen < 0) {
            throw Quiche.convertToException(recvLen);
        }
        buffer.writerIndex(writerIndex + recvLen);
        return finBuffer.getBoolean(0) ? StreamRecvResult.FIN : StreamRecvResult.OK;
    }

    /**
     * Receive some data on a QUIC connection.
     */
    void recv(InetSocketAddress sender, InetSocketAddress recipient, ByteBuf buffer) {
        ((QuicChannelUnsafe) unsafe()).connectionRecv(sender, recipient, buffer);
    }

    /**
     * Return all source connection ids that are retired and so should be removed to map to the channel.
     *
     * @return retired ids.
     */
    List<ByteBuffer> retiredSourceConnectionId() {
        QuicheQuicConnection connection = this.connection;
        if (connection == null || connection.isFreed()) {
            return Collections.emptyList();
        }
        long connAddr = connection.address();
        assert connAddr != -1;
        List<ByteBuffer> retiredSourceIds = null;
        for (;;) {
            byte[] retired = Quiche.quiche_conn_retired_scid_next(connAddr);
            if (retired == null) {
                break;
            }
            if (retiredSourceIds == null) {
                retiredSourceIds = new ArrayList<>();
            }
            ByteBuffer retiredId = ByteBuffer.wrap(retired);
            retiredSourceIds.add(retiredId);
            sourceConnectionIds.remove(retiredId);
        }
        if (retiredSourceIds == null) {
            return Collections.emptyList();
        }
        return retiredSourceIds;
    }

    List<ByteBuffer> newSourceConnectionIds() {
        if (connectionIdAddressGenerator != null && resetTokenGenerator != null) {
            QuicheQuicConnection connection = this.connection;
            if (connection == null || connection.isFreed()) {
                return Collections.emptyList();
            }
            long connAddr = connection.address();
            // Generate all extra source ids that we can provide. This will cause frames that need to be sent. Which
            // is the reason why we might need to call connectionSendAndFlush().
            int left = Quiche.quiche_conn_scids_left(connAddr);
            if (left > 0) {
                QuicConnectionAddress sourceAddr = connection.sourceId();
                if (sourceAddr == null) {
                    return Collections.emptyList();
                }
                List<ByteBuffer> generatedIds = new ArrayList<>(left);
                boolean sendAndFlush = false;
                ByteBuffer key = sourceAddr.id();
                ByteBuf connIdBuffer = alloc().directBuffer(key.remaining());

                byte[] resetTokenArray = new byte[Quic.RESET_TOKEN_LEN];
                try {
                    do {
                        ByteBuffer srcId = connectionIdAddressGenerator.newId(key.duplicate(), key.remaining())
                                .asReadOnlyBuffer();
                        connIdBuffer.clear();
                        connIdBuffer.writeBytes(srcId.duplicate());
                        ByteBuffer resetToken = resetTokenGenerator.newResetToken(srcId.duplicate());
                        resetToken.get(resetTokenArray);
                        long result = Quiche.quiche_conn_new_scid(
                                connAddr, Quiche.memoryAddress(connIdBuffer, 0, connIdBuffer.readableBytes()),
                                connIdBuffer.readableBytes(), resetTokenArray, false, -1);
                        if (result < 0) {
                            break;
                        }
                        sendAndFlush = true;
                        generatedIds.add(srcId.duplicate());
                        sourceConnectionIds.add(srcId);
                    } while (--left > 0);
                } finally {
                    connIdBuffer.release();
                }

                if (sendAndFlush) {
                    connectionSendAndFlush();
                }
                return generatedIds;
            }
        }
        return Collections.emptyList();
    }

    void writable() {
        QuicheQuicConnection conn = connection;
        SendResult result = connectionSend(conn);
        handleWritableStreams(conn);
        if (connectionSend(conn) == SendResult.SOME) {
            result = SendResult.SOME;
        }
        if (result == SendResult.SOME) {
            // The writability changed so lets flush as fast as possible.
            forceFlushParent();
        }
        freeIfClosed();
    }

    int streamCapacity(long streamId) {
        QuicheQuicConnection conn = connection;
        if (conn.isClosed()) {
            return 0;
        }
        return Quiche.quiche_conn_stream_capacity(conn.address(), streamId);
    }

    private boolean handleWritableStreams(QuicheQuicConnection conn) {
        if (conn.isFreed()) {
            return false;
        }
        reantranceGuard |= IN_HANDLE_WRITABLE_STREAMS;
        try {
            long connAddr = conn.address();
            boolean mayNeedWrite = false;

            if (Quiche.quiche_conn_is_established(connAddr) ||
                    Quiche.quiche_conn_is_in_early_data(connAddr)) {
                long writableIterator = Quiche.quiche_conn_writable(connAddr);

                int totalWritable = 0;
                try {
                    // For streams we always process all streams when at least on read was requested.
                    for (;;) {
                        int writable = Quiche.quiche_stream_iter_next(
                                writableIterator, writableStreams);
                        for (int i = 0; i < writable; i++) {
                            long streamId = writableStreams[i];
                            QuicheQuicStreamChannel streamChannel = streams.get(streamId);
                            if (streamChannel != null) {
                                int capacity = Quiche.quiche_conn_stream_capacity(connAddr, streamId);
                                if (streamChannel.writable(capacity)) {
                                    mayNeedWrite = true;
                                }
                            }
                        }
                        if (writable > 0) {
                            totalWritable += writable;
                        }
                        if (writable < writableStreams.length) {
                            // We did handle all writable streams.
                            break;
                        }
                    }
                } finally {
                    Quiche.quiche_stream_iter_free(writableIterator);
                }
                writableStreams = growIfNeeded(writableStreams, totalWritable);
            }
            return mayNeedWrite;
        } finally {
            reantranceGuard &= ~IN_HANDLE_WRITABLE_STREAMS;
        }
    }

    /**
     * Called once we receive a channelReadComplete event. This method will take care of calling
     * {@link ChannelPipeline#fireChannelReadComplete()} if needed and also to handle pending flushes of
     * writable {@link QuicheQuicStreamChannel}s.
     */
    void recvComplete() {
        try {
            QuicheQuicConnection conn = connection;
            if (conn.isFreed()) {
                // Ensure we flush all pending writes.
                forceFlushParent();
                return;
            }
            fireChannelReadCompleteIfNeeded();

            // If we had called recv we need to ensure we call send as well.
            // See https://docs.rs/quiche/0.6.0/quiche/struct.Connection.html#method.send
            connectionSend(conn);

            // We are done with the read loop, flush all pending writes now.
            forceFlushParent();
            freeIfClosed();
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

    private void fireExceptionEvents(QuicheQuicConnection conn, Throwable cause) {
        if (cause instanceof SSLHandshakeException) {
            notifyAboutHandshakeCompletionIfNeeded(conn, (SSLHandshakeException) cause);
        }
        pipeline().fireExceptionCaught(cause);
    }

    private boolean runTasksDirectly() {
        return sslTaskExecutor == null || sslTaskExecutor == ImmediateExecutor.INSTANCE ||
                sslTaskExecutor == ImmediateEventExecutor.INSTANCE;
    }

    private void runAllTaskSend(QuicheQuicConnection conn, Runnable task) {
        sslTaskExecutor.execute(decorateTaskSend(conn, task));
    }

    private void runAll(QuicheQuicConnection conn, Runnable task) {
        do {
            task.run();
        } while ((task = conn.sslTask()) != null);
    }

    private Runnable decorateTaskSend(QuicheQuicConnection conn, Runnable task) {
        return () -> {
            try {
                runAll(conn, task);
            } finally {
                // Move back to the EventLoop.
                eventLoop().execute(() -> {
                    // Call connection send to continue handshake if needed.
                    if (connectionSend(conn) != SendResult.NONE) {
                        forceFlushParent();
                    }
                    freeIfClosed();
                });
            }
        };
    }

    private SendResult connectionSendSegments(QuicheQuicConnection conn,
                                              SegmentedDatagramPacketAllocator segmentedDatagramPacketAllocator) {
        if (conn.isClosed()) {
            return SendResult.NONE;
        }
        List<ByteBuf> bufferList = new ArrayList<>(segmentedDatagramPacketAllocator.maxNumSegments());
        long connAddr = conn.address();
        int maxDatagramSize = Quiche.quiche_conn_max_send_udp_payload_size(connAddr);
        SendResult sendResult = SendResult.NONE;
        boolean close = false;
        try {
            for (;;) {
                int len = calculateSendBufferLength(connAddr, maxDatagramSize);
                ByteBuf out = alloc().directBuffer(len);

                ByteBuffer sendInfo = conn.nextSendInfo();
                InetSocketAddress sendToAddress = this.remote;

                int writerIndex = out.writerIndex();
                int written = Quiche.quiche_conn_send(
                        connAddr, Quiche.writerMemoryAddress(out), out.writableBytes(),
                        Quiche.memoryAddressWithPosition(sendInfo));
                if (written == 0) {
                    out.release();
                    // No need to create a new datagram packet. Just try again.
                    continue;
                }
                final boolean done;
                if (written < 0) {
                    done = true;
                    if (written != Quiche.QUICHE_ERR_DONE) {
                        close = Quiche.shouldClose(written);
                        Exception e = Quiche.convertToException(written);
                        if (!tryFailConnectPromise(e)) {
                            // Only fire through the pipeline if this does not fail the connect promise.
                            fireExceptionEvents(conn, e);
                        }
                    }
                } else {
                    done = false;
                }
                int size = bufferList.size();
                if (done) {
                    // We are done, release the buffer and send what we did build up so far.
                    out.release();

                    switch (size) {
                        case 0:
                            // Nothing more to write.
                            break;
                        case 1:
                            // We can write a normal datagram packet.
                            parent().write(new DatagramPacket(bufferList.get(0), sendToAddress));
                            sendResult = SendResult.SOME;
                            break;
                        default:
                            int segmentSize = segmentSize(bufferList);
                            ByteBuf compositeBuffer = Unpooled.wrappedBuffer(bufferList.toArray(new ByteBuf[0]));
                            // We had more than one buffer, create a segmented packet.
                            parent().write(segmentedDatagramPacketAllocator.newPacket(
                                    compositeBuffer, segmentSize, sendToAddress));
                            sendResult = SendResult.SOME;
                            break;
                    }
                    bufferList.clear();
                    if (close) {
                        sendResult = SendResult.CLOSE;
                    }
                    return sendResult;
                }
                out.writerIndex(writerIndex + written);

                int segmentSize = -1;
                if (conn.isSendInfoChanged()) {
                    // Change the cached address and let the user know there was a connection migration.
                    remote = QuicheSendInfo.getToAddress(sendInfo);
                    local = QuicheSendInfo.getFromAddress(sendInfo);

                    if (size > 0) {
                        // We have something in the out list already, we need to send this now and so we set the
                        // segmentSize.
                        segmentSize = segmentSize(bufferList);
                    }
                } else if (size > 0) {
                    int lastReadable = segmentSize(bufferList);
                    // Check if we either need to send now because the last buffer we added has a smaller size then this
                    // one or if we reached the maximum number of segments that we can send.
                    if (lastReadable != out.readableBytes() ||
                            size == segmentedDatagramPacketAllocator.maxNumSegments()) {
                        segmentSize = lastReadable;
                    }
                }

                // If the segmentSize is not -1 we know we need to send now what was in the out list.
                if (segmentSize != -1) {
                    final boolean stop;
                    if (size == 1) {
                        // Only one buffer in the out list, there is no need to use segments.
                        stop = writePacket(new DatagramPacket(
                                bufferList.get(0), sendToAddress), maxDatagramSize, len);
                    } else {
                        // Create a packet with segments in.
                        ByteBuf compositeBuffer = Unpooled.wrappedBuffer(bufferList.toArray(new ByteBuf[0]));
                        stop = writePacket(segmentedDatagramPacketAllocator.newPacket(
                                compositeBuffer, segmentSize, sendToAddress), maxDatagramSize, len);
                    }
                    bufferList.clear();
                    sendResult = SendResult.SOME;

                    if (stop) {
                        // Nothing left in the window, continue later. That said we still need to also
                        // write the previous filled out buffer as otherwise we would either leak or need
                        // to drop it and so produce some loss.
                        if (out.isReadable()) {
                            parent().write(new DatagramPacket(out, sendToAddress));
                        } else {
                            out.release();
                        }
                        if (close) {
                            sendResult = SendResult.CLOSE;
                        }
                        return sendResult;
                    }
                }
                // Let's add a touch with the bufferList as a hint. This will help us to debug leaks if there
                // are any.
                out.touch(bufferList);
                // store for later, so we can make use of segments.
                bufferList.add(out);
            }
        } finally {
            // NOOP
        }
    }

    private static int segmentSize(List<ByteBuf> bufferList) {
        assert !bufferList.isEmpty();
        int size = bufferList.size();
        return bufferList.get(size - 1).readableBytes();
    }

    private SendResult connectionSendSimple(QuicheQuicConnection conn) {
        if (conn.isClosed()) {
            return SendResult.NONE;
        }
        long connAddr = conn.address();
        SendResult sendResult = SendResult.NONE;
        boolean close = false;
        int maxDatagramSize = Quiche.quiche_conn_max_send_udp_payload_size(connAddr);
        for (;;) {
            ByteBuffer sendInfo = conn.nextSendInfo();

            int len = calculateSendBufferLength(connAddr, maxDatagramSize);
            ByteBuf out = alloc().directBuffer(len);
            int writerIndex = out.writerIndex();

            int written = Quiche.quiche_conn_send(
                    connAddr, Quiche.writerMemoryAddress(out), out.writableBytes(),
                    Quiche.memoryAddressWithPosition(sendInfo));

            if (written == 0) {
                // No need to create a new datagram packet. Just release and try again.
                out.release();
                continue;
            }
            if (written < 0) {
                out.release();
                if (written != Quiche.QUICHE_ERR_DONE) {
                    close = Quiche.shouldClose(written);

                    Exception e = Quiche.convertToException(written);
                    if (!tryFailConnectPromise(e)) {
                        fireExceptionEvents(conn, e);
                    }
                }
                break;
            }
            if (conn.isSendInfoChanged()) {
                // Change the cached address
                remote = QuicheSendInfo.getToAddress(sendInfo);
                local = QuicheSendInfo.getFromAddress(sendInfo);
            }
            out.writerIndex(writerIndex + written);
            boolean stop = writePacket(new DatagramPacket(out, remote), maxDatagramSize, len);
            sendResult = SendResult.SOME;
            if (stop) {
                // Nothing left in the window, continue later
                break;
            }
        }
        if (close) {
            sendResult = SendResult.CLOSE;
        }
        return sendResult;
    }

    private boolean writePacket(DatagramPacket packet, int maxDatagramSize, int len) {
        ChannelFuture future = parent().write(packet);
        if (isSendWindowUsed(maxDatagramSize, len)) {
            // Nothing left in the window, continue later
            future.addListener(continueSendingListener);
            return true;
        }
        return false;
    }

    private static boolean isSendWindowUsed(int maxDatagramSize, int len) {
        return len < maxDatagramSize;
    }

    private static int calculateSendBufferLength(long connAddr, int maxDatagramSize) {
        int len = Math.min(maxDatagramSize, Quiche.quiche_conn_send_quantum(connAddr));
        if (len <= 0) {
            // If there is no room left we just return some small number to reduce the risk of packet drop
            // while still be able to attach the listener to the write future.
            // We use the value of 8 because such an allocation will be cheap to serve from the
            // PooledByteBufAllocator while still serve our need.
            return 8;
        }
        return len;
    }

    /**
     * Write datagrams if needed and return {@code true} if something was written and we need to call
     * {@link Channel#flush()} at some point.
     */
    private SendResult connectionSend(QuicheQuicConnection conn) {
        if (conn.isFreed()) {
            return SendResult.NONE;
        }
        if ((reantranceGuard & IN_CONNECTION_SEND) != 0) {
            // Let's notify about early data if needed.
            notifyEarlyDataReadyIfNeeded(conn);
            return SendResult.NONE;
        }

        reantranceGuard |= IN_CONNECTION_SEND;
        try {
            SendResult sendResult;
            SegmentedDatagramPacketAllocator segmentedDatagramPacketAllocator =
                    config.getSegmentedDatagramPacketAllocator();
            if (segmentedDatagramPacketAllocator.maxNumSegments() > 0) {
                sendResult = connectionSendSegments(conn, segmentedDatagramPacketAllocator);
            } else {
                sendResult = connectionSendSimple(conn);
            }

            // Process / schedule all tasks that were created.

            Runnable task = conn.sslTask();
            if (task != null) {
                if (runTasksDirectly()) {
                    // Consume all tasks
                    do {
                        task.run();
                        // Notify about early data ready if needed.
                        notifyEarlyDataReadyIfNeeded(conn);
                    } while ((task = conn.sslTask()) != null);

                    // Let's try again sending after we did process all tasks.
                    // We schedule this on the EventLoop as otherwise we will get into trouble with re-entrance.
                    eventLoop().execute(new Runnable() {
                        @Override
                        public void run() {
                            // Call connection send to continue handshake if needed.
                            if (connectionSend(conn) != SendResult.NONE) {
                                forceFlushParent();
                            }
                            freeIfClosed();
                        }
                    });
                } else {
                    runAllTaskSend(conn, task);
                }
            } else {
                // Notify about early data ready if needed.
                notifyEarlyDataReadyIfNeeded(conn);
            }

            // Whenever we called connection_send we should also schedule the timer if needed.
            timeoutHandler.scheduleTimeout();
            return sendResult;
        } finally {
            reantranceGuard &= ~IN_CONNECTION_SEND;
        }
    }

    private final class QuicChannelUnsafe extends AbstractChannel.AbstractUnsafe {

        void connectStream(QuicStreamType type, @Nullable ChannelHandler handler,
                           Promise<QuicStreamChannel> promise) {
            if (!promise.setUncancellable()) {
                return;
            }
            long streamId = idGenerator.nextStreamId(type == QuicStreamType.BIDIRECTIONAL);

            try {
                int res = streamSend0(connection, streamId, Unpooled.EMPTY_BUFFER, false);
                if (res < 0 && res != Quiche.QUICHE_ERR_DONE) {
                    throw Quiche.convertToException(res);
                }
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
            if (!channelPromise.setUncancellable()) {
                return;
            }
            if (server) {
                channelPromise.setFailure(new UnsupportedOperationException());
                return;
            }

            if (connectPromise != null) {
                channelPromise.setFailure(new ConnectionPendingException());
                return;
            }

            if (remote instanceof QuicConnectionAddress) {
                if (!sourceConnectionIds.isEmpty()) {
                    // If a key is assigned we know this channel was already connected.
                    channelPromise.setFailure(new AlreadyConnectedException());
                    return;
                }

                connectAddress = (QuicConnectionAddress) remote;
                connectPromise = channelPromise;

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

                parent().connect(new QuicheQuicChannelAddress(QuicheQuicChannel.this))
                        .addListener(f -> {
                            ChannelPromise connectPromise = QuicheQuicChannel.this.connectPromise;
                            if (connectPromise != null && !f.isSuccess()) {
                                connectPromise.tryFailure(f.cause());
                                // close everything after notify about failure.
                                unsafe().closeForcibly();
                            }
                        });
                return;
            }

            channelPromise.setFailure(new UnsupportedOperationException());
        }

        private void fireConnectCloseEventIfNeeded(QuicheQuicConnection conn) {
            if (connectionCloseEvent == null && !conn.isFreed()) {
                connectionCloseEvent = Quiche.quiche_conn_peer_error(conn.address());
                if (connectionCloseEvent != null) {
                    pipeline().fireUserEventTriggered(connectionCloseEvent);
                }
            }
        }

        void connectionRecv(InetSocketAddress sender, InetSocketAddress recipient, ByteBuf buffer) {
            QuicheQuicConnection conn = QuicheQuicChannel.this.connection;
            if (conn.isFreed()) {
                return;
            }
            int bufferReadable = buffer.readableBytes();
            if (bufferReadable == 0) {
                // Nothing to do here. Just return...
                // See also https://github.com/cloudflare/quiche/issues/817
                return;
            }

            reantranceGuard |= IN_RECV;
            boolean close = false;
            try {
                ByteBuf tmpBuffer = null;
                // We need to make a copy if the buffer is read only as recv(...) may modify the input buffer as well.
                // See https://docs.rs/quiche/0.6.0/quiche/struct.Connection.html#method.recv
                if (buffer.isReadOnly()) {
                    tmpBuffer = alloc().directBuffer(buffer.readableBytes());
                    tmpBuffer.writeBytes(buffer);
                    buffer = tmpBuffer;
                }
                long memoryAddress = Quiche.readerMemoryAddress(buffer);

                ByteBuffer recvInfo = conn.nextRecvInfo();
                QuicheRecvInfo.setRecvInfo(recvInfo, sender, recipient);

                remote = sender;
                local = recipient;

                try {
                    do  {
                        // Call quiche_conn_recv(...) until we consumed all bytes or we did receive some error.
                        int res = Quiche.quiche_conn_recv(conn.address(), memoryAddress, bufferReadable,
                                Quiche.memoryAddressWithPosition(recvInfo));
                        final boolean done;
                        if (res < 0) {
                            done = true;
                            if (res != Quiche.QUICHE_ERR_DONE) {
                                close = Quiche.shouldClose(res);
                                Exception e = Quiche.convertToException(res);
                                if (tryFailConnectPromise(e)) {
                                    break;
                                }
                                fireExceptionEvents(conn, e);
                            }
                        } else {
                            done = false;
                        }
                        // Process / schedule all tasks that were created.
                        Runnable task = conn.sslTask();
                        if (task != null) {
                            if (runTasksDirectly()) {
                                // Consume all tasks
                                do {
                                    task.run();
                                } while ((task = conn.sslTask()) != null);
                                processReceived(conn);
                            } else {
                                runAllTaskRecv(conn, task);
                            }
                        } else {
                            processReceived(conn);
                        }

                        if (done) {
                            break;
                        }
                        memoryAddress += res;
                        bufferReadable -= res;
                    } while (bufferReadable > 0 && !conn.isFreed());
                } finally {
                    buffer.skipBytes((int) (memoryAddress - Quiche.readerMemoryAddress(buffer)));
                    if (tmpBuffer != null) {
                        tmpBuffer.release();
                    }
                }
                if (close) {
                    // Let's close now as there is no way to recover
                    unsafe().close(newPromise());
                }
            } finally {
                reantranceGuard &= ~IN_RECV;
            }
        }

        private void processReceived(QuicheQuicConnection conn) {
            // Handle pending channelActive if needed.
            if (handlePendingChannelActive(conn)) {
                // Connection was closed right away.
                return;
            }

            notifyAboutHandshakeCompletionIfNeeded(conn, null);
            fireConnectCloseEventIfNeeded(conn);

            if (conn.isFreed()) {
                return;
            }

            long connAddr = conn.address();
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

                handlePathEvents(conn);

                if (handleWritableStreams(conn)) {
                    // Some data was produced, let's flush.
                    flushParent();
                }

                datagramReadable = true;
                streamReadable = true;

                recvDatagram(conn);
                recvStream(conn);
            }
        }

        private void handlePathEvents(QuicheQuicConnection conn) {
            long event;
            while (!conn.isFreed() && (event = Quiche.quiche_conn_path_event_next(conn.address())) > 0) {
                try {
                    int type = Quiche.quiche_path_event_type(event);

                    if (type == Quiche.QUICHE_PATH_EVENT_NEW) {
                        Object[] ret = Quiche.quiche_path_event_new(event);
                        InetSocketAddress local = (InetSocketAddress) ret[0];
                        InetSocketAddress peer = (InetSocketAddress) ret[1];
                        pipeline().fireUserEventTriggered(new QuicPathEvent.New(local, peer));
                    } else if (type == Quiche.QUICHE_PATH_EVENT_VALIDATED) {
                        Object[] ret = Quiche.quiche_path_event_validated(event);
                        InetSocketAddress local = (InetSocketAddress) ret[0];
                        InetSocketAddress peer = (InetSocketAddress) ret[1];
                        pipeline().fireUserEventTriggered(new QuicPathEvent.Validated(local, peer));
                    } else if (type == Quiche.QUICHE_PATH_EVENT_FAILED_VALIDATION) {
                        Object[] ret = Quiche.quiche_path_event_failed_validation(event);
                        InetSocketAddress local = (InetSocketAddress) ret[0];
                        InetSocketAddress peer = (InetSocketAddress) ret[1];
                        pipeline().fireUserEventTriggered(new QuicPathEvent.FailedValidation(local, peer));
                    } else if (type == Quiche.QUICHE_PATH_EVENT_CLOSED) {
                        Object[] ret = Quiche.quiche_path_event_closed(event);
                        InetSocketAddress local = (InetSocketAddress) ret[0];
                        InetSocketAddress peer = (InetSocketAddress) ret[1];
                        pipeline().fireUserEventTriggered(new QuicPathEvent.Closed(local, peer));
                    } else if (type == Quiche.QUICHE_PATH_EVENT_REUSED_SOURCE_CONNECTION_ID) {
                        Object[] ret = Quiche.quiche_path_event_reused_source_connection_id(event);
                        Long seq = (Long) ret[0];
                        InetSocketAddress localOld = (InetSocketAddress) ret[1];
                        InetSocketAddress peerOld = (InetSocketAddress) ret[2];
                        InetSocketAddress local = (InetSocketAddress) ret[3];
                        InetSocketAddress peer = (InetSocketAddress) ret[4];
                        pipeline().fireUserEventTriggered(
                                new QuicPathEvent.ReusedSourceConnectionId(seq, localOld, peerOld, local, peer));
                    } else if (type == Quiche.QUICHE_PATH_EVENT_PEER_MIGRATED) {
                        Object[] ret = Quiche.quiche_path_event_peer_migrated(event);
                        InetSocketAddress local = (InetSocketAddress) ret[0];
                        InetSocketAddress peer = (InetSocketAddress) ret[1];
                        pipeline().fireUserEventTriggered(new QuicPathEvent.PeerMigrated(local, peer));
                    }
                } finally {
                    Quiche.quiche_path_event_free(event);
                }
            }
        }

        private void runAllTaskRecv(QuicheQuicConnection conn, Runnable task) {
            sslTaskExecutor.execute(decorateTaskRecv(conn, task));
        }

        private Runnable decorateTaskRecv(QuicheQuicConnection conn, Runnable task) {
            return () -> {
                try {
                    runAll(conn, task);
                } finally {
                    // Move back to the EventLoop.
                    eventLoop().execute(() -> {
                        if (!conn.isFreed()) {
                            processReceived(conn);

                            // Call connection send to continue handshake if needed.
                            if (connectionSend(conn) != SendResult.NONE) {
                                forceFlushParent();
                            }

                            freeIfClosed();
                        }
                    });
                }
            };
        }
        void recv() {
            QuicheQuicConnection conn = connection;
            if ((reantranceGuard & IN_RECV) != 0 || conn.isFreed()) {
                return;
            }

            long connAddr = conn.address();
            // Check if we can read anything yet.
            if (!Quiche.quiche_conn_is_established(connAddr) &&
                    !Quiche.quiche_conn_is_in_early_data(connAddr)) {
                return;
            }

            reantranceGuard |= IN_RECV;
            try {
                recvDatagram(conn);
                recvStream(conn);
            } finally {
                fireChannelReadCompleteIfNeeded();
                reantranceGuard &= ~IN_RECV;
            }
        }

        private void recvStream(QuicheQuicConnection conn) {
            if (conn.isFreed()) {
                return;
            }
            long connAddr = conn.address();
            long readableIterator = Quiche.quiche_conn_readable(connAddr);
            int totalReadable = 0;
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
                                // We did consume all readable streams.
                                streamReadable = false;
                                break;
                            }
                            if (readable > 0) {
                                totalReadable += readable;
                            }
                        }
                    }
                } finally {
                    Quiche.quiche_stream_iter_free(readableIterator);
                }
                readableStreams = growIfNeeded(readableStreams, totalReadable);
            }
        }

        private void recvDatagram(QuicheQuicConnection conn) {
            if (!supportsDatagram) {
                return;
            }
            while (recvDatagramPending && datagramReadable && !conn.isFreed()) {
                @SuppressWarnings("deprecation")
                RecvByteBufAllocator.Handle recvHandle = recvBufAllocHandle();
                recvHandle.reset(config());

                int numMessagesRead = 0;
                do {
                    long connAddr = conn.address();
                    int len = Quiche.quiche_conn_dgram_recv_front_len(connAddr);
                    if (len == Quiche.QUICHE_ERR_DONE) {
                        datagramReadable = false;
                        return;
                    }

                    ByteBuf datagramBuffer = alloc().directBuffer(len);
                    recvHandle.attemptedBytesRead(datagramBuffer.writableBytes());
                    int writerIndex = datagramBuffer.writerIndex();
                    long memoryAddress = Quiche.writerMemoryAddress(datagramBuffer);

                    int written = Quiche.quiche_conn_dgram_recv(connAddr,
                            memoryAddress, datagramBuffer.writableBytes());
                    if (written < 0) {
                        datagramBuffer.release();
                        if (written == Quiche.QUICHE_ERR_DONE) {
                            // We did consume all datagram packets.
                            datagramReadable = false;
                            break;
                        }
                        pipeline().fireExceptionCaught(Quiche.convertToException(written));
                    }
                    recvHandle.lastBytesRead(written);
                    recvHandle.incMessagesRead(1);
                    numMessagesRead++;
                    datagramBuffer.writerIndex(writerIndex + written);
                    recvDatagramPending = false;
                    fireChannelReadCompletePending = true;

                    pipeline().fireChannelRead(datagramBuffer);
                } while (recvHandle.continueReading() && !conn.isFreed());
                recvHandle.readComplete();

                // Check if we produced any messages.
                if (numMessagesRead > 0) {
                    fireChannelReadCompleteIfNeeded();
                }
            }
        }

        private boolean handlePendingChannelActive(QuicheQuicConnection conn) {
            if (conn.isFreed() || state == ChannelState.CLOSED) {
                return true;
            }
            if (server) {
                if (state == ChannelState.OPEN && Quiche.quiche_conn_is_established(conn.address())) {
                    // We didn't notify before about channelActive... Update state and fire the event.
                    state = ChannelState.ACTIVE;

                    pipeline().fireChannelActive();
                    notifyAboutHandshakeCompletionIfNeeded(conn, null);
                    fireDatagramExtensionEvent(conn);
                }
            } else if (connectPromise != null && Quiche.quiche_conn_is_established(conn.address())) {
                ChannelPromise promise = connectPromise;
                connectPromise = null;
                state = ChannelState.ACTIVE;

                boolean promiseSet = promise.trySuccess();
                pipeline().fireChannelActive();
                notifyAboutHandshakeCompletionIfNeeded(conn, null);
                fireDatagramExtensionEvent(conn);
                if (!promiseSet) {
                    fireConnectCloseEventIfNeeded(conn);
                    this.close(this.voidPromise());
                    return true;
                }
            }
            return false;
        }

        private void fireDatagramExtensionEvent(QuicheQuicConnection conn) {
            if (conn.isClosed()) {
                return;
            }
            long connAddr = conn.address();
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
            streamChannel.writable(streamCapacity(streamId));
            return streamChannel;
        }
    }

    /**
     * Finish the connect operation of a client channel.
     */
    void finishConnect() {
        assert !server;
        assert connection != null;
        if (connectionSend(connection) != SendResult.NONE) {
            flushParent();
        }
    }

    private void notifyEarlyDataReadyIfNeeded(QuicheQuicConnection conn) {
        if (!server && !earlyDataReadyNotified &&
                !conn.isFreed() && Quiche.quiche_conn_is_in_early_data(conn.address())) {
            earlyDataReadyNotified = true;
            pipeline().fireUserEventTriggered(SslEarlyDataReadyEvent.INSTANCE);
        }
    }

    private final class TimeoutHandler implements Runnable {
        private ScheduledFuture<?> timeoutFuture;

        @Override
        public void run() {
            QuicheQuicConnection conn = connection;
            if (conn.isFreed()) {
                return;
            }
            if (!freeIfClosed()) {
                long connAddr = conn.address();
                timeoutFuture = null;
                // Notify quiche there was a timeout.
                Quiche.quiche_conn_on_timeout(connAddr);
                if (!freeIfClosed()) {
                    // We need to call connectionSend when a timeout was triggered.
                    // See https://docs.rs/quiche/0.6.0/quiche/struct.Connection.html#method.send.
                    if (connectionSend(conn) != SendResult.NONE) {
                        flushParent();
                    }
                    boolean closed = freeIfClosed();
                    if (!closed) {
                        // The connection is alive, reschedule.
                        scheduleTimeout();
                    }
                }
            }
        }

        // Schedule timeout.
        // See https://docs.rs/quiche/0.6.0/quiche/#generating-outgoing-packets
        void scheduleTimeout() {
            QuicheQuicConnection conn = connection;
            if (conn.isFreed()) {
                cancel();
                return;
            }
            if (conn.isClosed()) {
                cancel();
                unsafe().close(newPromise());
                return;
            }
            long nanos = Quiche.quiche_conn_timeout_as_nanos(conn.address());
            if (nanos < 0 || nanos == Long.MAX_VALUE) {
                // No timeout needed.
                cancel();
                return;
            }
            if (timeoutFuture == null) {
                timeoutFuture = eventLoop().schedule(this,
                        nanos, TimeUnit.NANOSECONDS);
            } else {
                long remaining = timeoutFuture.getDelay(TimeUnit.NANOSECONDS);
                if (remaining <= 0) {
                    // This means the timer already elapsed. In this case just cancel the future and call run()
                    // directly. This will ensure we correctly call quiche_conn_on_timeout() etc.
                    cancel();
                    run();
                } else if (remaining > nanos) {
                    // The new timeout is smaller then what was scheduled before. Let's cancel the old timeout
                    // and schedule a new one.
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

    private void collectStats0(Promise<QuicConnectionStats> promise) {
        QuicheQuicConnection conn = connection;
        if (conn.isFreed()) {
            promise.setSuccess(statsAtClose);
            return;
        }

        collectStats0(connection, promise);
    }

    @Nullable
    private QuicConnectionStats collectStats0(QuicheQuicConnection connection, Promise<QuicConnectionStats> promise) {
        final long[] stats = Quiche.quiche_conn_stats(connection.address());
        if (stats == null) {
            promise.setFailure(new IllegalStateException("native quiche_conn_stats(...) failed"));
            return null;
        }

        final QuicheQuicConnectionStats connStats =
            new QuicheQuicConnectionStats(stats);
        promise.setSuccess(connStats);
        return connStats;
    }

    @Override
    public Future<QuicConnectionPathStats> collectPathStats(int pathIdx, Promise<QuicConnectionPathStats> promise) {
        if (eventLoop().inEventLoop()) {
            collectPathStats0(pathIdx, promise);
        } else {
            eventLoop().execute(() -> collectPathStats0(pathIdx, promise));
        }
        return promise;
    }

    private void collectPathStats0(int pathIdx, Promise<QuicConnectionPathStats> promise) {
        QuicheQuicConnection conn = connection;
        if (conn.isFreed()) {
            promise.setFailure(new IllegalStateException("Connection is closed"));
            return;
        }

        final Object[] stats = Quiche.quiche_conn_path_stats(connection.address(), pathIdx);
        if (stats == null) {
            promise.setFailure(new IllegalStateException("native quiche_conn_path_stats(...) failed"));
            return;
        }
        promise.setSuccess(new QuicheQuicConnectionPathStats(stats));
    }

    @Override
    public QuicTransportParameters peerTransportParameters() {
        return connection.peerParameters();
    }
}
