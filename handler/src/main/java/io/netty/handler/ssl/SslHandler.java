/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.PendingWriteQueue;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.ImmediateExecutor;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Adds <a href="http://en.wikipedia.org/wiki/Transport_Layer_Security">SSL
 * &middot; TLS</a> and StartTLS support to a {@link Channel}.  Please refer
 * to the <strong>"SecureChat"</strong> example in the distribution or the web
 * site for the detailed usage.
 *
 * <h3>Beginning the handshake</h3>
 * <p>
 * You must make sure not to write a message while the handshake is in progress unless you are
 * renegotiating.  You will be notified by the {@link Future} which is
 * returned by the {@link #handshakeFuture()} method when the handshake
 * process succeeds or fails.
 * <p>
 * Beside using the handshake {@link ChannelFuture} to get notified about the completation of the handshake it's
 * also possible to detect it by implement the
 * {@link ChannelInboundHandler#userEventTriggered(ChannelHandlerContext, Object)}
 * method and check for a {@link SslHandshakeCompletionEvent}.
 *
 * <h3>Handshake</h3>
 * <p>
 * The handshake will be automaticly issued for you once the {@link Channel} is active and
 * {@link SSLEngine#getUseClientMode()} returns {@code true}.
 * So no need to bother with it by your self.
 *
 * <h3>Closing the session</h3>
 * <p>
 * To close the SSL session, the {@link #close()} method should be
 * called to send the {@code close_notify} message to the remote peer.  One
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
 * <a href="http://en.wikipedia.org/wiki/STARTTLS">StartTLS</a> is the
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

    private static final SSLException SSLENGINE_CLOSED = new SSLException("SSLEngine closed already");
    private static final SSLException HANDSHAKE_TIMED_OUT = new SSLException("handshake timed out");
    private static final ClosedChannelException CHANNEL_CLOSED = new ClosedChannelException();

    static {
        SSLENGINE_CLOSED.setStackTrace(EmptyArrays.EMPTY_STACK_TRACE);
        HANDSHAKE_TIMED_OUT.setStackTrace(EmptyArrays.EMPTY_STACK_TRACE);
        CHANNEL_CLOSED.setStackTrace(EmptyArrays.EMPTY_STACK_TRACE);
    }

    private volatile ChannelHandlerContext ctx;
    private final SSLEngine engine;
    private final int maxPacketBufferSize;
    private final Executor delegatedTaskExecutor;

    // BEGIN Platform-dependent flags

    /**
     * {@code trus} if and only if {@link SSLEngine} expects a direct buffer.
     */
    private final boolean wantsDirectBuffer;
    /**
     * {@code true} if and only if {@link SSLEngine#wrap(ByteBuffer, ByteBuffer)} requires the output buffer
     * to be always as large as {@link #maxPacketBufferSize} even if the input buffer contains small amount of data.
     * <p>
     * If this flag is {@code false}, we allocate a smaller output buffer.
     * </p>
     */
    private final boolean wantsLargeOutboundNetworkBuffer;
    /**
     * {@code true} if and only if {@link SSLEngine#unwrap(ByteBuffer, ByteBuffer)} expects a heap buffer rather than
     * a direct buffer.  For an unknown reason, JDK8 SSLEngine causes JVM to crash when its cipher suite uses Galois
     * Counter Mode (GCM).
     */
    private boolean wantsInboundHeapBuffer;

    // END Platform-dependent flags

    private final boolean startTls;
    private boolean sentFirstMessage;
    private boolean flushedBeforeHandshakeDone;
    private PendingWriteQueue pendingUnencryptedWrites;

    private final LazyChannelPromise handshakePromise = new LazyChannelPromise();
    private final LazyChannelPromise sslCloseFuture = new LazyChannelPromise();

    /**
     * Set by wrap*() methods when something is produced.
     * {@link #channelReadComplete(ChannelHandlerContext)} will check this flag, clear it, and call ctx.flush().
     */
    private boolean needsFlush;

    private int packetLength;

    private volatile long handshakeTimeoutMillis = 10000;
    private volatile long closeNotifyTimeoutMillis = 3000;

    /**
     * Creates a new instance.
     *
     * @param engine  the {@link SSLEngine} this handler will use
     */
    public SslHandler(SSLEngine engine) {
        this(engine, false);
    }

    /**
     * Creates a new instance.
     *
     * @param engine    the {@link SSLEngine} this handler will use
     * @param startTls  {@code true} if the first write request shouldn't be
     *                  encrypted by the {@link SSLEngine}
     */
    @SuppressWarnings("deprecation")
    public SslHandler(SSLEngine engine, boolean startTls) {
        this(engine, startTls, ImmediateExecutor.INSTANCE);
    }

    /**
     * @deprecated Use {@link #SslHandler(SSLEngine)} instead.
     */
    @Deprecated
    public SslHandler(SSLEngine engine, Executor delegatedTaskExecutor) {
        this(engine, false, delegatedTaskExecutor);
    }

    /**
     * @deprecated Use {@link #SslHandler(SSLEngine, boolean)} instead.
     */
    @Deprecated
    public SslHandler(SSLEngine engine, boolean startTls, Executor delegatedTaskExecutor) {
        if (engine == null) {
            throw new NullPointerException("engine");
        }
        if (delegatedTaskExecutor == null) {
            throw new NullPointerException("delegatedTaskExecutor");
        }
        this.engine = engine;
        this.delegatedTaskExecutor = delegatedTaskExecutor;
        this.startTls = startTls;
        maxPacketBufferSize = engine.getSession().getPacketBufferSize();

        wantsDirectBuffer = engine instanceof OpenSslEngine;
        wantsLargeOutboundNetworkBuffer = !(engine instanceof OpenSslEngine);
    }

    public long getHandshakeTimeoutMillis() {
        return handshakeTimeoutMillis;
    }

    public void setHandshakeTimeout(long handshakeTimeout, TimeUnit unit) {
        if (unit == null) {
            throw new NullPointerException("unit");
        }

        setHandshakeTimeoutMillis(unit.toMillis(handshakeTimeout));
    }

    public void setHandshakeTimeoutMillis(long handshakeTimeoutMillis) {
        if (handshakeTimeoutMillis < 0) {
            throw new IllegalArgumentException(
                    "handshakeTimeoutMillis: " + handshakeTimeoutMillis + " (expected: >= 0)");
        }
        this.handshakeTimeoutMillis = handshakeTimeoutMillis;
    }

    public long getCloseNotifyTimeoutMillis() {
        return closeNotifyTimeoutMillis;
    }

    public void setCloseNotifyTimeout(long closeNotifyTimeout, TimeUnit unit) {
        if (unit == null) {
            throw new NullPointerException("unit");
        }

        setCloseNotifyTimeoutMillis(unit.toMillis(closeNotifyTimeout));
    }

    public void setCloseNotifyTimeoutMillis(long closeNotifyTimeoutMillis) {
        if (closeNotifyTimeoutMillis < 0) {
            throw new IllegalArgumentException(
                    "closeNotifyTimeoutMillis: " + closeNotifyTimeoutMillis + " (expected: >= 0)");
        }
        this.closeNotifyTimeoutMillis = closeNotifyTimeoutMillis;
    }

    /**
     * Returns the {@link SSLEngine} which is used by this handler.
     */
    public SSLEngine engine() {
        return engine;
    }

    /**
     * Returns a {@link Future} that will get notified once the handshake completes.
     */
    public Future<Channel> handshakeFuture() {
        return handshakePromise;
    }

    /**
     * Sends an SSL {@code close_notify} message to the specified channel and
     * destroys the underlying {@link SSLEngine}.
     */
    public ChannelFuture close() {
        return close(ctx.newPromise());
    }

    /**
     * See {@link #close()}
     */
    public ChannelFuture close(final ChannelPromise future) {
        final ChannelHandlerContext ctx = this.ctx;
        ctx.executor().execute(new Runnable() {
            @Override
            public void run() {
                engine.closeOutbound();
                try {
                    write(ctx, Unpooled.EMPTY_BUFFER, future);
                    flush(ctx);
                } catch (Exception e) {
                    if (!future.tryFailure(e)) {
                        logger.warn("flush() raised a masked exception.", e);
                    }
                }
            }
        });

        return future;
    }

    /**
     * Return the {@link ChannelFuture} that will get notified if the inbound of the {@link SSLEngine} will get closed.
     *
     * This method will return the same {@link ChannelFuture} all the time.
     *
     * For more informations see the apidocs of {@link SSLEngine}
     *
     */
    public Future<Channel> sslCloseFuture() {
        return sslCloseFuture;
    }

    @Override
    public void handlerRemoved0(ChannelHandlerContext ctx) throws Exception {
        if (!pendingUnencryptedWrites.isEmpty()) {
            // Check if queue is not empty first because create a new ChannelException is expensive
            pendingUnencryptedWrites.removeAndFailAll(new ChannelException("Pending write on removal of SslHandler"));
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
    public void read(ChannelHandlerContext ctx) {
        ctx.read();
    }

    @Override
    public void write(final ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        pendingUnencryptedWrites.add(msg, promise);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        // Do not encrypt the first write request if this handler is
        // created with startTLS flag turned on.
        if (startTls && !sentFirstMessage) {
            sentFirstMessage = true;
            pendingUnencryptedWrites.removeAndWriteAll();
            ctx.flush();
            return;
        }
        if (pendingUnencryptedWrites.isEmpty()) {
            pendingUnencryptedWrites.add(Unpooled.EMPTY_BUFFER, ctx.voidPromise());
        }
        if (!handshakePromise.isDone()) {
            flushedBeforeHandshakeDone = true;
        }
        wrap(ctx, false);
        ctx.flush();
    }

    private void wrap(ChannelHandlerContext ctx, boolean inUnwrap) throws SSLException {
        ByteBuf out = null;
        ChannelPromise promise = null;
        try {
            for (;;) {
                Object msg = pendingUnencryptedWrites.current();
                if (msg == null) {
                    break;
                }

                if (!(msg instanceof ByteBuf)) {
                    pendingUnencryptedWrites.removeAndWrite();
                    continue;
                }

                ByteBuf buf = (ByteBuf) msg;
                if (out == null) {
                    out = allocateOutNetBuf(ctx, buf.readableBytes());
                }

                SSLEngineResult result = wrap(engine, buf, out);

                if (!buf.isReadable()) {
                    promise = pendingUnencryptedWrites.remove();
                } else {
                    promise = null;
                }

                if (result.getStatus() == Status.CLOSED) {
                    // SSLEngine has been closed already.
                    // Any further write attempts should be denied.
                    pendingUnencryptedWrites.removeAndFailAll(SSLENGINE_CLOSED);
                    return;
                } else {
                    switch (result.getHandshakeStatus()) {
                        case NEED_TASK:
                            runDelegatedTasks();
                            break;
                        case FINISHED:
                            setHandshakeSuccess();
                            // deliberate fall-through
                        case NOT_HANDSHAKING:
                            setHandshakeSuccessIfStillHandshaking();
                            // deliberate fall-through
                        case NEED_WRAP:
                            finishWrap(ctx, out, promise, inUnwrap);
                            promise = null;
                            out = null;
                            break;
                        case NEED_UNWRAP:
                            return;
                        default:
                            throw new IllegalStateException(
                                    "Unknown handshake status: " + result.getHandshakeStatus());
                    }
                }
            }
        } catch (SSLException e) {
            setHandshakeFailure(e);
            throw e;
        } finally {
            finishWrap(ctx, out, promise, inUnwrap);
        }
    }

    private void finishWrap(ChannelHandlerContext ctx, ByteBuf out, ChannelPromise promise, boolean inUnwrap) {
        if (out == null) {
            out = Unpooled.EMPTY_BUFFER;
        } else if (!out.isReadable()) {
            out.release();
            out = Unpooled.EMPTY_BUFFER;
        }

        if (promise != null) {
            ctx.write(out, promise);
        } else {
            ctx.write(out);
        }

        if (inUnwrap) {
            needsFlush = true;
        }
    }

    private void wrapNonAppData(ChannelHandlerContext ctx, boolean inUnwrap) throws SSLException {
        ByteBuf out = null;
        try {
            for (;;) {
                if (out == null) {
                    out = allocateOutNetBuf(ctx, 0);
                }
                SSLEngineResult result = wrap(engine, Unpooled.EMPTY_BUFFER, out);

                if (result.bytesProduced() > 0) {
                    ctx.write(out);
                    if (inUnwrap) {
                        needsFlush = true;
                    }
                    out = null;
                }

                switch (result.getHandshakeStatus()) {
                    case FINISHED:
                        setHandshakeSuccess();
                        break;
                    case NEED_TASK:
                        runDelegatedTasks();
                        break;
                    case NEED_UNWRAP:
                        if (!inUnwrap) {
                            unwrapNonAppData(ctx);
                        }
                        break;
                    case NEED_WRAP:
                        break;
                    case NOT_HANDSHAKING:
                        setHandshakeSuccessIfStillHandshaking();
                        // Workaround for TLS False Start problem reported at:
                        // https://github.com/netty/netty/issues/1108#issuecomment-14266970
                        if (!inUnwrap) {
                            unwrapNonAppData(ctx);
                        }
                        break;
                    default:
                        throw new IllegalStateException("Unknown handshake status: " + result.getHandshakeStatus());
                }

                if (result.bytesProduced() == 0) {
                    break;
                }
            }
        } catch (SSLException e) {
            setHandshakeFailure(e);
            throw e;
        }  finally {
            if (out != null) {
                out.release();
            }
        }
    }

    private SSLEngineResult wrap(SSLEngine engine, ByteBuf in, ByteBuf out) throws SSLException {
        ByteBuffer in0 = in.nioBuffer();
        if (!in0.isDirect()) {
            ByteBuffer newIn0 = ByteBuffer.allocateDirect(in0.remaining());
            newIn0.put(in0).flip();
            in0 = newIn0;
        }

        for (;;) {
            ByteBuffer out0 = out.nioBuffer(out.writerIndex(), out.writableBytes());
            SSLEngineResult result = engine.wrap(in0, out0);
            in.skipBytes(result.bytesConsumed());
            out.writerIndex(out.writerIndex() + result.bytesProduced());

            switch (result.getStatus()) {
                case BUFFER_OVERFLOW:
                    out.ensureWritable(maxPacketBufferSize);
                    break;
                default:
                    return result;
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // Make sure to release SSLEngine,
        // and notify the handshake future if the connection has been closed during handshake.
        setHandshakeFailure(CHANNEL_CLOSED);
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (ignoreException(cause)) {
            // It is safe to ignore the 'connection reset by peer' or
            // 'broken pipe' error after sending close_notify.
            if (logger.isDebugEnabled()) {
                logger.debug(
                        "Swallowing a harmless 'connection reset by peer / broken pipe' error that occurred " +
                                "while writing close_notify in response to the peer's close_notify", cause);
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
        if (!(t instanceof SSLException) && t instanceof IOException && sslCloseFuture.isDone()) {
            String message = String.valueOf(t.getMessage()).toLowerCase();

            // first try to match connection reset / broke peer based on the regex. This is the fastest way
            // but may fail on different jdk impls or OS's
            if (IGNORABLE_ERROR_MESSAGE.matcher(message).matches()) {
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
                } catch (ClassNotFoundException e) {
                    // This should not happen just ignore
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
        if (buffer.readableBytes() < 5) {
            throw new IllegalArgumentException("buffer must have at least 5 readable bytes");
        }
        return getEncryptedPacketLength(buffer, buffer.readerIndex()) != -1;
    }

    /**
     * Return how much bytes can be read out of the encrypted data. Be aware that this method will not increase
     * the readerIndex of the given {@link ByteBuf}.
     *
     * @param   buffer
     *                  The {@link ByteBuf} to read from. Be aware that it must have at least 5 bytes to read,
     *                  otherwise it will throw an {@link IllegalArgumentException}.
     * @return length
     *                  The length of the encrypted packet that is included in the buffer. This will
     *                  return {@code -1} if the given {@link ByteBuf} is not encrypted at all.
     * @throws IllegalArgumentException
     *                  Is thrown if the given {@link ByteBuf} has not at least 5 bytes to read.
     */
    private static int getEncryptedPacketLength(ByteBuf buffer, int offset) {
        int packetLength = 0;

        // SSLv3 or TLS - Check ContentType
        boolean tls;
        switch (buffer.getUnsignedByte(offset)) {
            case 20:  // change_cipher_spec
            case 21:  // alert
            case 22:  // handshake
            case 23:  // application_data
                tls = true;
                break;
            default:
                // SSLv2 or bad data
                tls = false;
        }

        if (tls) {
            // SSLv3 or TLS - Check ProtocolVersion
            int majorVersion = buffer.getUnsignedByte(offset + 1);
            if (majorVersion == 3) {
                // SSLv3 or TLS
                packetLength = buffer.getUnsignedShort(offset + 3) + 5;
                if (packetLength <= 5) {
                    // Neither SSLv3 or TLSv1 (i.e. SSLv2 or bad data)
                    tls = false;
                }
            } else {
                // Neither SSLv3 or TLSv1 (i.e. SSLv2 or bad data)
                tls = false;
            }
        }

        if (!tls) {
            // SSLv2 or bad data - Check the version
            boolean sslv2 = true;
            int headerLength = (buffer.getUnsignedByte(offset) & 0x80) != 0 ? 2 : 3;
            int majorVersion = buffer.getUnsignedByte(offset + headerLength + 1);
            if (majorVersion == 2 || majorVersion == 3) {
                // SSLv2
                if (headerLength == 2) {
                    packetLength = (buffer.getShort(offset) & 0x7FFF) + 2;
                } else {
                    packetLength = (buffer.getShort(offset) & 0x3FFF) + 3;
                }
                if (packetLength <= headerLength) {
                    sslv2 = false;
                }
            } else {
                sslv2 = false;
            }

            if (!sslv2) {
                return -1;
            }
        }
        return packetLength;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws SSLException {

        final int startOffset = in.readerIndex();
        final int endOffset = in.writerIndex();
        int offset = startOffset;
        int totalLength = 0;

        // If we calculated the length of the current SSL record before, use that information.
        if (packetLength > 0) {
            if (endOffset - startOffset < packetLength) {
                return;
            } else {
                offset += packetLength;
                totalLength = packetLength;
                packetLength = 0;
            }
        }

        boolean nonSslRecord = false;

        while (totalLength < OpenSslEngine.MAX_ENCRYPTED_PACKET_LENGTH) {
            final int readableBytes = endOffset - offset;
            if (readableBytes < 5) {
                break;
            }

            final int packetLength = getEncryptedPacketLength(in, offset);
            if (packetLength == -1) {
                nonSslRecord = true;
                break;
            }

            assert packetLength > 0;

            if (packetLength > readableBytes) {
                // wait until the whole packet can be read
                this.packetLength = packetLength;
                break;
            }

            int newTotalLength = totalLength + packetLength;
            if (newTotalLength > OpenSslEngine.MAX_ENCRYPTED_PACKET_LENGTH) {
                // Don't read too much.
                break;
            }

            // We have a whole packet.
            // Increment the offset to handle the next packet.
            offset += packetLength;
            totalLength = newTotalLength;
        }

        if (totalLength > 0) {
            // The buffer contains one or more full SSL records.
            // Slice out the whole packet so unwrap will only be called with complete packets.
            // Also directly reset the packetLength. This is needed as unwrap(..) may trigger
            // decode(...) again via:
            // 1) unwrap(..) is called
            // 2) wrap(...) is called from within unwrap(...)
            // 3) wrap(...) calls unwrapLater(...)
            // 4) unwrapLater(...) calls decode(...)
            //
            // See https://github.com/netty/netty/issues/1534

            in.skipBytes(totalLength);
            final ByteBuffer inNetBuf = in.nioBuffer(startOffset, totalLength);
            unwrap(ctx, inNetBuf, totalLength);
            assert !inNetBuf.hasRemaining() || engine.isInboundDone();
        }

        if (nonSslRecord) {
            // Not an SSL/TLS packet
            NotSslRecordException e = new NotSslRecordException(
                    "not an SSL/TLS record: " + ByteBufUtil.hexDump(in));
            in.skipBytes(in.readableBytes());
            ctx.fireExceptionCaught(e);
            setHandshakeFailure(e);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        if (needsFlush) {
            needsFlush = false;
            ctx.flush();
        }
        super.channelReadComplete(ctx);
    }

    /**
     * Calls {@link SSLEngine#unwrap(ByteBuffer, ByteBuffer)} with an empty buffer to handle handshakes, etc.
     */
    private void unwrapNonAppData(ChannelHandlerContext ctx) throws SSLException {
        unwrap(ctx, Unpooled.EMPTY_BUFFER.nioBuffer(), 0);
    }

    /**
     * Unwraps inbound SSL records.
     */
    private void unwrap(
            ChannelHandlerContext ctx, ByteBuffer packet, int initialOutAppBufCapacity) throws SSLException {

        // If SSLEngine expects a heap buffer for unwrapping, do the conversion.
        final ByteBuffer oldPacket;
        final ByteBuf newPacket;
        final int oldPos = packet.position();
        if (wantsInboundHeapBuffer && packet.isDirect()) {
            newPacket = ctx.alloc().heapBuffer(packet.limit() - oldPos);
            newPacket.writeBytes(packet);
            oldPacket = packet;
            packet = newPacket.nioBuffer();
        } else {
            oldPacket = null;
            newPacket = null;
        }

        boolean wrapLater = false;
        ByteBuf decodeOut = allocate(ctx, initialOutAppBufCapacity);
        try {
            for (;;) {
                final SSLEngineResult result = unwrap(engine, packet, decodeOut);
                final Status status = result.getStatus();
                final HandshakeStatus handshakeStatus = result.getHandshakeStatus();
                final int produced = result.bytesProduced();
                final int consumed = result.bytesConsumed();

                if (status == Status.CLOSED) {
                    // notify about the CLOSED state of the SSLEngine. See #137
                    sslCloseFuture.trySuccess(ctx.channel());
                    break;
                }

                switch (handshakeStatus) {
                    case NEED_UNWRAP:
                        break;
                    case NEED_WRAP:
                        wrapNonAppData(ctx, true);
                        break;
                    case NEED_TASK:
                        runDelegatedTasks();
                        break;
                    case FINISHED:
                        setHandshakeSuccess();
                        wrapLater = true;
                        continue;
                    case NOT_HANDSHAKING:
                        if (setHandshakeSuccessIfStillHandshaking()) {
                            wrapLater = true;
                            continue;
                        }
                        if (flushedBeforeHandshakeDone) {
                            // We need to call wrap(...) in case there was a flush done before the handshake completed.
                            //
                            // See https://github.com/netty/netty/pull/2437
                            flushedBeforeHandshakeDone = false;
                            wrapLater = true;
                        }

                        break;
                    default:
                        throw new IllegalStateException("Unknown handshake status: " + handshakeStatus);
                }

                if (status == Status.BUFFER_UNDERFLOW || consumed == 0 && produced == 0) {
                    break;
                }
            }

            if (wrapLater) {
                wrap(ctx, true);
            }
        } catch (SSLException e) {
            setHandshakeFailure(e);
            throw e;
        } finally {
            // If we converted packet into a heap buffer at the beginning of this method,
            // we should synchronize the position of the original buffer.
            if (newPacket != null) {
                oldPacket.position(oldPos + packet.position());
                newPacket.release();
            }

            if (decodeOut.isReadable()) {
                ctx.fireChannelRead(decodeOut);
            } else {
                decodeOut.release();
            }
        }
    }

    private static SSLEngineResult unwrap(SSLEngine engine, ByteBuffer in, ByteBuf out) throws SSLException {
        int overflows = 0;
        for (;;) {
            ByteBuffer out0 = out.nioBuffer(out.writerIndex(), out.writableBytes());
            SSLEngineResult result = engine.unwrap(in, out0);
            out.writerIndex(out.writerIndex() + result.bytesProduced());
            switch (result.getStatus()) {
                case BUFFER_OVERFLOW:
                    int max = engine.getSession().getApplicationBufferSize();
                    switch (overflows ++) {
                        case 0:
                            out.ensureWritable(Math.min(max, in.remaining()));
                            break;
                        default:
                            out.ensureWritable(max);
                    }
                    break;
                default:
                    return result;
            }
        }
    }

    /**
     * Fetches all delegated tasks from the {@link SSLEngine} and runs them via the {@link #delegatedTaskExecutor}.
     * If the {@link #delegatedTaskExecutor} is {@link ImmediateExecutor}, just call {@link Runnable#run()} directly
     * instead of using {@link Executor#execute(Runnable)}.  Otherwise, run the tasks via
     * the {@link #delegatedTaskExecutor} and wait until the tasks are finished.
     */
    private void runDelegatedTasks() {
        if (delegatedTaskExecutor == ImmediateExecutor.INSTANCE) {
            for (;;) {
                Runnable task = engine.getDelegatedTask();
                if (task == null) {
                    break;
                }

                task.run();
            }
        } else {
            final List<Runnable> tasks = new ArrayList<Runnable>(2);
            for (;;) {
                final Runnable task = engine.getDelegatedTask();
                if (task == null) {
                    break;
                }

                tasks.add(task);
            }

            if (tasks.isEmpty()) {
                return;
            }

            final CountDownLatch latch = new CountDownLatch(1);
            delegatedTaskExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        for (Runnable task: tasks) {
                            task.run();
                        }
                    } catch (Exception e) {
                        ctx.fireExceptionCaught(e);
                    } finally {
                        latch.countDown();
                    }
                }
            });

            boolean interrupted = false;
            while (latch.getCount() != 0) {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    // Interrupt later.
                    interrupted = true;
                }
            }

            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Works around some Android {@link SSLEngine} implementations that skip {@link HandshakeStatus#FINISHED} and
     * go straight into {@link HandshakeStatus#NOT_HANDSHAKING} when handshake is finished.
     *
     * @return {@code true} if and only if the workaround has been applied and thus {@link #handshakeFuture} has been
     *         marked as success by this method
     */
    private boolean setHandshakeSuccessIfStillHandshaking() {
        if (!handshakePromise.isDone()) {
            setHandshakeSuccess();
            return true;
        }
        return false;
    }

    /**
     * Notify all the handshake futures about the successfully handshake
     */
    private void setHandshakeSuccess() {
        // Work around the JVM crash which occurs when a cipher suite with GCM enabled.
        final String cipherSuite = String.valueOf(engine.getSession().getCipherSuite());
        if (!wantsDirectBuffer && (cipherSuite.contains("_GCM_") || cipherSuite.contains("-GCM-"))) {
            wantsInboundHeapBuffer = true;
        }

        if (handshakePromise.trySuccess(ctx.channel())) {
            if (logger.isDebugEnabled()) {
                logger.debug(ctx.channel() + " HANDSHAKEN: " + engine.getSession().getCipherSuite());
            }
            ctx.fireUserEventTriggered(SslHandshakeCompletionEvent.SUCCESS);
        }
    }

    /**
     * Notify all the handshake futures about the failure during the handshake.
     */
    private void setHandshakeFailure(Throwable cause) {
        // Release all resources such as internal buffers that SSLEngine
        // is managing.
        engine.closeOutbound();

        try {
            engine.closeInbound();
        } catch (SSLException e) {
            // only log in debug mode as it most likely harmless and latest chrome still trigger
            // this all the time.
            //
            // See https://github.com/netty/netty/issues/1340
            String msg = e.getMessage();
            if (msg == null || !msg.contains("possible truncation attack")) {
                logger.debug("SSLEngine.closeInbound() raised an exception.", e);
            }
        }
        notifyHandshakeFailure(cause);
        pendingUnencryptedWrites.removeAndFailAll(cause);
    }

    private void notifyHandshakeFailure(Throwable cause) {
        if (handshakePromise.tryFailure(cause)) {
            ctx.fireUserEventTriggered(new SslHandshakeCompletionEvent(cause));
            ctx.close();
        }
    }

    private void closeOutboundAndChannel(
            final ChannelHandlerContext ctx, final ChannelPromise promise, boolean disconnect) throws Exception {
        if (!ctx.channel().isActive()) {
            if (disconnect) {
                ctx.disconnect(promise);
            } else {
                ctx.close(promise);
            }
            return;
        }

        engine.closeOutbound();

        ChannelPromise closeNotifyFuture = ctx.newPromise();
        write(ctx, Unpooled.EMPTY_BUFFER, closeNotifyFuture);
        flush(ctx);
        safeClose(ctx, closeNotifyFuture, promise);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
        pendingUnencryptedWrites = new PendingWriteQueue(ctx);

        if (ctx.channel().isActive() && engine.getUseClientMode()) {
            // channelActive() event has been fired already, which means this.channelActive() will
            // not be invoked. We have to initialize here instead.
            handshake();
        } else {
            // channelActive() event has not been fired yet.  this.channelOpen() will be invoked
            // and initialization will occur there.
        }
    }

    private Future<Channel> handshake() {
        final ScheduledFuture<?> timeoutFuture;
        if (handshakeTimeoutMillis > 0) {
            timeoutFuture = ctx.executor().schedule(new Runnable() {
                @Override
                public void run() {
                    if (handshakePromise.isDone()) {
                        return;
                    }
                    notifyHandshakeFailure(HANDSHAKE_TIMED_OUT);
                }
            }, handshakeTimeoutMillis, TimeUnit.MILLISECONDS);
        } else {
            timeoutFuture = null;
        }

        handshakePromise.addListener(new GenericFutureListener<Future<Channel>>() {
            @Override
            public void operationComplete(Future<Channel> f) throws Exception {
                if (timeoutFuture != null) {
                    timeoutFuture.cancel(false);
                }
            }
        });
        try {
            engine.beginHandshake();
            wrapNonAppData(ctx, false);
            ctx.flush();
        } catch (Exception e) {
            notifyHandshakeFailure(e);
        }
        return handshakePromise;
    }

    /**
     * Issues a SSL handshake once connected when used in client-mode
     */
    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        if (!startTls && engine.getUseClientMode()) {
            // issue and handshake and add a listener to it which will fire an exception event if
            // an exception was thrown while doing the handshake
            handshake().addListener(new GenericFutureListener<Future<Channel>>() {
                @Override
                public void operationComplete(Future<Channel> future) throws Exception {
                    if (!future.isSuccess()) {
                        logger.debug("Failed to complete handshake", future.cause());
                        ctx.close();
                    }
                }
            });
        }
        ctx.fireChannelActive();
    }

    private void safeClose(
            final ChannelHandlerContext ctx, ChannelFuture flushFuture,
            final ChannelPromise promise) {
        if (!ctx.channel().isActive()) {
            ctx.close(promise);
            return;
        }

        final ScheduledFuture<?> timeoutFuture;
        if (closeNotifyTimeoutMillis > 0) {
            // Force-close the connection if close_notify is not fully sent in time.
            timeoutFuture = ctx.executor().schedule(new Runnable() {
                @Override
                public void run() {
                    logger.warn(
                            ctx.channel() + " last write attempt timed out." +
                                    " Force-closing the connection.");
                    ctx.close(promise);
                }
            }, closeNotifyTimeoutMillis, TimeUnit.MILLISECONDS);
        } else {
            timeoutFuture = null;
        }

        // Close the connection if close_notify is sent in time.
        flushFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture f)
                    throws Exception {
                if (timeoutFuture != null) {
                    timeoutFuture.cancel(false);
                }
                // Trigger the close in all cases to make sure the promise is notified
                // See https://github.com/netty/netty/issues/2358
                ctx.close(promise);
            }
        });
    }

    /**
     * Always prefer a direct buffer when it's pooled, so that we reduce the number of memory copies
     * in {@link OpenSslEngine}.
     */
    private ByteBuf allocate(ChannelHandlerContext ctx, int capacity) {
        ByteBufAllocator alloc = ctx.alloc();
        if (wantsDirectBuffer) {
            return alloc.directBuffer(capacity);
        } else {
            return alloc.buffer(capacity);
        }
    }

    /**
     * Allocates an outbound network buffer for {@link SSLEngine#wrap(ByteBuffer, ByteBuffer)} which can encrypt
     * the specified amount of pending bytes.
     */
    private ByteBuf allocateOutNetBuf(ChannelHandlerContext ctx, int pendingBytes) {
        if (wantsLargeOutboundNetworkBuffer) {
            return allocate(ctx, maxPacketBufferSize);
        } else {
            return allocate(ctx, Math.min(
                    pendingBytes + OpenSslEngine.MAX_ENCRYPTION_OVERHEAD_LENGTH,
                    maxPacketBufferSize));
        }
    }

    private final class LazyChannelPromise extends DefaultPromise<Channel> {

        @Override
        protected EventExecutor executor() {
            if (ctx == null) {
                throw new IllegalStateException();
            }
            return ctx.executor();
        }
    }
}
