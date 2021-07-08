/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.ssl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.AbstractCoalescingBufferQueue;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ChannelPromiseNotifier;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ImmediateExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseNotifier;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.UnstableApi;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;

import static io.netty.buffer.ByteBufUtil.ensureWritableSuccess;
import static io.netty.handler.ssl.SslUtils.getEncryptedPacketLength;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

/**
 * Adds <a href="https://en.wikipedia.org/wiki/Transport_Layer_Security">SSL
 * &middot; TLS</a> and StartTLS support to a {@link Channel}.  Please refer
 * to the <strong>"SecureChat"</strong> example in the distribution or the web
 * site for the detailed usage.
 *
 * <h3>Beginning the handshake</h3>
 * <p>
 * Beside using the handshake {@link ChannelFuture} to get notified about the completion of the handshake it's
 * also possible to detect it by implement the
 * {@link ChannelInboundHandler#userEventTriggered(ChannelHandlerContext, Object)}
 * method and check for a {@link SslHandshakeCompletionEvent}.
 *
 * <h3>Handshake</h3>
 * <p>
 * The handshake will be automatically issued for you once the {@link Channel} is active and
 * {@link SSLEngine#getUseClientMode()} returns {@code true}.
 * So no need to bother with it by your self.
 *
 * <h3>Closing the session</h3>
 * <p>
 * To close the SSL session, the {@link #closeOutbound()} method should be
 * called to send the {@code close_notify} message to the remote peer. One
 * exception is when you close the {@link Channel} - {@link SslHandler}
 * intercepts the close request and send the {@code close_notify} message
 * before the channel closure automatically.  Once the SSL session is closed,
 * it is not reusable, and consequently you should create a new
 * {@link SslHandler} with a new {@link SSLEngine} as explained in the
 * following section.
 *
 * <h3>Restarting the session</h3>
 * <p>
 * To restart the SSL session, you must remove the existing closed
 * {@link SslHandler} from the {@link ChannelPipeline}, insert a new
 * {@link SslHandler} with a new {@link SSLEngine} into the pipeline,
 * and start the handshake process as described in the first section.
 *
 * <h3>Implementing StartTLS</h3>
 * <p>
 * <a href="https://en.wikipedia.org/wiki/STARTTLS">StartTLS</a> is the
 * communication pattern that secures the wire in the middle of the plaintext
 * connection.  Please note that it is different from SSL &middot; TLS, that
 * secures the wire from the beginning of the connection.  Typically, StartTLS
 * is composed of three steps:
 * <ol>
 * <li>Client sends a StartTLS request to server.</li>
 * <li>Server sends a StartTLS response to client.</li>
 * <li>Client begins SSL handshake.</li>
 * </ol>
 * If you implement a server, you need to:
 * <ol>
 * <li>create a new {@link SslHandler} instance with {@code startTls} flag set
 *     to {@code true},</li>
 * <li>insert the {@link SslHandler} to the {@link ChannelPipeline}, and</li>
 * <li>write a StartTLS response.</li>
 * </ol>
 * Please note that you must insert {@link SslHandler} <em>before</em> sending
 * the StartTLS response.  Otherwise the client can send begin SSL handshake
 * before {@link SslHandler} is inserted to the {@link ChannelPipeline}, causing
 * data corruption.
 * <p>
 * The client-side implementation is much simpler.
 * <ol>
 * <li>Write a StartTLS request,</li>
 * <li>wait for the StartTLS response,</li>
 * <li>create a new {@link SslHandler} instance with {@code startTls} flag set
 *     to {@code false},</li>
 * <li>insert the {@link SslHandler} to the {@link ChannelPipeline}, and</li>
 * <li>Initiate SSL handshake.</li>
 * </ol>
 *
 * <h3>Known issues</h3>
 * <p>
 * Because of a known issue with the current implementation of the SslEngine that comes
 * with Java it may be possible that you see blocked IO-Threads while a full GC is done.
 * <p>
 * So if you are affected you can workaround this problem by adjust the cache settings
 * like shown below:
 *
 * <pre>
 *     SslContext context = ...;
 *     context.getServerSessionContext().setSessionCacheSize(someSaneSize);
 *     context.getServerSessionContext().setSessionTime(someSameTimeout);
 * </pre>
 * <p>
 * What values to use here depends on the nature of your application and should be set
 * based on monitoring and debugging of it.
 * For more details see
 * <a href="https://github.com/netty/netty/issues/832">#832</a> in our issue tracker.
 */
public class SslHandler extends ByteToMessageDecoder implements ChannelOutboundHandler {
    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(SslHandler.class);
    private static final Pattern IGNORABLE_CLASS_IN_STACK = Pattern.compile(
            "^.*(?:Socket|Datagram|Sctp|Udt)Channel.*$");
    private static final Pattern IGNORABLE_ERROR_MESSAGE = Pattern.compile(
            "^.*(?:connection.*(?:reset|closed|abort|broken)|broken.*pipe).*$", Pattern.CASE_INSENSITIVE);
    private static final int STATE_SENT_FIRST_MESSAGE = 1;
    private static final int STATE_FLUSHED_BEFORE_HANDSHAKE = 1 << 1;
    private static final int STATE_READ_DURING_HANDSHAKE = 1 << 2;
    private static final int STATE_HANDSHAKE_STARTED = 1 << 3;
    /**
     * Set by wrap*() methods when something is produced.
     * {@link #channelReadComplete(ChannelHandlerContext)} will check this flag, clear it, and call ctx.flush().
     */
    private static final int STATE_NEEDS_FLUSH = 1 << 4;
    private static final int STATE_OUTBOUND_CLOSED = 1 << 5;
    private static final int STATE_CLOSE_NOTIFY = 1 << 6;
    private static final int STATE_PROCESS_TASK = 1 << 7;
    /**
     * This flag is used to determine if we need to call {@link ChannelHandlerContext#read()} to consume more data
     * when {@link ChannelConfig#isAutoRead()} is {@code false}.
     */
    private static final int STATE_FIRE_CHANNEL_READ = 1 << 8;
    private static final int STATE_UNWRAP_REENTRY = 1 << 9;

    /**
     * <a href="https://tools.ietf.org/html/rfc5246#section-6.2">2^14</a> which is the maximum sized plaintext chunk
     * allowed by the TLS RFC.
     */
    private static final int MAX_PLAINTEXT_LENGTH = 16 * 1024;

    private enum SslEngineType {
        TCNATIVE(true, COMPOSITE_CUMULATOR) {
            @Override
            SSLEngineResult unwrap(SslHandler handler, ByteBuf in, int len, ByteBuf out) throws SSLException {
                int nioBufferCount = in.nioBufferCount();
                int writerIndex = out.writerIndex();
                final SSLEngineResult result;
                if (nioBufferCount > 1) {
                    /*
                     * If {@link OpenSslEngine} is in use,
                     * we can use a special {@link OpenSslEngine#unwrap(ByteBuffer[], ByteBuffer[])} method
                     * that accepts multiple {@link ByteBuffer}s without additional memory copies.
                     */
                    ReferenceCountedOpenSslEngine opensslEngine = (ReferenceCountedOpenSslEngine) handler.engine;
                    try {
                        handler.singleBuffer[0] = toByteBuffer(out, writerIndex, out.writableBytes());
                        result = opensslEngine.unwrap(in.nioBuffers(in.readerIndex(), len), handler.singleBuffer);
                    } finally {
                        handler.singleBuffer[0] = null;
                    }
                } else {
                    result = handler.engine.unwrap(toByteBuffer(in, in.readerIndex(), len),
                        toByteBuffer(out, writerIndex, out.writableBytes()));
                }
                out.writerIndex(writerIndex + result.bytesProduced());
                return result;
            }

            @Override
            ByteBuf allocateWrapBuffer(SslHandler handler, ByteBufAllocator allocator,
                                       int pendingBytes, int numComponents) {
                return allocator.directBuffer(((ReferenceCountedOpenSslEngine) handler.engine)
                        .calculateMaxLengthForWrap(pendingBytes, numComponents));
            }

            @Override
            int calculatePendingData(SslHandler handler, int guess) {
                int sslPending = ((ReferenceCountedOpenSslEngine) handler.engine).sslPending();
                return sslPending > 0 ? sslPending : guess;
            }

            @Override
            boolean jdkCompatibilityMode(SSLEngine engine) {
                return ((ReferenceCountedOpenSslEngine) engine).jdkCompatibilityMode;
            }
        },
        CONSCRYPT(true, COMPOSITE_CUMULATOR) {
            @Override
            SSLEngineResult unwrap(SslHandler handler, ByteBuf in, int len, ByteBuf out) throws SSLException {
                int nioBufferCount = in.nioBufferCount();
                int writerIndex = out.writerIndex();
                final SSLEngineResult result;
                if (nioBufferCount > 1) {
                    /*
                     * Use a special unwrap method without additional memory copies.
                     */
                    try {
                        handler.singleBuffer[0] = toByteBuffer(out, writerIndex, out.writableBytes());
                        result = ((ConscryptAlpnSslEngine) handler.engine).unwrap(
                                in.nioBuffers(in.readerIndex(), len),
                                handler.singleBuffer);
                    } finally {
                        handler.singleBuffer[0] = null;
                    }
                } else {
                    result = handler.engine.unwrap(toByteBuffer(in, in.readerIndex(), len),
                            toByteBuffer(out, writerIndex, out.writableBytes()));
                }
                out.writerIndex(writerIndex + result.bytesProduced());
                return result;
            }

            @Override
            ByteBuf allocateWrapBuffer(SslHandler handler, ByteBufAllocator allocator,
                                       int pendingBytes, int numComponents) {
                return allocator.directBuffer(
                        ((ConscryptAlpnSslEngine) handler.engine).calculateOutNetBufSize(pendingBytes, numComponents));
            }

            @Override
            int calculatePendingData(SslHandler handler, int guess) {
                return guess;
            }

            @Override
            boolean jdkCompatibilityMode(SSLEngine engine) {
                return true;
            }
        },
        JDK(false, MERGE_CUMULATOR) {
            @Override
            SSLEngineResult unwrap(SslHandler handler, ByteBuf in, int len, ByteBuf out) throws SSLException {
                int writerIndex = out.writerIndex();
                ByteBuffer inNioBuffer = toByteBuffer(in, in.readerIndex(), len);
                int position = inNioBuffer.position();
                final SSLEngineResult result = handler.engine.unwrap(inNioBuffer,
                    toByteBuffer(out, writerIndex, out.writableBytes()));
                out.writerIndex(writerIndex + result.bytesProduced());

                // This is a workaround for a bug in Android 5.0. Android 5.0 does not correctly update the
                // SSLEngineResult.bytesConsumed() in some cases and just return 0.
                //
                // See:
                //     - https://android-review.googlesource.com/c/platform/external/conscrypt/+/122080
                //     - https://github.com/netty/netty/issues/7758
                if (result.bytesConsumed() == 0) {
                    int consumed = inNioBuffer.position() - position;
                    if (consumed != result.bytesConsumed()) {
                        // Create a new SSLEngineResult with the correct bytesConsumed().
                        return new SSLEngineResult(
                                result.getStatus(), result.getHandshakeStatus(), consumed, result.bytesProduced());
                    }
                }
                return result;
            }

            @Override
            ByteBuf allocateWrapBuffer(SslHandler handler, ByteBufAllocator allocator,
                                       int pendingBytes, int numComponents) {
                // As for the JDK SSLEngine we always need to allocate buffers of the size required by the SSLEngine
                // (normally ~16KB). This is required even if the amount of data to encrypt is very small. Use heap
                // buffers to reduce the native memory usage.
                //
                // Beside this the JDK SSLEngine also (as of today) will do an extra heap to direct buffer copy
                // if a direct buffer is used as its internals operate on byte[].
                return allocator.heapBuffer(handler.engine.getSession().getPacketBufferSize());
            }

            @Override
            int calculatePendingData(SslHandler handler, int guess) {
                return guess;
            }

            @Override
            boolean jdkCompatibilityMode(SSLEngine engine) {
                return true;
            }
        };

        static SslEngineType forEngine(SSLEngine engine) {
            return engine instanceof ReferenceCountedOpenSslEngine ? TCNATIVE :
                   engine instanceof ConscryptAlpnSslEngine ? CONSCRYPT : JDK;
        }

        SslEngineType(boolean wantsDirectBuffer, Cumulator cumulator) {
            this.wantsDirectBuffer = wantsDirectBuffer;
            this.cumulator = cumulator;
        }

        abstract SSLEngineResult unwrap(SslHandler handler, ByteBuf in, int len, ByteBuf out) throws SSLException;

        abstract int calculatePendingData(SslHandler handler, int guess);

        abstract boolean jdkCompatibilityMode(SSLEngine engine);

        abstract ByteBuf allocateWrapBuffer(SslHandler handler, ByteBufAllocator allocator,
                                            int pendingBytes, int numComponents);

        // BEGIN Platform-dependent flags

        /**
         * {@code true} if and only if {@link SSLEngine} expects a direct buffer and so if a heap buffer
         * is given will make an extra memory copy.
         */
        final boolean wantsDirectBuffer;

        // END Platform-dependent flags

        /**
         * When using JDK {@link SSLEngine}, we use {@link #MERGE_CUMULATOR} because it works only with
         * one {@link ByteBuffer}.
         *
         * When using {@link OpenSslEngine}, we can use {@link #COMPOSITE_CUMULATOR} because it has
         * {@link OpenSslEngine#unwrap(ByteBuffer[], ByteBuffer[])} which works with multiple {@link ByteBuffer}s
         * and which does not need to do extra memory copies.
         */
        final Cumulator cumulator;
    }

    private volatile ChannelHandlerContext ctx;
    private final SSLEngine engine;
    private final SslEngineType engineType;
    private final Executor delegatedTaskExecutor;
    private final boolean jdkCompatibilityMode;

    /**
     * Used if {@link SSLEngine#wrap(ByteBuffer[], ByteBuffer)} and {@link SSLEngine#unwrap(ByteBuffer, ByteBuffer[])}
     * should be called with a {@link ByteBuf} that is only backed by one {@link ByteBuffer} to reduce the object
     * creation.
     */
    private final ByteBuffer[] singleBuffer = new ByteBuffer[1];

    private final boolean startTls;

    private final SslTasksRunner sslTaskRunnerForUnwrap = new SslTasksRunner(true);
    private final SslTasksRunner sslTaskRunner = new SslTasksRunner(false);

    private SslHandlerCoalescingBufferQueue pendingUnencryptedWrites;
    private Promise<Channel> handshakePromise = new LazyChannelPromise();
    private final LazyChannelPromise sslClosePromise = new LazyChannelPromise();

    private int packetLength;
    private short state;

    private volatile long handshakeTimeoutMillis = 10000;
    private volatile long closeNotifyFlushTimeoutMillis = 3000;
    private volatile long closeNotifyReadTimeoutMillis;
    volatile int wrapDataSize = MAX_PLAINTEXT_LENGTH;

    /**
     * Creates a new instance which runs all delegated tasks directly on the {@link EventExecutor}.
     *
     * @param engine  the {@link SSLEngine} this handler will use
     */
    public SslHandler(SSLEngine engine) {
        this(engine, false);
    }

    /**
     * Creates a new instance which runs all delegated tasks directly on the {@link EventExecutor}.
     *
     * @param engine    the {@link SSLEngine} this handler will use
     * @param startTls  {@code true} if the first write request shouldn't be
     *                  encrypted by the {@link SSLEngine}
     */
    public SslHandler(SSLEngine engine, boolean startTls) {
        this(engine, startTls, ImmediateExecutor.INSTANCE);
    }

    /**
     * Creates a new instance.
     *
     * @param engine  the {@link SSLEngine} this handler will use
     * @param delegatedTaskExecutor the {@link Executor} that will be used to execute tasks that are returned by
     *                              {@link SSLEngine#getDelegatedTask()}.
     */
    public SslHandler(SSLEngine engine, Executor delegatedTaskExecutor) {
        this(engine, false, delegatedTaskExecutor);
    }

    /**
     * Creates a new instance.
     *
     * @param engine  the {@link SSLEngine} this handler will use
     * @param startTls  {@code true} if the first write request shouldn't be
     *                  encrypted by the {@link SSLEngine}
     * @param delegatedTaskExecutor the {@link Executor} that will be used to execute tasks that are returned by
     *                              {@link SSLEngine#getDelegatedTask()}.
     */
    public SslHandler(SSLEngine engine, boolean startTls, Executor delegatedTaskExecutor) {
        this.engine = ObjectUtil.checkNotNull(engine, "engine");
        this.delegatedTaskExecutor = ObjectUtil.checkNotNull(delegatedTaskExecutor, "delegatedTaskExecutor");
        engineType = SslEngineType.forEngine(engine);
        this.startTls = startTls;
        this.jdkCompatibilityMode = engineType.jdkCompatibilityMode(engine);
        setCumulator(engineType.cumulator);
    }

    public long getHandshakeTimeoutMillis() {
        return handshakeTimeoutMillis;
    }

    public void setHandshakeTimeout(long handshakeTimeout, TimeUnit unit) {
        checkNotNull(unit, "unit");
        setHandshakeTimeoutMillis(unit.toMillis(handshakeTimeout));
    }

    public void setHandshakeTimeoutMillis(long handshakeTimeoutMillis) {
        this.handshakeTimeoutMillis = checkPositiveOrZero(handshakeTimeoutMillis, "handshakeTimeoutMillis");
    }

    /**
     * Sets the number of bytes to pass to each {@link SSLEngine#wrap(ByteBuffer[], int, int, ByteBuffer)} call.
     * <p>
     * This value will partition data which is passed to write
     * {@link #write(ChannelHandlerContext, Object, ChannelPromise)}. The partitioning will work as follows:
     * <ul>
     * <li>If {@code wrapDataSize <= 0} then we will write each data chunk as is.</li>
     * <li>If {@code wrapDataSize > data size} then we will attempt to aggregate multiple data chunks together.</li>
     * <li>If {@code wrapDataSize > data size}  Else if {@code wrapDataSize <= data size} then we will divide the data
     * into chunks of {@code wrapDataSize} when writing.</li>
     * </ul>
     * <p>
     * If the {@link SSLEngine} doesn't support a gather wrap operation (e.g. {@link SslProvider#OPENSSL}) then
     * aggregating data before wrapping can help reduce the ratio between TLS overhead vs data payload which will lead
     * to better goodput. Writing fixed chunks of data can also help target the underlying transport's (e.g. TCP)
     * frame size. Under lossy/congested network conditions this may help the peer get full TLS packets earlier and
     * be able to do work sooner, as opposed to waiting for the all the pieces of the TLS packet to arrive.
     * @param wrapDataSize the number of bytes which will be passed to each
     *      {@link SSLEngine#wrap(ByteBuffer[], int, int, ByteBuffer)} call.
     */
    @UnstableApi
    public final void setWrapDataSize(int wrapDataSize) {
        this.wrapDataSize = wrapDataSize;
    }

    /**
     * @deprecated use {@link #getCloseNotifyFlushTimeoutMillis()}
     */
    @Deprecated
    public long getCloseNotifyTimeoutMillis() {
        return getCloseNotifyFlushTimeoutMillis();
    }

    /**
     * @deprecated use {@link #setCloseNotifyFlushTimeout(long, TimeUnit)}
     */
    @Deprecated
    public void setCloseNotifyTimeout(long closeNotifyTimeout, TimeUnit unit) {
        setCloseNotifyFlushTimeout(closeNotifyTimeout, unit);
    }

    /**
     * @deprecated use {@link #setCloseNotifyFlushTimeoutMillis(long)}
     */
    @Deprecated
    public void setCloseNotifyTimeoutMillis(long closeNotifyFlushTimeoutMillis) {
        setCloseNotifyFlushTimeoutMillis(closeNotifyFlushTimeoutMillis);
    }

    /**
     * Gets the timeout for flushing the close_notify that was triggered by closing the
     * {@link Channel}. If the close_notify was not flushed in the given timeout the {@link Channel} will be closed
     * forcibly.
     */
    public final long getCloseNotifyFlushTimeoutMillis() {
        return closeNotifyFlushTimeoutMillis;
    }

    /**
     * Sets the timeout for flushing the close_notify that was triggered by closing the
     * {@link Channel}. If the close_notify was not flushed in the given timeout the {@link Channel} will be closed
     * forcibly.
     */
    public final void setCloseNotifyFlushTimeout(long closeNotifyFlushTimeout, TimeUnit unit) {
        setCloseNotifyFlushTimeoutMillis(unit.toMillis(closeNotifyFlushTimeout));
    }

    /**
     * See {@link #setCloseNotifyFlushTimeout(long, TimeUnit)}.
     */
    public final void setCloseNotifyFlushTimeoutMillis(long closeNotifyFlushTimeoutMillis) {
        this.closeNotifyFlushTimeoutMillis = checkPositiveOrZero(closeNotifyFlushTimeoutMillis,
                "closeNotifyFlushTimeoutMillis");
    }

    /**
     * Gets the timeout (in ms) for receiving the response for the close_notify that was triggered by closing the
     * {@link Channel}. This timeout starts after the close_notify message was successfully written to the
     * remote peer. Use {@code 0} to directly close the {@link Channel} and not wait for the response.
     */
    public final long getCloseNotifyReadTimeoutMillis() {
        return closeNotifyReadTimeoutMillis;
    }

    /**
     * Sets the timeout  for receiving the response for the close_notify that was triggered by closing the
     * {@link Channel}. This timeout starts after the close_notify message was successfully written to the
     * remote peer. Use {@code 0} to directly close the {@link Channel} and not wait for the response.
     */
    public final void setCloseNotifyReadTimeout(long closeNotifyReadTimeout, TimeUnit unit) {
        setCloseNotifyReadTimeoutMillis(unit.toMillis(closeNotifyReadTimeout));
    }

    /**
     * See {@link #setCloseNotifyReadTimeout(long, TimeUnit)}.
     */
    public final void setCloseNotifyReadTimeoutMillis(long closeNotifyReadTimeoutMillis) {
        this.closeNotifyReadTimeoutMillis = checkPositiveOrZero(closeNotifyReadTimeoutMillis,
                "closeNotifyReadTimeoutMillis");
    }

    /**
     * Returns the {@link SSLEngine} which is used by this handler.
     */
    public SSLEngine engine() {
        return engine;
    }

    /**
     * Returns the name of the current application-level protocol.
     *
     * @return the protocol name or {@code null} if application-level protocol has not been negotiated
     */
    public String applicationProtocol() {
        SSLEngine engine = engine();
        if (!(engine instanceof ApplicationProtocolAccessor)) {
            return null;
        }

        return ((ApplicationProtocolAccessor) engine).getNegotiatedApplicationProtocol();
    }

    /**
     * Returns a {@link Future} that will get notified once the current TLS handshake completes.
     *
     * @return the {@link Future} for the initial TLS handshake if {@link #renegotiate()} was not invoked.
     *         The {@link Future} for the most recent {@linkplain #renegotiate() TLS renegotiation} otherwise.
     */
    public Future<Channel> handshakeFuture() {
        return handshakePromise;
    }

    /**
     * Use {@link #closeOutbound()}
     */
    @Deprecated
    public ChannelFuture close() {
        return closeOutbound();
    }

    /**
     * Use {@link #closeOutbound(ChannelPromise)}
     */
    @Deprecated
    public ChannelFuture close(ChannelPromise promise) {
        return closeOutbound(promise);
    }

    /**
     * Sends an SSL {@code close_notify} message to the specified channel and
     * destroys the underlying {@link SSLEngine}. This will <strong>not</strong> close the underlying
     * {@link Channel}. If you want to also close the {@link Channel} use {@link Channel#close()} or
     * {@link ChannelHandlerContext#close()}
     */
    public ChannelFuture closeOutbound() {
        return closeOutbound(ctx.newPromise());
    }

    /**
     * Sends an SSL {@code close_notify} message to the specified channel and
     * destroys the underlying {@link SSLEngine}. This will <strong>not</strong> close the underlying
     * {@link Channel}. If you want to also close the {@link Channel} use {@link Channel#close()} or
     * {@link ChannelHandlerContext#close()}
     */
    public ChannelFuture closeOutbound(final ChannelPromise promise) {
        final ChannelHandlerContext ctx = this.ctx;
        if (ctx.executor().inEventLoop()) {
            closeOutbound0(promise);
        } else {
            ctx.executor().execute(new Runnable() {
                @Override
                public void run() {
                    closeOutbound0(promise);
                }
            });
        }
        return promise;
    }

    private void closeOutbound0(ChannelPromise promise) {
        setState(STATE_OUTBOUND_CLOSED);
        engine.closeOutbound();
        try {
            flush(ctx, promise);
        } catch (Exception e) {
            if (!promise.tryFailure(e)) {
                logger.warn("{} flush() raised a masked exception.", ctx.channel(), e);
            }
        }
    }

    /**
     * Return the {@link Future} that will get notified if the inbound of the {@link SSLEngine} is closed.
     *
     * This method will return the same {@link Future} all the time.
     *
     * @see SSLEngine
     */
    public Future<Channel> sslCloseFuture() {
        return sslClosePromise;
    }

    @Override
    public void handlerRemoved0(ChannelHandlerContext ctx) throws Exception {
        if (!pendingUnencryptedWrites.isEmpty()) {
            // Check if queue is not empty first because create a new ChannelException is expensive
            pendingUnencryptedWrites.releaseAndFailAll(ctx,
                    new ChannelException("Pending write on removal of SslHandler"));
        }
        pendingUnencryptedWrites = null;

        SSLHandshakeException cause = null;

        // If the handshake is not done yet we should fail the handshake promise and notify the rest of the
        // pipeline.
        if (!handshakePromise.isDone()) {
            cause = new SSLHandshakeException("SslHandler removed before handshake completed");
            if (handshakePromise.tryFailure(cause)) {
                ctx.fireUserEventTriggered(new SslHandshakeCompletionEvent(cause));
            }
        }
        if (!sslClosePromise.isDone()) {
            if (cause == null) {
                cause = new SSLHandshakeException("SslHandler removed before handshake completed");
            }
            notifyClosePromise(cause);
        }

        if (engine instanceof ReferenceCounted) {
            ((ReferenceCounted) engine).release();
        }
    }

    @Override
    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) throws Exception {
        ctx.bind(localAddress, promise);
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress,
                        ChannelPromise promise) throws Exception {
        ctx.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.deregister(promise);
    }

    @Override
    public void disconnect(final ChannelHandlerContext ctx,
                           final ChannelPromise promise) throws Exception {
        closeOutboundAndChannel(ctx, promise, true);
    }

    @Override
    public void close(final ChannelHandlerContext ctx,
                      final ChannelPromise promise) throws Exception {
        closeOutboundAndChannel(ctx, promise, false);
    }

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        if (!handshakePromise.isDone()) {
            setState(STATE_READ_DURING_HANDSHAKE);
        }

        ctx.read();
    }

    private static IllegalStateException newPendingWritesNullException() {
        return new IllegalStateException("pendingUnencryptedWrites is null, handlerRemoved0 called?");
    }

    @Override
    public void write(final ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!(msg instanceof ByteBuf)) {
            UnsupportedMessageTypeException exception = new UnsupportedMessageTypeException(msg, ByteBuf.class);
            ReferenceCountUtil.safeRelease(msg);
            promise.setFailure(exception);
        } else if (pendingUnencryptedWrites == null) {
            ReferenceCountUtil.safeRelease(msg);
            promise.setFailure(newPendingWritesNullException());
        } else {
            pendingUnencryptedWrites.add((ByteBuf) msg, promise);
        }
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        // Do not encrypt the first write request if this handler is
        // created with startTLS flag turned on.
        if (startTls && !isStateSet(STATE_SENT_FIRST_MESSAGE)) {
            setState(STATE_SENT_FIRST_MESSAGE);
            pendingUnencryptedWrites.writeAndRemoveAll(ctx);
            forceFlush(ctx);
            // Explicit start handshake processing once we send the first message. This will also ensure
            // we will schedule the timeout if needed.
            startHandshakeProcessing(true);
            return;
        }

        if (isStateSet(STATE_PROCESS_TASK)) {
            return;
        }

        try {
            wrapAndFlush(ctx);
        } catch (Throwable cause) {
            setHandshakeFailure(ctx, cause);
            PlatformDependent.throwException(cause);
        }
    }

    private void wrapAndFlush(ChannelHandlerContext ctx) throws SSLException {
        if (pendingUnencryptedWrites.isEmpty()) {
            // It's important to NOT use a voidPromise here as the user
            // may want to add a ChannelFutureListener to the ChannelPromise later.
            //
            // See https://github.com/netty/netty/issues/3364
            pendingUnencryptedWrites.add(Unpooled.EMPTY_BUFFER, ctx.newPromise());
        }
        if (!handshakePromise.isDone()) {
            setState(STATE_FLUSHED_BEFORE_HANDSHAKE);
        }
        try {
            wrap(ctx, false);
        } finally {
            // We may have written some parts of data before an exception was thrown so ensure we always flush.
            // See https://github.com/netty/netty/issues/3900#issuecomment-172481830
            forceFlush(ctx);
        }
    }

    // This method will not call setHandshakeFailure(...) !
    private void wrap(ChannelHandlerContext ctx, boolean inUnwrap) throws SSLException {
        ByteBuf out = null;
        ByteBufAllocator alloc = ctx.alloc();
        try {
            final int wrapDataSize = this.wrapDataSize;
            // Only continue to loop if the handler was not removed in the meantime.
            // See https://github.com/netty/netty/issues/5860
            outer: while (!ctx.isRemoved()) {
                ChannelPromise promise = ctx.newPromise();
                ByteBuf buf = wrapDataSize > 0 ?
                        pendingUnencryptedWrites.remove(alloc, wrapDataSize, promise) :
                        pendingUnencryptedWrites.removeFirst(promise);
                if (buf == null) {
                    break;
                }

                if (out == null) {
                    out = allocateOutNetBuf(ctx, buf.readableBytes(), buf.nioBufferCount());
                }

                SSLEngineResult result = wrap(alloc, engine, buf, out);
                if (buf.isReadable()) {
                    pendingUnencryptedWrites.addFirst(buf, promise);
                    // When we add the buffer/promise pair back we need to be sure we don't complete the promise
                    // later. We only complete the promise if the buffer is completely consumed.
                    promise = null;
                } else {
                    buf.release();
                }

                // We need to write any data before we invoke any methods which may trigger re-entry, otherwise
                // writes may occur out of order and TLS sequencing may be off (e.g. SSLV3_ALERT_BAD_RECORD_MAC).
                if (out.isReadable()) {
                    final ByteBuf b = out;
                    out = null;
                    if (promise != null) {
                        ctx.write(b, promise);
                    } else {
                        ctx.write(b);
                    }
                } else if (promise != null) {
                    ctx.write(Unpooled.EMPTY_BUFFER, promise);
                }
                // else out is not readable we can re-use it and so save an extra allocation

                if (result.getStatus() == Status.CLOSED) {
                    // Make a best effort to preserve any exception that way previously encountered from the handshake
                    // or the transport, else fallback to a general error.
                    Throwable exception = handshakePromise.cause();
                    if (exception == null) {
                        exception = sslClosePromise.cause();
                        if (exception == null) {
                            exception = new SslClosedEngineException("SSLEngine closed already");
                        }
                    }
                    pendingUnencryptedWrites.releaseAndFailAll(ctx, exception);
                    return;
                } else {
                    switch (result.getHandshakeStatus()) {
                        case NEED_TASK:
                            if (!runDelegatedTasks(inUnwrap)) {
                                // We scheduled a task on the delegatingTaskExecutor, so stop processing as we will
                                // resume once the task completes.
                                break outer;
                            }
                            break;
                        case FINISHED:
                        case NOT_HANDSHAKING: // work around for android bug that skips the FINISHED state.
                            setHandshakeSuccess();
                            break;
                        case NEED_WRAP:
                            // If we are expected to wrap again and we produced some data we need to ensure there
                            // is something in the queue to process as otherwise we will not try again before there
                            // was more added. Failing to do so may fail to produce an alert that can be
                            // consumed by the remote peer.
                            if (result.bytesProduced() > 0 && pendingUnencryptedWrites.isEmpty()) {
                                pendingUnencryptedWrites.add(Unpooled.EMPTY_BUFFER);
                            }
                            break;
                        case NEED_UNWRAP:
                            // The underlying engine is starving so we need to feed it with more data.
                            // See https://github.com/netty/netty/pull/5039
                            readIfNeeded(ctx);
                            return;
                        default:
                            throw new IllegalStateException(
                                    "Unknown handshake status: " + result.getHandshakeStatus());
                    }
                }
            }
        } finally {
            if (out != null) {
                out.release();
            }
            if (inUnwrap) {
                setState(STATE_NEEDS_FLUSH);
            }
        }
    }

    /**
     * This method will not call
     * {@link #setHandshakeFailure(ChannelHandlerContext, Throwable, boolean, boolean, boolean)} or
     * {@link #setHandshakeFailure(ChannelHandlerContext, Throwable)}.
     * @return {@code true} if this method ends on {@link SSLEngineResult.HandshakeStatus#NOT_HANDSHAKING}.
     */
    private boolean wrapNonAppData(final ChannelHandlerContext ctx, boolean inUnwrap) throws SSLException {
        ByteBuf out = null;
        ByteBufAllocator alloc = ctx.alloc();
        try {
            // Only continue to loop if the handler was not removed in the meantime.
            // See https://github.com/netty/netty/issues/5860
            outer: while (!ctx.isRemoved()) {
                if (out == null) {
                    // As this is called for the handshake we have no real idea how big the buffer needs to be.
                    // That said 2048 should give us enough room to include everything like ALPN / NPN data.
                    // If this is not enough we will increase the buffer in wrap(...).
                    out = allocateOutNetBuf(ctx, 2048, 1);
                }
                SSLEngineResult result = wrap(alloc, engine, Unpooled.EMPTY_BUFFER, out);
                if (result.bytesProduced() > 0) {
                    ctx.write(out).addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) {
                            Throwable cause = future.cause();
                            if (cause != null) {
                                setHandshakeFailureTransportFailure(ctx, cause);
                            }
                        }
                    });
                    if (inUnwrap) {
                        setState(STATE_NEEDS_FLUSH);
                    }
                    out = null;
                }

                HandshakeStatus status = result.getHandshakeStatus();
                switch (status) {
                    case FINISHED:
                        // We may be here because we read data and discovered the remote peer initiated a renegotiation
                        // and this write is to complete the new handshake. The user may have previously done a
                        // writeAndFlush which wasn't able to wrap data due to needing the pending handshake, so we
                        // attempt to wrap application data here if any is pending.
                        if (setHandshakeSuccess() && inUnwrap && !pendingUnencryptedWrites.isEmpty()) {
                            wrap(ctx, true);
                        }
                        return false;
                    case NEED_TASK:
                        if (!runDelegatedTasks(inUnwrap)) {
                            // We scheduled a task on the delegatingTaskExecutor, so stop processing as we will
                            // resume once the task completes.
                            break outer;
                        }
                        break;
                    case NEED_UNWRAP:
                        if (inUnwrap || unwrapNonAppData(ctx) <= 0) {
                            // If we asked for a wrap, the engine requested an unwrap, and we are in unwrap there is
                            // no use in trying to call wrap again because we have already attempted (or will after we
                            // return) to feed more data to the engine.
                            return false;
                        }
                        break;
                    case NEED_WRAP:
                        break;
                    case NOT_HANDSHAKING:
                        if (setHandshakeSuccess() && inUnwrap && !pendingUnencryptedWrites.isEmpty()) {
                            wrap(ctx, true);
                        }
                        // Workaround for TLS False Start problem reported at:
                        // https://github.com/netty/netty/issues/1108#issuecomment-14266970
                        if (!inUnwrap) {
                            unwrapNonAppData(ctx);
                        }
                        return true;
                    default:
                        throw new IllegalStateException("Unknown handshake status: " + result.getHandshakeStatus());
                }

                // Check if did not produce any bytes and if so break out of the loop, but only if we did not process
                // a task as last action. It's fine to not produce any data as part of executing a task.
                if (result.bytesProduced() == 0 && status != HandshakeStatus.NEED_TASK) {
                    break;
                }

                // It should not consume empty buffers when it is not handshaking
                // Fix for Android, where it was encrypting empty buffers even when not handshaking
                if (result.bytesConsumed() == 0 && result.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING) {
                    break;
                }
            }
        }  finally {
            if (out != null) {
                out.release();
            }
        }
        return false;
    }

    private SSLEngineResult wrap(ByteBufAllocator alloc, SSLEngine engine, ByteBuf in, ByteBuf out)
            throws SSLException {
        ByteBuf newDirectIn = null;
        try {
            int readerIndex = in.readerIndex();
            int readableBytes = in.readableBytes();

            // We will call SslEngine.wrap(ByteBuffer[], ByteBuffer) to allow efficient handling of
            // CompositeByteBuf without force an extra memory copy when CompositeByteBuffer.nioBuffer() is called.
            final ByteBuffer[] in0;
            if (in.isDirect() || !engineType.wantsDirectBuffer) {
                // As CompositeByteBuf.nioBufferCount() can be expensive (as it needs to check all composed ByteBuf
                // to calculate the count) we will just assume a CompositeByteBuf contains more then 1 ByteBuf.
                // The worst that can happen is that we allocate an extra ByteBuffer[] in CompositeByteBuf.nioBuffers()
                // which is better then walking the composed ByteBuf in most cases.
                if (!(in instanceof CompositeByteBuf) && in.nioBufferCount() == 1) {
                    in0 = singleBuffer;
                    // We know its only backed by 1 ByteBuffer so use internalNioBuffer to keep object allocation
                    // to a minimum.
                    in0[0] = in.internalNioBuffer(readerIndex, readableBytes);
                } else {
                    in0 = in.nioBuffers();
                }
            } else {
                // We could even go further here and check if its a CompositeByteBuf and if so try to decompose it and
                // only replace the ByteBuffer that are not direct. At the moment we just will replace the whole
                // CompositeByteBuf to keep the complexity to a minimum
                newDirectIn = alloc.directBuffer(readableBytes);
                newDirectIn.writeBytes(in, readerIndex, readableBytes);
                in0 = singleBuffer;
                in0[0] = newDirectIn.internalNioBuffer(newDirectIn.readerIndex(), readableBytes);
            }

            for (;;) {
                ByteBuffer out0 = out.nioBuffer(out.writerIndex(), out.writableBytes());
                SSLEngineResult result = engine.wrap(in0, out0);
                in.skipBytes(result.bytesConsumed());
                out.writerIndex(out.writerIndex() + result.bytesProduced());

                if (result.getStatus() == Status.BUFFER_OVERFLOW) {
                    out.ensureWritable(engine.getSession().getPacketBufferSize());
                } else {
                    return result;
                }
            }
        } finally {
            // Null out to allow GC of ByteBuffer
            singleBuffer[0] = null;

            if (newDirectIn != null) {
                newDirectIn.release();
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        boolean handshakeFailed = handshakePromise.cause() != null;

        ClosedChannelException exception = new ClosedChannelException();
        // Make sure to release SSLEngine,
        // and notify the handshake future if the connection has been closed during handshake.
        setHandshakeFailure(ctx, exception, !isStateSet(STATE_OUTBOUND_CLOSED), isStateSet(STATE_HANDSHAKE_STARTED),
                false);

        // Ensure we always notify the sslClosePromise as well
        notifyClosePromise(exception);

        try {
            super.channelInactive(ctx);
        } catch (DecoderException e) {
            if (!handshakeFailed || !(e.getCause() instanceof SSLException)) {
                // We only rethrow the exception if the handshake did not fail before channelInactive(...) was called
                // as otherwise this may produce duplicated failures as super.channelInactive(...) will also call
                // channelRead(...).
                //
                // See https://github.com/netty/netty/issues/10119
                throw e;
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (ignoreException(cause)) {
            // It is safe to ignore the 'connection reset by peer' or
            // 'broken pipe' error after sending close_notify.
            if (logger.isDebugEnabled()) {
                logger.debug(
                        "{} Swallowing a harmless 'connection reset by peer / broken pipe' error that occurred " +
                        "while writing close_notify in response to the peer's close_notify", ctx.channel(), cause);
            }

            // Close the connection explicitly just in case the transport
            // did not close the connection automatically.
            if (ctx.channel().isActive()) {
                ctx.close();
            }
        } else {
            ctx.fireExceptionCaught(cause);
        }
    }

    /**
     * Checks if the given {@link Throwable} can be ignore and just "swallowed"
     *
     * When an ssl connection is closed a close_notify message is sent.
     * After that the peer also sends close_notify however, it's not mandatory to receive
     * the close_notify. The party who sent the initial close_notify can close the connection immediately
     * then the peer will get connection reset error.
     *
     */
    private boolean ignoreException(Throwable t) {
        if (!(t instanceof SSLException) && t instanceof IOException && sslClosePromise.isDone()) {
            String message = t.getMessage();

            // first try to match connection reset / broke peer based on the regex. This is the fastest way
            // but may fail on different jdk impls or OS's
            if (message != null && IGNORABLE_ERROR_MESSAGE.matcher(message).matches()) {
                return true;
            }

            // Inspect the StackTraceElements to see if it was a connection reset / broken pipe or not
            StackTraceElement[] elements = t.getStackTrace();
            for (StackTraceElement element: elements) {
                String classname = element.getClassName();
                String methodname = element.getMethodName();

                // skip all classes that belong to the io.netty package
                if (classname.startsWith("io.netty.")) {
                    continue;
                }

                // check if the method name is read if not skip it
                if (!"read".equals(methodname)) {
                    continue;
                }

                // This will also match against SocketInputStream which is used by openjdk 7 and maybe
                // also others
                if (IGNORABLE_CLASS_IN_STACK.matcher(classname).matches()) {
                    return true;
                }

                try {
                    // No match by now.. Try to load the class via classloader and inspect it.
                    // This is mainly done as other JDK implementations may differ in name of
                    // the impl.
                    Class<?> clazz = PlatformDependent.getClassLoader(getClass()).loadClass(classname);

                    if (SocketChannel.class.isAssignableFrom(clazz)
                            || DatagramChannel.class.isAssignableFrom(clazz)) {
                        return true;
                    }

                    // also match against SctpChannel via String matching as it may not present.
                    if (PlatformDependent.javaVersion() >= 7
                            && "com.sun.nio.sctp.SctpChannel".equals(clazz.getSuperclass().getName())) {
                        return true;
                    }
                } catch (Throwable cause) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Unexpected exception while loading class {} classname {}",
                                getClass(), classname, cause);
                    }
                }
            }
        }

        return false;
    }

    /**
     * Returns {@code true} if the given {@link ByteBuf} is encrypted. Be aware that this method
     * will not increase the readerIndex of the given {@link ByteBuf}.
     *
     * @param   buffer
     *                  The {@link ByteBuf} to read from. Be aware that it must have at least 5 bytes to read,
     *                  otherwise it will throw an {@link IllegalArgumentException}.
     * @return encrypted
     *                  {@code true} if the {@link ByteBuf} is encrypted, {@code false} otherwise.
     * @throws IllegalArgumentException
     *                  Is thrown if the given {@link ByteBuf} has not at least 5 bytes to read.
     */
    public static boolean isEncrypted(ByteBuf buffer) {
        if (buffer.readableBytes() < SslUtils.SSL_RECORD_HEADER_LENGTH) {
            throw new IllegalArgumentException(
                    "buffer must have at least " + SslUtils.SSL_RECORD_HEADER_LENGTH + " readable bytes");
        }
        return getEncryptedPacketLength(buffer, buffer.readerIndex()) != SslUtils.NOT_ENCRYPTED;
    }

    private void decodeJdkCompatible(ChannelHandlerContext ctx, ByteBuf in) throws NotSslRecordException {
        int packetLength = this.packetLength;
        // If we calculated the length of the current SSL record before, use that information.
        if (packetLength > 0) {
            if (in.readableBytes() < packetLength) {
                return;
            }
        } else {
            // Get the packet length and wait until we get a packets worth of data to unwrap.
            final int readableBytes = in.readableBytes();
            if (readableBytes < SslUtils.SSL_RECORD_HEADER_LENGTH) {
                return;
            }
            packetLength = getEncryptedPacketLength(in, in.readerIndex());
            if (packetLength == SslUtils.NOT_ENCRYPTED) {
                // Not an SSL/TLS packet
                NotSslRecordException e = new NotSslRecordException(
                        "not an SSL/TLS record: " + ByteBufUtil.hexDump(in));
                in.skipBytes(in.readableBytes());

                // First fail the handshake promise as we may need to have access to the SSLEngine which may
                // be released because the user will remove the SslHandler in an exceptionCaught(...) implementation.
                setHandshakeFailure(ctx, e);

                throw e;
            }
            assert packetLength > 0;
            if (packetLength > readableBytes) {
                // wait until the whole packet can be read
                this.packetLength = packetLength;
                return;
            }
        }

        // Reset the state of this class so we can get the length of the next packet. We assume the entire packet will
        // be consumed by the SSLEngine.
        this.packetLength = 0;
        try {
            final int bytesConsumed = unwrap(ctx, in, packetLength);
            assert bytesConsumed == packetLength || engine.isInboundDone() :
                    "we feed the SSLEngine a packets worth of data: " + packetLength + " but it only consumed: " +
                            bytesConsumed;
        } catch (Throwable cause) {
            handleUnwrapThrowable(ctx, cause);
        }
    }

    private void decodeNonJdkCompatible(ChannelHandlerContext ctx, ByteBuf in) {
        try {
            unwrap(ctx, in, in.readableBytes());
        } catch (Throwable cause) {
            handleUnwrapThrowable(ctx, cause);
        }
    }

    private void handleUnwrapThrowable(ChannelHandlerContext ctx, Throwable cause) {
        try {
            // We should attempt to notify the handshake failure before writing any pending data. If we are in unwrap
            // and failed during the handshake process, and we attempt to wrap, then promises will fail, and if
            // listeners immediately close the Channel then we may end up firing the handshake event after the Channel
            // has been closed.
            if (handshakePromise.tryFailure(cause)) {
                ctx.fireUserEventTriggered(new SslHandshakeCompletionEvent(cause));
            }

            // Let's check if the handler was removed in the meantime and so pendingUnencryptedWrites is null.
            if (pendingUnencryptedWrites != null) {
                // We need to flush one time as there may be an alert that we should send to the remote peer because
                // of the SSLException reported here.
                wrapAndFlush(ctx);
            }
        } catch (SSLException ex) {
            logger.debug("SSLException during trying to call SSLEngine.wrap(...)" +
                    " because of an previous SSLException, ignoring...", ex);
        } finally {
            // ensure we always flush and close the channel.
            setHandshakeFailure(ctx, cause, true, false, true);
        }
        PlatformDependent.throwException(cause);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws SSLException {
        if (isStateSet(STATE_PROCESS_TASK)) {
            return;
        }
        if (jdkCompatibilityMode) {
            decodeJdkCompatible(ctx, in);
        } else {
            decodeNonJdkCompatible(ctx, in);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        channelReadComplete0(ctx);
    }

    private void channelReadComplete0(ChannelHandlerContext ctx) {
        // Discard bytes of the cumulation buffer if needed.
        discardSomeReadBytes();

        flushIfNeeded(ctx);
        readIfNeeded(ctx);

        clearState(STATE_FIRE_CHANNEL_READ);
        ctx.fireChannelReadComplete();
    }

    private void readIfNeeded(ChannelHandlerContext ctx) {
        // If handshake is not finished yet, we need more data.
        if (!ctx.channel().config().isAutoRead() &&
                (!isStateSet(STATE_FIRE_CHANNEL_READ) || !handshakePromise.isDone())) {
            // No auto-read used and no message passed through the ChannelPipeline or the handshake was not complete
            // yet, which means we need to trigger the read to ensure we not encounter any stalls.
            ctx.read();
        }
    }

    private void flushIfNeeded(ChannelHandlerContext ctx) {
        if (isStateSet(STATE_NEEDS_FLUSH)) {
            forceFlush(ctx);
        }
    }

    /**
     * Calls {@link SSLEngine#unwrap(ByteBuffer, ByteBuffer)} with an empty buffer to handle handshakes, etc.
     */
    private int unwrapNonAppData(ChannelHandlerContext ctx) throws SSLException {
        return unwrap(ctx, Unpooled.EMPTY_BUFFER, 0);
    }

    /**
     * Unwraps inbound SSL records.
     */
    private int unwrap(ChannelHandlerContext ctx, ByteBuf packet, int length) throws SSLException {
        final int originalLength = length;
        boolean wrapLater = false;
        boolean notifyClosure = false;
        boolean executedRead = false;
        ByteBuf decodeOut = allocate(ctx, length);
        try {
            // Only continue to loop if the handler was not removed in the meantime.
            // See https://github.com/netty/netty/issues/5860
            do {
                final SSLEngineResult result = engineType.unwrap(this, packet, length, decodeOut);
                final Status status = result.getStatus();
                final HandshakeStatus handshakeStatus = result.getHandshakeStatus();
                final int produced = result.bytesProduced();
                final int consumed = result.bytesConsumed();

                // Skip bytes now in case unwrap is called in a re-entry scenario. For example LocalChannel.read()
                // may entry this method in a re-entry fashion and if the peer is writing into a shared buffer we may
                // unwrap the same data multiple times.
                packet.skipBytes(consumed);
                length -= consumed;

                // The expected sequence of events is:
                // 1. Notify of handshake success
                // 2. fireChannelRead for unwrapped data
                if (handshakeStatus == HandshakeStatus.FINISHED || handshakeStatus == HandshakeStatus.NOT_HANDSHAKING) {
                    wrapLater |= (decodeOut.isReadable() ?
                            setHandshakeSuccessUnwrapMarkReentry() : setHandshakeSuccess()) ||
                            handshakeStatus == HandshakeStatus.FINISHED;
                }

                // Dispatch decoded data after we have notified of handshake success. If this method has been invoked
                // in a re-entry fashion we execute a task on the executor queue to process after the stack unwinds
                // to preserve order of events.
                if (decodeOut.isReadable()) {
                    setState(STATE_FIRE_CHANNEL_READ);
                    if (isStateSet(STATE_UNWRAP_REENTRY)) {
                        executedRead = true;
                        executeChannelRead(ctx, decodeOut);
                    } else {
                        ctx.fireChannelRead(decodeOut);
                    }
                    decodeOut = null;
                }

                if (status == Status.CLOSED) {
                    notifyClosure = true; // notify about the CLOSED state of the SSLEngine. See #137
                } else if (status == Status.BUFFER_OVERFLOW) {
                    if (decodeOut != null) {
                        decodeOut.release();
                    }
                    final int applicationBufferSize = engine.getSession().getApplicationBufferSize();
                    // Allocate a new buffer which can hold all the rest data and loop again.
                    // It may happen that applicationBufferSize < produced while there is still more to unwrap, in this
                    // case we will just allocate a new buffer with the capacity of applicationBufferSize and call
                    // unwrap again.
                    decodeOut = allocate(ctx, engineType.calculatePendingData(this, applicationBufferSize < produced ?
                            applicationBufferSize : applicationBufferSize - produced));
                    continue;
                }

                if (handshakeStatus == HandshakeStatus.NEED_TASK) {
                    if (!runDelegatedTasks(true)) {
                        // We scheduled a task on the delegatingTaskExecutor, so stop processing as we will
                        // resume once the task completes.
                        //
                        // We break out of the loop only and do NOT return here as we still may need to notify
                        // about the closure of the SSLEngine.
                        wrapLater = false;
                        break;
                    }
                } else if (handshakeStatus == HandshakeStatus.NEED_WRAP) {
                    // If the wrap operation transitions the status to NOT_HANDSHAKING and there is no more data to
                    // unwrap then the next call to unwrap will not produce any data. We can avoid the potentially
                    // costly unwrap operation and break out of the loop.
                    if (wrapNonAppData(ctx, true) && length == 0) {
                        break;
                    }
                }

                if (status == Status.BUFFER_UNDERFLOW ||
                        // If we processed NEED_TASK we should try again even we did not consume or produce anything.
                        handshakeStatus != HandshakeStatus.NEED_TASK && (consumed == 0 && produced == 0 ||
                                (length == 0 && handshakeStatus == HandshakeStatus.NOT_HANDSHAKING))) {
                    if (handshakeStatus == HandshakeStatus.NEED_UNWRAP) {
                        // The underlying engine is starving so we need to feed it with more data.
                        // See https://github.com/netty/netty/pull/5039
                        readIfNeeded(ctx);
                    }

                    break;
                } else if (decodeOut == null) {
                    decodeOut = allocate(ctx, length);
                }
            } while (!ctx.isRemoved());

            if (isStateSet(STATE_FLUSHED_BEFORE_HANDSHAKE) && handshakePromise.isDone()) {
                // We need to call wrap(...) in case there was a flush done before the handshake completed to ensure
                // we do not stale.
                //
                // See https://github.com/netty/netty/pull/2437
                clearState(STATE_FLUSHED_BEFORE_HANDSHAKE);
                wrapLater = true;
            }

            if (wrapLater) {
                wrap(ctx, true);
            }
        } finally {
            if (decodeOut != null) {
                decodeOut.release();
            }

            if (notifyClosure) {
                if (executedRead) {
                    executeNotifyClosePromise(ctx);
                } else {
                    notifyClosePromise(null);
                }
            }
        }
        return originalLength - length;
    }

    private boolean setHandshakeSuccessUnwrapMarkReentry() {
        // setHandshakeSuccess calls out to external methods which may trigger re-entry. We need to preserve ordering of
        // fireChannelRead for decodeOut relative to re-entry data.
        final boolean setReentryState = !isStateSet(STATE_UNWRAP_REENTRY);
        if (setReentryState) {
            setState(STATE_UNWRAP_REENTRY);
        }
        try {
            return setHandshakeSuccess();
        } finally {
            // It is unlikely this specific method will be re-entry because handshake completion is infrequent, but just
            // in case we only clear the state if we set it in the first place.
            if (setReentryState) {
                clearState(STATE_UNWRAP_REENTRY);
            }
        }
    }

    private void executeNotifyClosePromise(final ChannelHandlerContext ctx) {
        try {
            ctx.executor().execute(new Runnable() {
                @Override
                public void run() {
                    notifyClosePromise(null);
                }
            });
        } catch (RejectedExecutionException e) {
            notifyClosePromise(e);
        }
    }

    private void executeChannelRead(final ChannelHandlerContext ctx, final ByteBuf decodedOut) {
        try {
            ctx.executor().execute(new Runnable() {
                @Override
                public void run() {
                    ctx.fireChannelRead(decodedOut);
                }
            });
        } catch (RejectedExecutionException e) {
            decodedOut.release();
            throw e;
        }
    }

    private static ByteBuffer toByteBuffer(ByteBuf out, int index, int len) {
        return out.nioBufferCount() == 1 ? out.internalNioBuffer(index, len) :
                out.nioBuffer(index, len);
    }

    private static boolean inEventLoop(Executor executor) {
        return executor instanceof EventExecutor && ((EventExecutor) executor).inEventLoop();
    }

    private static void runAllDelegatedTasks(SSLEngine engine) {
        for (;;) {
            Runnable task = engine.getDelegatedTask();
            if (task == null) {
                return;
            }
            task.run();
        }
    }

    /**
     * Will either run the delegated task directly calling {@link Runnable#run()} and return {@code true} or will
     * offload the delegated task using {@link Executor#execute(Runnable)} and return {@code false}.
     *
     * If the task is offloaded it will take care to resume its work on the {@link EventExecutor} once there are no
     * more tasks to process.
     */
    private boolean runDelegatedTasks(boolean inUnwrap) {
        if (delegatedTaskExecutor == ImmediateExecutor.INSTANCE || inEventLoop(delegatedTaskExecutor)) {
            // We should run the task directly in the EventExecutor thread and not offload at all. As we are on the
            // EventLoop we can just run all tasks at once.
            runAllDelegatedTasks(engine);
            return true;
        } else {
            executeDelegatedTask(inUnwrap);
            return false;
        }
    }

    private void executeDelegatedTask(boolean inUnwrap) {
        executeDelegatedTask(inUnwrap ? sslTaskRunnerForUnwrap : sslTaskRunner);
    }

    private void executeDelegatedTask(SslTasksRunner task) {
        setState(STATE_PROCESS_TASK);
        try {
            delegatedTaskExecutor.execute(task);
        } catch (RejectedExecutionException e) {
            clearState(STATE_PROCESS_TASK);
            throw e;
        }
    }

    /**
     * {@link Runnable} that will be scheduled on the {@code delegatedTaskExecutor} and will take care
     * of resume work on the {@link EventExecutor} once the task was executed.
     */
    private final class SslTasksRunner implements Runnable {
        private final boolean inUnwrap;

        SslTasksRunner(boolean inUnwrap) {
            this.inUnwrap = inUnwrap;
        }

        // Handle errors which happened during task processing.
        private void taskError(Throwable e) {
            if (inUnwrap) {
                // As the error happened while the task was scheduled as part of unwrap(...) we also need to ensure
                // we fire it through the pipeline as inbound error to be consistent with what we do in decode(...).
                //
                // This will also ensure we fail the handshake future and flush all produced data.
                try {
                    handleUnwrapThrowable(ctx, e);
                } catch (Throwable cause) {
                    safeExceptionCaught(cause);
                }
            } else {
                setHandshakeFailure(ctx, e);
                forceFlush(ctx);
            }
        }

        // Try to call exceptionCaught(...)
        private void safeExceptionCaught(Throwable cause) {
            try {
                exceptionCaught(ctx, wrapIfNeeded(cause));
            } catch (Throwable error) {
                ctx.fireExceptionCaught(error);
            }
        }

        private Throwable wrapIfNeeded(Throwable cause) {
            if (!inUnwrap) {
                // If we are not in unwrap(...) we can just rethrow without wrapping at all.
                return cause;
            }
            // As the exception would have been triggered by an inbound operation we will need to wrap it in a
            // DecoderException to mimic what a decoder would do when decode(...) throws.
            return cause instanceof DecoderException ? cause : new DecoderException(cause);
        }

        private void tryDecodeAgain() {
            try {
                channelRead(ctx, Unpooled.EMPTY_BUFFER);
            } catch (Throwable cause) {
                safeExceptionCaught(cause);
            } finally {
                // As we called channelRead(...) we also need to call channelReadComplete(...) which
                // will ensure we either call ctx.fireChannelReadComplete() or will trigger a ctx.read() if
                // more data is needed.
                channelReadComplete0(ctx);
            }
        }

        /**
         * Executed after the wrapped {@code task} was executed via {@code delegatedTaskExecutor} to resume work
         * on the {@link EventExecutor}.
         */
        private void resumeOnEventExecutor() {
            assert ctx.executor().inEventLoop();
            clearState(STATE_PROCESS_TASK);
            try {
                HandshakeStatus status = engine.getHandshakeStatus();
                switch (status) {
                    // There is another task that needs to be executed and offloaded to the delegatingTaskExecutor as
                    // a result of this. Let's reschedule....
                    case NEED_TASK:
                        executeDelegatedTask(this);

                        break;

                    // The handshake finished, lets notify about the completion of it and resume processing.
                    case FINISHED:
                    // Not handshaking anymore, lets notify about the completion if not done yet and resume processing.
                    case NOT_HANDSHAKING:
                        setHandshakeSuccess(); // NOT_HANDSHAKING -> workaround for android skipping FINISHED state.
                        try {
                            // Lets call wrap to ensure we produce the alert if there is any pending and also to
                            // ensure we flush any queued data..
                            wrap(ctx, inUnwrap);
                        } catch (Throwable e) {
                            taskError(e);
                            return;
                        }
                        if (inUnwrap) {
                            // If we were in the unwrap call when the task was processed we should also try to unwrap
                            // non app data first as there may not anything left in the inbound buffer to process.
                            unwrapNonAppData(ctx);
                        }

                        // Flush now as we may have written some data as part of the wrap call.
                        forceFlush(ctx);

                        tryDecodeAgain();
                        break;

                    // We need more data so lets try to unwrap first and then call decode again which will feed us
                    // with buffered data (if there is any).
                    case NEED_UNWRAP:
                        try {
                            unwrapNonAppData(ctx);
                        } catch (SSLException e) {
                            handleUnwrapThrowable(ctx, e);
                            return;
                        }
                        tryDecodeAgain();
                        break;

                    // To make progress we need to call SSLEngine.wrap(...) which may produce more output data
                    // that will be written to the Channel.
                    case NEED_WRAP:
                        try {
                            if (!wrapNonAppData(ctx, false) && inUnwrap) {
                                // The handshake finished in wrapNonAppData(...), we need to try call
                                // unwrapNonAppData(...) as we may have some alert that we should read.
                                //
                                // This mimics what we would do when we are calling this method while in unwrap(...).
                                unwrapNonAppData(ctx);
                            }

                            // Flush now as we may have written some data as part of the wrap call.
                            forceFlush(ctx);
                        } catch (Throwable e) {
                            taskError(e);
                            return;
                        }

                        // Now try to feed in more data that we have buffered.
                        tryDecodeAgain();
                        break;

                    default:
                        // Should never reach here as we handle all cases.
                        throw new AssertionError();
                }
            } catch (Throwable cause) {
                safeExceptionCaught(cause);
            }
        }

        @Override
        public void run() {
            try {
                Runnable task = engine.getDelegatedTask();
                if (task == null) {
                    // The task was processed in the meantime. Let's just return.
                    return;
                }
                task.run();

                EventExecutor executor = ctx.executor();
                if (executor.inEventLoop()) {
                    resumeOnEventExecutor();
                } else {
                    // Jump back on the EventExecutor.
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            resumeOnEventExecutor();
                        }
                    });
                }
            } catch (final Throwable cause) {
                handleException(cause);
            }
        }

        private void handleException(final Throwable cause) {
            if (ctx.executor().inEventLoop()) {
                clearState(STATE_PROCESS_TASK);
                safeExceptionCaught(cause);
            } else {
                try {
                    ctx.executor().execute(new Runnable() {
                        @Override
                        public void run() {
                            clearState(STATE_PROCESS_TASK);
                            safeExceptionCaught(cause);
                        }
                    });
                } catch (RejectedExecutionException ignore) {
                    clearState(STATE_PROCESS_TASK);
                    // the context itself will handle the rejected exception when try to schedule the operation so
                    // ignore the RejectedExecutionException
                    ctx.fireExceptionCaught(cause);
                }
            }
        }
    }

    /**
     * Notify all the handshake futures about the successfully handshake
     * @return {@code true} if {@link #handshakePromise} was set successfully and a {@link SslHandshakeCompletionEvent}
     * was fired. {@code false} otherwise.
     */
    private boolean setHandshakeSuccess() {
        // Our control flow may invoke this method multiple times for a single FINISHED event. For example
        // wrapNonAppData may drain pendingUnencryptedWrites in wrap which transitions to handshake from FINISHED to
        // NOT_HANDSHAKING which invokes setHandshakeSuccess, and then wrapNonAppData also directly invokes this method.
        final boolean notified;
        if (notified = !handshakePromise.isDone() && handshakePromise.trySuccess(ctx.channel())) {
            if (logger.isDebugEnabled()) {
                SSLSession session = engine.getSession();
                logger.debug(
                        "{} HANDSHAKEN: protocol:{} cipher suite:{}",
                        ctx.channel(),
                        session.getProtocol(),
                        session.getCipherSuite());
            }
            ctx.fireUserEventTriggered(SslHandshakeCompletionEvent.SUCCESS);
        }
        if (isStateSet(STATE_READ_DURING_HANDSHAKE)) {
            clearState(STATE_READ_DURING_HANDSHAKE);
            if (!ctx.channel().config().isAutoRead()) {
                ctx.read();
            }
        }
        return notified;
    }

    /**
     * Notify all the handshake futures about the failure during the handshake.
     */
    private void setHandshakeFailure(ChannelHandlerContext ctx, Throwable cause) {
        setHandshakeFailure(ctx, cause, true, true, false);
    }

    /**
     * Notify all the handshake futures about the failure during the handshake.
     */
    private void setHandshakeFailure(ChannelHandlerContext ctx, Throwable cause, boolean closeInbound,
                                     boolean notify, boolean alwaysFlushAndClose) {
        try {
            // Release all resources such as internal buffers that SSLEngine is managing.
            setState(STATE_OUTBOUND_CLOSED);
            engine.closeOutbound();

            if (closeInbound) {
                try {
                    engine.closeInbound();
                } catch (SSLException e) {
                    if (logger.isDebugEnabled()) {
                        // only log in debug mode as it most likely harmless and latest chrome still trigger
                        // this all the time.
                        //
                        // See https://github.com/netty/netty/issues/1340
                        String msg = e.getMessage();
                        if (msg == null || !(msg.contains("possible truncation attack") ||
                                msg.contains("closing inbound before receiving peer's close_notify"))) {
                            logger.debug("{} SSLEngine.closeInbound() raised an exception.", ctx.channel(), e);
                        }
                    }
                }
            }
            if (handshakePromise.tryFailure(cause) || alwaysFlushAndClose) {
                SslUtils.handleHandshakeFailure(ctx, cause, notify);
            }
        } finally {
            // Ensure we remove and fail all pending writes in all cases and so release memory quickly.
            releaseAndFailAll(ctx, cause);
        }
    }

    private void setHandshakeFailureTransportFailure(ChannelHandlerContext ctx, Throwable cause) {
        // If TLS control frames fail to write we are in an unknown state and may become out of
        // sync with our peer. We give up and close the channel. This will also take care of
        // cleaning up any outstanding state (e.g. handshake promise, queued unencrypted data).
        try {
            SSLException transportFailure = new SSLException("failure when writing TLS control frames", cause);
            releaseAndFailAll(ctx, transportFailure);
            if (handshakePromise.tryFailure(transportFailure)) {
                ctx.fireUserEventTriggered(new SslHandshakeCompletionEvent(transportFailure));
            }
        } finally {
            ctx.close();
        }
    }

    private void releaseAndFailAll(ChannelHandlerContext ctx, Throwable cause) {
        if (pendingUnencryptedWrites != null) {
            pendingUnencryptedWrites.releaseAndFailAll(ctx, cause);
        }
    }

    private void notifyClosePromise(Throwable cause) {
        if (cause == null) {
            if (sslClosePromise.trySuccess(ctx.channel())) {
                ctx.fireUserEventTriggered(SslCloseCompletionEvent.SUCCESS);
            }
        } else {
            if (sslClosePromise.tryFailure(cause)) {
                ctx.fireUserEventTriggered(new SslCloseCompletionEvent(cause));
            }
        }
    }

    private void closeOutboundAndChannel(
            final ChannelHandlerContext ctx, final ChannelPromise promise, boolean disconnect) throws Exception {
        setState(STATE_OUTBOUND_CLOSED);
        engine.closeOutbound();

        if (!ctx.channel().isActive()) {
            if (disconnect) {
                ctx.disconnect(promise);
            } else {
                ctx.close(promise);
            }
            return;
        }

        ChannelPromise closeNotifyPromise = ctx.newPromise();
        try {
            flush(ctx, closeNotifyPromise);
        } finally {
            if (!isStateSet(STATE_CLOSE_NOTIFY)) {
                setState(STATE_CLOSE_NOTIFY);
                // It's important that we do not pass the original ChannelPromise to safeClose(...) as when flush(....)
                // throws an Exception it will be propagated to the AbstractChannelHandlerContext which will try
                // to fail the promise because of this. This will then fail as it was already completed by
                // safeClose(...). We create a new ChannelPromise and try to notify the original ChannelPromise
                // once it is complete. If we fail to do so we just ignore it as in this case it was failed already
                // because of a propagated Exception.
                //
                // See https://github.com/netty/netty/issues/5931
                safeClose(ctx, closeNotifyPromise, ctx.newPromise().addListener(
                        new ChannelPromiseNotifier(false, promise)));
            } else {
                /// We already handling the close_notify so just attach the promise to the sslClosePromise.
                sslClosePromise.addListener(new FutureListener<Channel>() {
                    @Override
                    public void operationComplete(Future<Channel> future) {
                        promise.setSuccess();
                    }
                });
            }
        }
    }

    private void flush(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        if (pendingUnencryptedWrites != null) {
            pendingUnencryptedWrites.add(Unpooled.EMPTY_BUFFER, promise);
        } else {
            promise.setFailure(newPendingWritesNullException());
        }
        flush(ctx);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;

        Channel channel = ctx.channel();
        pendingUnencryptedWrites = new SslHandlerCoalescingBufferQueue(channel, 16);
        boolean fastOpen = Boolean.TRUE.equals(channel.config().getOption(ChannelOption.TCP_FASTOPEN_CONNECT));
        boolean active = channel.isActive();
        if (active || fastOpen) {
            // Explicitly flush the handshake only if the channel is already active.
            // With TCP Fast Open, we write to the outbound buffer before the TCP connect is established.
            // The buffer will then be flushed as part of establishing the connection, saving us a round-trip.
            startHandshakeProcessing(active);
            // If we weren't able to include client_hello in the TCP SYN (e.g. no token, disabled at the OS) we have to
            // flush pending data in the outbound buffer later in channelActive().
            final ChannelOutboundBuffer outboundBuffer;
            if (fastOpen && ((outboundBuffer = channel.unsafe().outboundBuffer()) == null ||
                    outboundBuffer.totalPendingWriteBytes() > 0)) {
                setState(STATE_NEEDS_FLUSH);
            }
        }
    }

    private void startHandshakeProcessing(boolean flushAtEnd) {
        if (!isStateSet(STATE_HANDSHAKE_STARTED)) {
            setState(STATE_HANDSHAKE_STARTED);
            if (engine.getUseClientMode()) {
                // Begin the initial handshake.
                // channelActive() event has been fired already, which means this.channelActive() will
                // not be invoked. We have to initialize here instead.
                handshake(flushAtEnd);
            }
            applyHandshakeTimeout();
        } else if (isStateSet(STATE_NEEDS_FLUSH)) {
            forceFlush(ctx);
        }
    }

    /**
     * Performs TLS renegotiation.
     */
    public Future<Channel> renegotiate() {
        ChannelHandlerContext ctx = this.ctx;
        if (ctx == null) {
            throw new IllegalStateException();
        }

        return renegotiate(ctx.executor().<Channel>newPromise());
    }

    /**
     * Performs TLS renegotiation.
     */
    public Future<Channel> renegotiate(final Promise<Channel> promise) {
        ObjectUtil.checkNotNull(promise, "promise");

        ChannelHandlerContext ctx = this.ctx;
        if (ctx == null) {
            throw new IllegalStateException();
        }

        EventExecutor executor = ctx.executor();
        if (!executor.inEventLoop()) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    renegotiateOnEventLoop(promise);
                }
            });
            return promise;
        }

        renegotiateOnEventLoop(promise);
        return promise;
    }

    private void renegotiateOnEventLoop(final Promise<Channel> newHandshakePromise) {
        final Promise<Channel> oldHandshakePromise = handshakePromise;
        if (!oldHandshakePromise.isDone()) {
            // There's no need to handshake because handshake is in progress already.
            // Merge the new promise into the old one.
            oldHandshakePromise.addListener(new PromiseNotifier<Channel, Future<Channel>>(newHandshakePromise));
        } else {
            handshakePromise = newHandshakePromise;
            handshake(true);
            applyHandshakeTimeout();
        }
    }

    /**
     * Performs TLS (re)negotiation.
     * @param flushAtEnd Set to {@code true} if the outbound buffer should be flushed (written to the network) at the
     *                  end. Set to {@code false} if the handshake will be flushed later, e.g. as part of TCP Fast Open
     *                  connect.
     */
    private void handshake(boolean flushAtEnd) {
        if (engine.getHandshakeStatus() != HandshakeStatus.NOT_HANDSHAKING) {
            // Not all SSLEngine implementations support calling beginHandshake multiple times while a handshake
            // is in progress. See https://github.com/netty/netty/issues/4718.
            return;
        }
        if (handshakePromise.isDone()) {
            // If the handshake is done already lets just return directly as there is no need to trigger it again.
            // This can happen if the handshake(...) was triggered before we called channelActive(...) by a
            // flush() that was triggered by a ChannelFutureListener that was added to the ChannelFuture returned
            // from the connect(...) method. In this case we will see the flush() happen before we had a chance to
            // call fireChannelActive() on the pipeline.
            return;
        }

        // Begin handshake.
        final ChannelHandlerContext ctx = this.ctx;
        try {
            engine.beginHandshake();
            wrapNonAppData(ctx, false);
        } catch (Throwable e) {
            setHandshakeFailure(ctx, e);
        } finally {
            if (flushAtEnd) {
                forceFlush(ctx);
            }
        }
    }

    private void applyHandshakeTimeout() {
        final Promise<Channel> localHandshakePromise = this.handshakePromise;

        // Set timeout if necessary.
        final long handshakeTimeoutMillis = this.handshakeTimeoutMillis;
        if (handshakeTimeoutMillis <= 0 || localHandshakePromise.isDone()) {
            return;
        }

        final ScheduledFuture<?> timeoutFuture = ctx.executor().schedule(new Runnable() {
            @Override
            public void run() {
                if (localHandshakePromise.isDone()) {
                    return;
                }
                SSLException exception =
                        new SslHandshakeTimeoutException("handshake timed out after " + handshakeTimeoutMillis + "ms");
                try {
                    if (localHandshakePromise.tryFailure(exception)) {
                        SslUtils.handleHandshakeFailure(ctx, exception, true);
                    }
                } finally {
                    releaseAndFailAll(ctx, exception);
                }
            }
        }, handshakeTimeoutMillis, TimeUnit.MILLISECONDS);

        // Cancel the handshake timeout when handshake is finished.
        localHandshakePromise.addListener(new FutureListener<Channel>() {
            @Override
            public void operationComplete(Future<Channel> f) throws Exception {
                timeoutFuture.cancel(false);
            }
        });
    }

    private void forceFlush(ChannelHandlerContext ctx) {
        clearState(STATE_NEEDS_FLUSH);
        ctx.flush();
    }

    /**
     * Issues an initial TLS handshake once connected when used in client-mode
     */
    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        if (!startTls) {
            startHandshakeProcessing(true);
        }
        ctx.fireChannelActive();
    }

    private void safeClose(
            final ChannelHandlerContext ctx, final ChannelFuture flushFuture,
            final ChannelPromise promise) {
        if (!ctx.channel().isActive()) {
            ctx.close(promise);
            return;
        }

        final ScheduledFuture<?> timeoutFuture;
        if (!flushFuture.isDone()) {
            long closeNotifyTimeout = closeNotifyFlushTimeoutMillis;
            if (closeNotifyTimeout > 0) {
                // Force-close the connection if close_notify is not fully sent in time.
                timeoutFuture = ctx.executor().schedule(new Runnable() {
                    @Override
                    public void run() {
                        // May be done in the meantime as cancel(...) is only best effort.
                        if (!flushFuture.isDone()) {
                            logger.warn("{} Last write attempt timed out; force-closing the connection.",
                                    ctx.channel());
                            addCloseListener(ctx.close(ctx.newPromise()), promise);
                        }
                    }
                }, closeNotifyTimeout, TimeUnit.MILLISECONDS);
            } else {
                timeoutFuture = null;
            }
        } else {
            timeoutFuture = null;
        }

        // Close the connection if close_notify is sent in time.
        flushFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture f) {
                if (timeoutFuture != null) {
                    timeoutFuture.cancel(false);
                }
                final long closeNotifyReadTimeout = closeNotifyReadTimeoutMillis;
                if (closeNotifyReadTimeout <= 0) {
                    // Trigger the close in all cases to make sure the promise is notified
                    // See https://github.com/netty/netty/issues/2358
                    addCloseListener(ctx.close(ctx.newPromise()), promise);
                } else {
                    final ScheduledFuture<?> closeNotifyReadTimeoutFuture;

                    if (!sslClosePromise.isDone()) {
                        closeNotifyReadTimeoutFuture = ctx.executor().schedule(new Runnable() {
                            @Override
                            public void run() {
                                if (!sslClosePromise.isDone()) {
                                    logger.debug(
                                            "{} did not receive close_notify in {}ms; force-closing the connection.",
                                            ctx.channel(), closeNotifyReadTimeout);

                                    // Do the close now...
                                    addCloseListener(ctx.close(ctx.newPromise()), promise);
                                }
                            }
                        }, closeNotifyReadTimeout, TimeUnit.MILLISECONDS);
                    } else {
                        closeNotifyReadTimeoutFuture = null;
                    }

                    // Do the close once the we received the close_notify.
                    sslClosePromise.addListener(new FutureListener<Channel>() {
                        @Override
                        public void operationComplete(Future<Channel> future) throws Exception {
                            if (closeNotifyReadTimeoutFuture != null) {
                                closeNotifyReadTimeoutFuture.cancel(false);
                            }
                            addCloseListener(ctx.close(ctx.newPromise()), promise);
                        }
                    });
                }
            }
        });
    }

    private static void addCloseListener(ChannelFuture future, ChannelPromise promise) {
        // We notify the promise in the ChannelPromiseNotifier as there is a "race" where the close(...) call
        // by the timeoutFuture and the close call in the flushFuture listener will be called. Because of
        // this we need to use trySuccess() and tryFailure(...) as otherwise we can cause an
        // IllegalStateException.
        // Also we not want to log if the notification happens as this is expected in some cases.
        // See https://github.com/netty/netty/issues/5598
        future.addListener(new ChannelPromiseNotifier(false, promise));
    }

    /**
     * Always prefer a direct buffer when it's pooled, so that we reduce the number of memory copies
     * in {@link OpenSslEngine}.
     */
    private ByteBuf allocate(ChannelHandlerContext ctx, int capacity) {
        ByteBufAllocator alloc = ctx.alloc();
        if (engineType.wantsDirectBuffer) {
            return alloc.directBuffer(capacity);
        } else {
            return alloc.buffer(capacity);
        }
    }

    /**
     * Allocates an outbound network buffer for {@link SSLEngine#wrap(ByteBuffer, ByteBuffer)} which can encrypt
     * the specified amount of pending bytes.
     */
    private ByteBuf allocateOutNetBuf(ChannelHandlerContext ctx, int pendingBytes, int numComponents) {
        return engineType.allocateWrapBuffer(this, ctx.alloc(), pendingBytes, numComponents);
    }

    private boolean isStateSet(int bit) {
        return (state & bit) == bit;
    }

    private void setState(int bit) {
        state |= bit;
    }

    private void clearState(int bit) {
        state &= ~bit;
    }

    /**
     * Each call to SSL_write will introduce about ~100 bytes of overhead. This coalescing queue attempts to increase
     * goodput by aggregating the plaintext in chunks of {@link #wrapDataSize}. If many small chunks are written
     * this can increase goodput, decrease the amount of calls to SSL_write, and decrease overall encryption operations.
     */
    private final class SslHandlerCoalescingBufferQueue extends AbstractCoalescingBufferQueue {

        SslHandlerCoalescingBufferQueue(Channel channel, int initSize) {
            super(channel, initSize);
        }

        @Override
        protected ByteBuf compose(ByteBufAllocator alloc, ByteBuf cumulation, ByteBuf next) {
            final int wrapDataSize = SslHandler.this.wrapDataSize;
            if (cumulation instanceof CompositeByteBuf) {
                CompositeByteBuf composite = (CompositeByteBuf) cumulation;
                int numComponents = composite.numComponents();
                if (numComponents == 0 ||
                        !attemptCopyToCumulation(composite.internalComponent(numComponents - 1), next, wrapDataSize)) {
                    composite.addComponent(true, next);
                }
                return composite;
            }
            return attemptCopyToCumulation(cumulation, next, wrapDataSize) ? cumulation :
                    copyAndCompose(alloc, cumulation, next);
        }

        @Override
        protected ByteBuf composeFirst(ByteBufAllocator allocator, ByteBuf first) {
            if (first instanceof CompositeByteBuf) {
                CompositeByteBuf composite = (CompositeByteBuf) first;
                if (engineType.wantsDirectBuffer) {
                    first = allocator.directBuffer(composite.readableBytes());
                } else {
                    first = allocator.heapBuffer(composite.readableBytes());
                }
                try {
                    first.writeBytes(composite);
                } catch (Throwable cause) {
                    first.release();
                    PlatformDependent.throwException(cause);
                }
                composite.release();
            }
            return first;
        }

        @Override
        protected ByteBuf removeEmptyValue() {
            return null;
        }
    }

    private static boolean attemptCopyToCumulation(ByteBuf cumulation, ByteBuf next, int wrapDataSize) {
        final int inReadableBytes = next.readableBytes();
        final int cumulationCapacity = cumulation.capacity();
        if (wrapDataSize - cumulation.readableBytes() >= inReadableBytes &&
                // Avoid using the same buffer if next's data would make cumulation exceed the wrapDataSize.
                // Only copy if there is enough space available and the capacity is large enough, and attempt to
                // resize if the capacity is small.
                (cumulation.isWritable(inReadableBytes) && cumulationCapacity >= wrapDataSize ||
                        cumulationCapacity < wrapDataSize &&
                                ensureWritableSuccess(cumulation.ensureWritable(inReadableBytes, false)))) {
            cumulation.writeBytes(next);
            next.release();
            return true;
        }
        return false;
    }

    private final class LazyChannelPromise extends DefaultPromise<Channel> {

        @Override
        protected EventExecutor executor() {
            if (ctx == null) {
                throw new IllegalStateException();
            }
            return ctx.executor();
        }

        @Override
        protected void checkDeadLock() {
            if (ctx == null) {
                // If ctx is null the handlerAdded(...) callback was not called, in this case the checkDeadLock()
                // method was called from another Thread then the one that is used by ctx.executor(). We need to
                // guard against this as a user can see a race if handshakeFuture().sync() is called but the
                // handlerAdded(..) method was not yet as it is called from the EventExecutor of the
                // ChannelHandlerContext. If we not guard against this super.checkDeadLock() would cause an
                // IllegalStateException when trying to call executor().
                return;
            }
            super.checkDeadLock();
        }
    }
}
