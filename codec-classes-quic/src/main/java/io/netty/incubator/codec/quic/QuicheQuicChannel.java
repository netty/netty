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

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import java.io.File;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ConnectionPendingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private final ChannelFutureListener continueSendingListener = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture channelFuture) {
            if (connectionSend()) {
                flushParent();
            }
        }
    };

    private static final ChannelMetadata METADATA = new ChannelMetadata(false);
    private final long[] readableStreams = new long[128];
    private final long[] writableStreams = new long[128];

    private final LongObjectMap<QuicheQuicStreamChannel> streams = new LongObjectHashMap<>();
    private final QuicheQuicChannelConfig config;
    private final boolean server;
    private final QuicStreamIdGenerator idGenerator;
    private final ChannelHandler streamHandler;
    private final Map.Entry<ChannelOption<?>, Object>[] streamOptionsArray;
    private final Map.Entry<AttributeKey<?>, Object>[] streamAttrsArray;
    private final TimeoutHandler timeoutHandler;
    private Executor sslTaskExecutor;

    private boolean inFireChannelReadCompleteQueue;
    private boolean fireChannelReadCompletePending;
    private ByteBuf finBuffer;
    private ChannelPromise connectPromise;
    private ScheduledFuture<?> connectTimeoutFuture;
    private QuicConnectionAddress connectAddress;
    private ByteBuffer key;
    private CloseData closeData;
    private QuicConnectionCloseEvent connectionCloseEvent;
    private QuicConnectionStats statsAtClose;

    private InetSocketAddress local;
    private InetSocketAddress remote;
    private boolean supportsDatagram;
    private boolean recvDatagramPending;
    private boolean datagramReadable;

    private boolean recvStreamPending;
    private boolean streamReadable;
    private boolean handshakeCompletionNotified;
    private boolean earlyDataReadyNotified;

    private int reantranceGuard = 0;
    private static final int IN_RECV = 1 << 1;
    private static final int IN_CONNECTION_SEND = 1 << 2;
    private static final int IN_HANDLE_WRITABLE_STREAMS = 1 << 3;
    private static final int IN_FORCE_CLOSE = 1 << 4;

    private static final int CLOSED = 0;
    private static final int OPEN = 1;
    private static final int ACTIVE = 2;
    private volatile int state;
    private volatile boolean timedOut;
    private volatile String traceId;
    private volatile QuicheQuicConnection connection;
    private volatile QuicConnectionAddress remoteIdAddr;
    private volatile QuicConnectionAddress localIdAdrr;

    private static final AtomicLongFieldUpdater<QuicheQuicChannel> UNI_STREAMS_LEFT_UPDATER =
            AtomicLongFieldUpdater.newUpdater(QuicheQuicChannel.class, "uniStreamsLeft");
    private volatile long uniStreamsLeft;

    private static final AtomicLongFieldUpdater<QuicheQuicChannel> BIDI_STREAMS_LEFT_UPDATER =
            AtomicLongFieldUpdater.newUpdater(QuicheQuicChannel.class, "bidiStreamsLeft");
    private volatile long bidiStreamsLeft;

    private QuicheQuicChannel(Channel parent, boolean server, ByteBuffer key, InetSocketAddress local,
                              InetSocketAddress remote, boolean supportsDatagram, ChannelHandler streamHandler,
                              Map.Entry<ChannelOption<?>, Object>[] streamOptionsArray,
                              Map.Entry<AttributeKey<?>, Object>[] streamAttrsArray,
                              Consumer<QuicheQuicChannel> timeoutTask,
                              Executor sslTaskExecutor) {
        super(parent);
        config = new QuicheQuicChannelConfig(this);
        this.server = server;
        this.idGenerator = new QuicStreamIdGenerator(server);
        this.key = key;
        state = OPEN;

        this.supportsDatagram = supportsDatagram;
        this.local = local;
        this.remote = remote;

        this.streamHandler = streamHandler;
        this.streamOptionsArray = streamOptionsArray;
        this.streamAttrsArray = streamAttrsArray;
        timeoutHandler = new TimeoutHandler(timeoutTask);
        this.sslTaskExecutor = sslTaskExecutor == null ? ImmediateExecutor.INSTANCE : sslTaskExecutor;
    }

    static QuicheQuicChannel forClient(Channel parent, InetSocketAddress local, InetSocketAddress remote,
                                       ChannelHandler streamHandler,
                                       Map.Entry<ChannelOption<?>, Object>[] streamOptionsArray,
                                       Map.Entry<AttributeKey<?>, Object>[] streamAttrsArray) {
        return new QuicheQuicChannel(parent, false, null, local, remote, false, streamHandler,
                streamOptionsArray, streamAttrsArray, null, null);
    }

    static QuicheQuicChannel forServer(Channel parent, ByteBuffer key, InetSocketAddress local,
                                       InetSocketAddress remote,
                                       boolean supportsDatagram, ChannelHandler streamHandler,
                                       Map.Entry<ChannelOption<?>, Object>[] streamOptionsArray,
                                       Map.Entry<AttributeKey<?>, Object>[] streamAttrsArray,
                                       Consumer<QuicheQuicChannel> timeoutTask, Executor sslTaskExecutor) {
        return new QuicheQuicChannel(parent, true, key, local, remote, supportsDatagram,
                streamHandler, streamOptionsArray, streamAttrsArray, timeoutTask,
                sslTaskExecutor);
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

    private void notifyAboutHandshakeCompletionIfNeeded(SSLHandshakeException cause) {
        if (handshakeCompletionNotified) {
            return;
        }
        if (cause != null) {
            pipeline().fireUserEventTriggered(new SslHandshakeCompletionEvent(cause));
            return;
        }
        QuicheQuicConnection connection = this.connection;
        if (connection == null) {
            return;
        }
        switch (connection.engine().getHandshakeStatus()) {
            case NOT_HANDSHAKING:
            case FINISHED:
                handshakeCompletionNotified = true;
                String sniHostname = connection.engine().sniHostname;
                if (sniHostname != null) {
                    connection.engine().sniHostname = null;
                    pipeline().fireUserEventTriggered(new SniCompletionEvent(sniHostname));
                }
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

        connection.initInfo(local, remote);

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

    private void connect(Function<QuicChannel, ? extends QuicSslEngine> engineProvider, Executor sslTaskExecutor,
                         long configAddr, int localConnIdLength,
                         boolean supportsDatagram, ByteBuffer fromSockaddrMemory, ByteBuffer toSockaddrMemory)
            throws Exception {
        assert this.connection == null;
        assert this.traceId == null;
        assert this.key == null;

        this.sslTaskExecutor = sslTaskExecutor;

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

    private void failPendingConnectPromise() {
        ChannelPromise promise = QuicheQuicChannel.this.connectPromise;
        if (promise != null) {
            QuicheQuicChannel.this.connectPromise = null;
            promise.tryFailure(new QuicClosedChannelException(this.connectionCloseEvent));
        }
    }

    void forceClose() {
        if (isConnDestroyed() || (reantranceGuard & IN_FORCE_CLOSE) != 0) {
            // Just return if we already destroyed the underlying connection.
            return;
        }
        reantranceGuard |= IN_FORCE_CLOSE;

        QuicheQuicConnection conn = connection;

        unsafe().close(voidPromise());
        // making sure that connection statistics is avaliable
        // even after channel is closed
        statsAtClose = collectStats0(conn,  eventLoop().newPromise());
        try {
            failPendingConnectPromise();
            state = CLOSED;
            timedOut = Quiche.quiche_conn_is_timed_out(conn.address());

            closeStreams();

            if (finBuffer != null) {
                finBuffer.release();
                finBuffer = null;
            }
            state = CLOSED;

            timeoutHandler.cancel();
        } finally {
            flushParent();
            connection = null;
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
        return localIdAdrr;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return remoteIdAddr;
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

        failPendingConnectPromise();
        Quiche.throwIfError(Quiche.quiche_conn_close(connectionAddressChecked(), app, err,
                Quiche.readerMemoryAddress(reason), reason.readableBytes()));

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
        throw new UnsupportedOperationException("Unsupported message type: " + StringUtil.simpleClassName(msg));
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
                Quiche.readerMemoryAddress(buf), buf.readableBytes());
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

    void streamShutdown(long streamId, boolean read, boolean write, int err, ChannelPromise promise) {
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

    void streamSendFin(long streamId) throws Exception {
        try {
            // Just write an empty buffer and set fin to true.
            Quiche.throwIfError(streamSend0(streamId, Unpooled.EMPTY_BUFFER, true));
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

    int streamSend(long streamId, ByteBuf buffer, boolean fin) throws ClosedChannelException {
        if (buffer.nioBufferCount() == 1) {
            return streamSend0(streamId, buffer, fin);
        }
        ByteBuffer[] nioBuffers  = buffer.nioBuffers();
        int lastIdx = nioBuffers.length - 1;
        int res = 0;
        for (int i = 0; i < lastIdx; i++) {
            ByteBuffer nioBuffer = nioBuffers[i];
            while (nioBuffer.hasRemaining()) {
                int localRes = streamSend(streamId, nioBuffer, false);
                if (localRes <= 0) {
                    return res;
                }
                res += localRes;

                nioBuffer.position(nioBuffer.position() + localRes);
            }
        }
        int localRes = streamSend(streamId, nioBuffers[lastIdx], fin);
        if (localRes > 0) {
            res += localRes;
        }
        return res;
    }

    void connectionSendAndFlush() {
        if (inFireChannelReadCompleteQueue || (reantranceGuard & IN_HANDLE_WRITABLE_STREAMS) != 0) {
            return;
        }
        if (connectionSend()) {
            flushParent();
        }
    }

    private int streamSend0(long streamId, ByteBuf buffer, boolean fin) throws ClosedChannelException {
        return Quiche.quiche_conn_stream_send(connectionAddressChecked(), streamId,
                Quiche.readerMemoryAddress(buffer), buffer.readableBytes(), fin);
    }

    private int streamSend(long streamId, ByteBuffer buffer, boolean fin) throws ClosedChannelException {
        return Quiche.quiche_conn_stream_send(connectionAddressChecked(), streamId,
                Quiche.memoryAddressWithPosition(buffer), buffer.remaining(), fin);
    }

    StreamRecvResult streamRecv(long streamId, ByteBuf buffer) throws Exception {
        if (finBuffer == null) {
            finBuffer = alloc().directBuffer(1);
        }
        int writerIndex = buffer.writerIndex();
        int recvLen = Quiche.quiche_conn_stream_recv(connectionAddressChecked(), streamId,
                Quiche.writerMemoryAddress(buffer), buffer.writableBytes(), Quiche.writerMemoryAddress(finBuffer));
        if (Quiche.throwIfError(recvLen)) {
            return StreamRecvResult.DONE;
        }

        buffer.writerIndex(writerIndex + recvLen);
        return finBuffer.getBoolean(0) ? StreamRecvResult.FIN : StreamRecvResult.OK;
    }

    /**
     * Receive some data on a QUIC connection.
     */
    void recv(InetSocketAddress recipient, InetSocketAddress sender, ByteBuf buffer) {
        ((QuicChannelUnsafe) unsafe()).connectionRecv(recipient, sender, buffer);
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

    int streamCapacity(long streamId) {
        if (connection.isClosed()) {
            return 0;
        }
        return Quiche.quiche_conn_stream_capacity(connection.address(), streamId);
    }

    private boolean handleWritableStreams() {
        if (isConnDestroyed()) {
            return false;
        }
        reantranceGuard |= IN_HANDLE_WRITABLE_STREAMS;
        try {
            long connAddr = connection.address();
            boolean mayNeedWrite = false;

            if (Quiche.quiche_conn_is_established(connAddr) ||
                    Quiche.quiche_conn_is_in_early_data(connAddr)) {
                long writableIterator = Quiche.quiche_conn_writable(connAddr);

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
                                if (capacity < 0) {
                                    if (!Quiche.quiche_conn_stream_finished(connAddr, streamId)) {
                                        // Only fire an exception if the error was not caused because the stream is
                                        // considered finished.
                                        streamChannel.pipeline().fireExceptionCaught(Quiche.newException(capacity));
                                    }
                                    // Let's close the channel if quiche_conn_stream_capacity(...) returns an error.
                                    streamChannel.forceClose();
                                } else if (streamChannel.writable(capacity)) {
                                    mayNeedWrite = true;
                                }
                            }
                        }
                        if (writable < writableStreams.length) {
                            // We did handle all writable streams.
                            break;
                        }
                    }
                } finally {
                    Quiche.quiche_stream_iter_free(writableIterator);
                }
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

    private void fireExceptionEvents(Throwable cause) {
        if (cause instanceof SSLHandshakeException) {
            notifyAboutHandshakeCompletionIfNeeded((SSLHandshakeException) cause);
        }
        pipeline().fireExceptionCaught(cause);
    }

    private boolean runTasksDirectly() {
        return sslTaskExecutor == null || sslTaskExecutor == ImmediateExecutor.INSTANCE ||
                sslTaskExecutor == ImmediateEventExecutor.INSTANCE;
    }

    private void runAllTaskSend(Runnable task) {
        sslTaskExecutor.execute(decorateTaskSend(task));
    }

    private void runAll(Runnable task) {
        do {
            task.run();
        } while ((task = connection.sslTask()) != null);
    }

    private Runnable decorateTaskSend(Runnable task) {
        return () -> {
            try {
                runAll(task);
            } finally {
                // Move back to the EventLoop.
                eventLoop().execute(() -> {
                    // Call connection send to continue handshake if needed.
                    if (connectionSend()) {
                        forceFlushParent();
                    }
                });
            }
        };
    }

    private boolean connectionSendSegments(SegmentedDatagramPacketAllocator segmentedDatagramPacketAllocator) {
        List<ByteBuf> bufferList = new ArrayList<>(segmentedDatagramPacketAllocator.maxNumSegments());
        long connAddr = connection.address();
        int maxDatagramSize = Quiche.quiche_conn_max_send_udp_payload_size(connAddr);
        boolean packetWasWritten = false;
        boolean close = false;
        try {
            for (;;) {
                int len = calculateSendBufferLength(connAddr, maxDatagramSize);
                ByteBuf out = alloc().directBuffer(len);

                ByteBuffer sendInfo = connection.nextSendInfo();
                InetSocketAddress sendToAddress = this.remote;

                boolean done;
                int writerIndex = out.writerIndex();
                int written = Quiche.quiche_conn_send(
                        connAddr, Quiche.writerMemoryAddress(out), out.writableBytes(),
                        Quiche.memoryAddressWithPosition(sendInfo));
                if (written == 0) {
                    out.release();
                    // No need to create a new datagram packet. Just try again.
                    continue;
                }

                try {
                    done = Quiche.throwIfError(written);
                } catch (Exception e) {
                    done = true;
                    close = Quiche.shouldClose(written);
                    if (!tryFailConnectPromise(e)) {
                        // Only fire through the pipeline if this does not fail the connect promise.
                        fireExceptionEvents(e);
                    }
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
                            packetWasWritten = true;
                            break;
                        default:
                            int segmentSize = segmentSize(bufferList);
                            ByteBuf compositeBuffer = Unpooled.wrappedBuffer(bufferList.toArray(new ByteBuf[0]));
                            // We had more than one buffer, create a segmented packet.
                            parent().write(segmentedDatagramPacketAllocator.newPacket(
                                    compositeBuffer, segmentSize, sendToAddress));
                            packetWasWritten = true;
                            break;
                    }
                    bufferList.clear();
                    return packetWasWritten;
                }
                out.writerIndex(writerIndex + written);

                int segmentSize = -1;
                if (connection.isSendInfoChanged()) {
                    // Change the cached address and let the user know there was a connection migration.
                    InetSocketAddress oldRemote = remote;
                    remote = QuicheSendInfo.getToAddress(sendInfo);
                    local = QuicheSendInfo.getFromAddress(sendInfo);
                    pipeline().fireUserEventTriggered(
                            new QuicConnectionEvent(oldRemote, remote));
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
                    packetWasWritten = true;

                    if (stop) {
                        // Nothing left in the window, continue later. That said we still need to also
                        // write the previous filled out buffer as otherwise we would either leak or need
                        // to drop it and so produce some loss.
                        if (out.isReadable()) {
                            parent().write(new DatagramPacket(out, sendToAddress));
                        } else {
                            out.release();
                        }
                        return true;
                    }
                }
                // Let's add a touch with the bufferList as a hint. This will help us to debug leaks if there
                // are any.
                out.touch(bufferList);
                // store for later, so we can make use of segments.
                bufferList.add(out);
            }
        } finally {
            if (close) {
                // Close now... now way to recover.
                unsafe().close(newPromise());
            }
        }
    }

    private static int segmentSize(List<ByteBuf> bufferList) {
        assert !bufferList.isEmpty();
        int size = bufferList.size();
        return bufferList.get(size - 1).readableBytes();
    }

    private boolean connectionSendSimple() {
        long connAddr = connection.address();
        boolean packetWasWritten = false;
        boolean close = false;
        int maxDatagramSize = Quiche.quiche_conn_max_send_udp_payload_size(connAddr);
        for (;;) {
            ByteBuffer sendInfo = connection.nextSendInfo();

            int len = calculateSendBufferLength(connAddr, maxDatagramSize);
            ByteBuf out = alloc().directBuffer(len);
            int writerIndex = out.writerIndex();

            int written = Quiche.quiche_conn_send(
                    connAddr, Quiche.writerMemoryAddress(out), out.writableBytes(),
                    Quiche.memoryAddressWithPosition(sendInfo));

            try {
                if (Quiche.throwIfError(written)) {
                    out.release();
                    break;
                }
            } catch (Exception e) {
                close = Quiche.shouldClose(written);
                out.release();
                if (!tryFailConnectPromise(e)) {
                    fireExceptionEvents(e);
                }
                break;
            }

            if (written == 0) {
                // No need to create a new datagram packet. Just release and try again.
                out.release();
                continue;
            }
            if (connection.isSendInfoChanged()) {
                // Change the cached address
                InetSocketAddress oldRemote = remote;
                remote = QuicheSendInfo.getToAddress(sendInfo);
                local = QuicheSendInfo.getFromAddress(sendInfo);
                pipeline().fireUserEventTriggered(
                        new QuicConnectionEvent(oldRemote, remote));
            }
            out.writerIndex(writerIndex + written);
            boolean stop = writePacket(new DatagramPacket(out, remote), maxDatagramSize, len);
            packetWasWritten = true;
            if (stop) {
                // Nothing left in the window, continue later
                break;
            }
        }
        if (close) {
            // Close now... now way to recover.
            unsafe().close(newPromise());
        }
        return packetWasWritten;
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
    private boolean connectionSend() {
        if (isConnDestroyed()) {
            return false;
        }
        if ((reantranceGuard & IN_CONNECTION_SEND) != 0) {
            // Let's notify about early data if needed.
            notifyEarlyDataReadyIfNeeded();
            return false;
        }

        reantranceGuard |= IN_CONNECTION_SEND;
        try {
            boolean packetWasWritten;
            SegmentedDatagramPacketAllocator segmentedDatagramPacketAllocator =
                    config.getSegmentedDatagramPacketAllocator();
            if (segmentedDatagramPacketAllocator.maxNumSegments() > 0) {
                packetWasWritten = connectionSendSegments(segmentedDatagramPacketAllocator);
            } else {
                packetWasWritten = connectionSendSimple();
            }

            // Process / schedule all tasks that were created.
            Runnable task = connection.sslTask();
            if (task != null) {
                if (runTasksDirectly()) {
                    // Consume all tasks
                    do {
                        task.run();
                        // Notify about early data ready if needed.
                        notifyEarlyDataReadyIfNeeded();
                    } while ((task = connection.sslTask()) != null);

                    // Let's try again sending after we did process all tasks.
                    return packetWasWritten | connectionSend();
                } else {
                    runAllTaskSend(task);
                }
            } else {
                // Notify about early data ready if needed.
                notifyEarlyDataReadyIfNeeded();
            }

            if (packetWasWritten) {
                timeoutHandler.scheduleTimeout();
            }
            return packetWasWritten;
        } finally {
            reantranceGuard &= ~IN_CONNECTION_SEND;
        }
    }

    private final class QuicChannelUnsafe extends AbstractChannel.AbstractUnsafe {

        void connectStream(QuicStreamType type, ChannelHandler handler,
                           Promise<QuicStreamChannel> promise) {
            long streamId = idGenerator.nextStreamId(type == QuicStreamType.BIDIRECTIONAL);
            try {
                Quiche.throwIfError(streamSend0(streamId, Unpooled.EMPTY_BUFFER, false));
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

        private void fireConnectCloseEventIfNeeded(long connAddr) {
            if (connectionCloseEvent == null) {
                connectionCloseEvent = Quiche.quiche_conn_peer_error(connAddr);
                if (connectionCloseEvent != null) {
                    pipeline().fireUserEventTriggered(connectionCloseEvent);
                }
            }
        }

        void connectionRecv(InetSocketAddress recipient, InetSocketAddress sender, ByteBuf buffer) {
            if (isConnDestroyed()) {
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

                ByteBuffer recvInfo = connection.nextRecvInfo();
                QuicheRecvInfo.setRecvInfo(recvInfo, sender, recipient);

                SocketAddress oldRemote = remote;

                if (connection.isRecvInfoChanged()) {
                    // Update the cached address
                    remote = sender;
                    pipeline().fireUserEventTriggered(
                            new QuicConnectionEvent(oldRemote, sender));
                }
                local = recipient;

                long connAddr = connection.address();
                try {
                    do  {
                        // Call quiche_conn_recv(...) until we consumed all bytes or we did receive some error.
                        int res = Quiche.quiche_conn_recv(connAddr, memoryAddress, bufferReadable,
                                Quiche.memoryAddressWithPosition(recvInfo));
                        boolean done;
                        try {
                            done = Quiche.throwIfError(res);
                        } catch (Exception e) {
                            done = true;
                            close = Quiche.shouldClose(res);
                            if (tryFailConnectPromise(e)) {
                                break;
                            }
                            fireExceptionEvents(e);
                        }

                        // Process / schedule all tasks that were created.
                        Runnable task = connection.sslTask();
                        if (task != null) {
                            if (runTasksDirectly()) {
                                // Consume all tasks
                                do {
                                    task.run();
                                } while ((task = connection.sslTask()) != null);
                                processReceived(connAddr);
                            } else {
                                runAllTaskRecv(task);
                            }
                        } else {
                            processReceived(connAddr);
                        }

                        if (done) {
                            break;
                        }
                        memoryAddress += res;
                        bufferReadable -= res;
                    } while (bufferReadable > 0);
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

        private void processReceived(long connAddr) {
            // Handle pending channelActive if needed.
            if (handlePendingChannelActive()) {
                // Connection was closed right away.
                return;
            }

            notifyAboutHandshakeCompletionIfNeeded(null);

            fireConnectCloseEventIfNeeded(connAddr);

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
        }

        private void runAllTaskRecv(Runnable task) {
            sslTaskExecutor.execute(decorateTaskRecv(task));
        }

        private Runnable decorateTaskRecv(Runnable task) {
            return () -> {
                try {
                    runAll(task);
                } finally {
                    // Move back to the EventLoop.
                    eventLoop().execute(() -> {
                        if (connection != null) {
                            processReceived(connection.address());

                            // Call connection send to continue handshake if needed.
                            if (connectionSend()) {
                                forceFlushParent();
                            }
                        }
                    });
                }
            };
        }
        void recv() {
            if ((reantranceGuard & IN_RECV) != 0 || isConnDestroyed()) {
                return;
            }

            long connAddr = connection.address();
            // Check if we can read anything yet.
            if (!Quiche.quiche_conn_is_established(connAddr) &&
                    !Quiche.quiche_conn_is_in_early_data(connAddr)) {
                return;
            }

            reantranceGuard |= IN_RECV;
            try {
                recvDatagram();
                recvStream();
            } finally {
                fireChannelReadCompleteIfNeeded();
                reantranceGuard &= ~IN_RECV;
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
                                // We did consume all readable streams.
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
                    initAddresses(connection);

                    pipeline().fireChannelActive();
                    notifyAboutHandshakeCompletionIfNeeded(null);
                    fireDatagramExtensionEvent();
                }
            } else if (connectPromise != null && Quiche.quiche_conn_is_established(connAddr)) {
                ChannelPromise promise = connectPromise;
                connectPromise = null;
                state = ACTIVE;
                initAddresses(connection);

                boolean promiseSet = promise.trySuccess();
                pipeline().fireChannelActive();
                notifyAboutHandshakeCompletionIfNeeded(null);
                fireDatagramExtensionEvent();
                if (!promiseSet) {
                    fireConnectCloseEventIfNeeded(connAddr);
                    this.close(this.voidPromise());
                    return true;
                }
            }
            return false;
        }

        private void initAddresses(QuicheQuicConnection connection) {
            localIdAdrr = connection.sourceId();
            remoteIdAddr = connection.destinationId();
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
            streamChannel.writable(streamCapacity(streamId));
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

    private void notifyEarlyDataReadyIfNeeded() {
        if (!server && !earlyDataReadyNotified &&
                !isConnDestroyed() && Quiche.quiche_conn_is_in_early_data(connection.address())) {
            earlyDataReadyNotified = true;
            pipeline().fireUserEventTriggered(SslEarlyDataReadyEvent.INSTANCE);
        }
    }

    // TODO: Come up with something better.
    static QuicheQuicChannel handleConnect(Function<QuicChannel, ? extends QuicSslEngine> sslEngineProvider,
                                           Executor sslTaskExecutor,
                                           SocketAddress address, long config, int localConnIdLength,
                                           boolean supportsDatagram, ByteBuffer fromSockaddrMemory,
                                           ByteBuffer toSockaddrMemory) throws Exception {
        if (address instanceof QuicheQuicChannel.QuicheQuicChannelAddress) {
            QuicheQuicChannel.QuicheQuicChannelAddress addr = (QuicheQuicChannel.QuicheQuicChannelAddress) address;
            QuicheQuicChannel channel = addr.channel;
            channel.connect(sslEngineProvider, sslTaskExecutor, config, localConnIdLength, supportsDatagram,
                    fromSockaddrMemory, toSockaddrMemory);
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
        private final Consumer<QuicheQuicChannel> timeoutTask;

        TimeoutHandler(Consumer<QuicheQuicChannel> timeoutTask) {
            this.timeoutTask = timeoutTask;
        }

        @Override
        public void run() {
            if (!isConnDestroyed()) {
                long connAddr = connection.address();
                timeoutFuture = null;
                // Notify quiche there was a timeout.
                Quiche.quiche_conn_on_timeout(connAddr);

                if (Quiche.quiche_conn_is_closed(connAddr)) {
                    forceClose();
                    if (timeoutTask != null){
                        timeoutTask.accept(QuicheQuicChannel.this);
                    }
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
        if (isConnDestroyed()) {
            promise.setSuccess(statsAtClose);
            return;
        }

        collectStats0(connection, promise);
    }

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
}
