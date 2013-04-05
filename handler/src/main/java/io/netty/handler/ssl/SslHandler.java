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

import io.netty.buffer.BufUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFlushPromiseNotifier;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelHandlerUtil;
import io.netty.channel.ChannelInboundByteHandler;
import io.netty.channel.ChannelOutboundByteHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ChannelStateHandler;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.FileRegion;
import io.netty.util.concurrent.ImmediateExecutor;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
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
 * You must make sure not to write a message while the
 * {@linkplain #handshake() handshake} is in progress unless you are
 * renegotiating.  You will be notified by the {@link ChannelFuture} which is
 * returned by the {@link #handshake()} method when the handshake
 * process succeeds or fails.
 * <p>
 * Beside using the handshake {@link ChannelFuture} to get notified about the completation of the handshake it's
 * also possible to detect it by implement the
 * {@link ChannelStateHandler#userEventTriggered(ChannelHandlerContext, Object)}
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
 * <li>Initiate SSL handshake by calling {@link SslHandler#handshake()}.</li>
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
public class SslHandler
        extends ChannelDuplexHandler
        implements ChannelInboundByteHandler, ChannelOutboundByteHandler {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(SslHandler.class);

    private static final Pattern IGNORABLE_CLASS_IN_STACK = Pattern.compile(
            "^.*(?:Socket|Datagram|Sctp)Channel.*$");
    private static final Pattern IGNORABLE_ERROR_MESSAGE = Pattern.compile(
            "^.*(?:connection.*reset|connection.*closed|broken.*pipe).*$",
            Pattern.CASE_INSENSITIVE);

    private volatile ChannelHandlerContext ctx;
    private final SSLEngine engine;
    private final Executor delegatedTaskExecutor;
    private final ChannelFlushPromiseNotifier flushFutureNotifier = new ChannelFlushPromiseNotifier(true);

    private final boolean startTls;
    private boolean sentFirstMessage;
    private WritableByteChannel bufferChannel;

    private final LazyChannelPromise handshakePromise = new LazyChannelPromise();
    private final LazyChannelPromise sslCloseFuture = new LazyChannelPromise();
    private final CloseNotifyListener closeNotifyWriteListener = new CloseNotifyListener();

    private volatile long handshakeTimeoutMillis = 10000;
    private volatile long closeNotifyTimeoutMillis = 3000;

    private static final SslHandshakeCompletionEvent HANDSHAKE_SUCCESS_EVENT = new SslHandshakeCompletionEvent(null);

    /**
     * Creates a new instance.
     *
     * @param engine  the {@link SSLEngine} this handler will use
     */
    public SslHandler(SSLEngine engine) {
        this(engine, ImmediateExecutor.INSTANCE);
    }

    /**
     * Creates a new instance.
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
     * @param engine
     *        the {@link SSLEngine} this handler will use
     * @param delegatedTaskExecutor
     *        the {@link Executor} which will execute the delegated task
     *        that {@link SSLEngine#getDelegatedTask()} will return
     */
    public SslHandler(SSLEngine engine, Executor delegatedTaskExecutor) {
        this(engine, false, delegatedTaskExecutor);
    }

    /**
     * Creates a new instance.
     *
     * @param engine
     *        the {@link SSLEngine} this handler will use
     * @param startTls
     *        {@code true} if the first write request shouldn't be encrypted
     *        by the {@link SSLEngine}
     * @param delegatedTaskExecutor
     *        the {@link Executor} which will execute the delegated task
     *        that {@link SSLEngine#getDelegatedTask()} will return
     */
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
        return handshakeTimeoutMillis;
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
     * Returns a {@link ChannelFuture} that will get notified once the handshake completes.
     */
    public ChannelFuture handshake() {
        return handshake(new LazyChannelPromise());
    }

    /**
     * Returns a {@link ChannelPromise} which is notified when the handshake
     *         succeeds or fails.
     */
    public synchronized ChannelFuture handshake(final ChannelPromise promise) {
        handshakePromise.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    promise.setSuccess();
                } else {
                    promise.setFailure(future.cause());
                }
            }
        });

        return promise;
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
                future.addListener(closeNotifyWriteListener);
                try {
                    flush(ctx, future);
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
    public ChannelFuture sslCloseFuture() {
        return sslCloseFuture;
    }

    @Override
    public ByteBuf newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
        return ChannelHandlerUtil.allocate(ctx);
    }

    @Override
    public void discardInboundReadBytes(ChannelHandlerContext ctx) throws Exception {
        ctx.inboundByteBuffer().discardSomeReadBytes();
    }

    @Override
    public void freeInboundBuffer(ChannelHandlerContext ctx) throws Exception {
        ctx.inboundByteBuffer().release();
    }

    @Override
    public ByteBuf newOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
        return ChannelHandlerUtil.allocate(ctx);
    }

    @Override
    public void discardOutboundReadBytes(ChannelHandlerContext ctx) throws Exception {
        ctx.outboundByteBuffer().discardSomeReadBytes();
    }

    @Override
    public void freeOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
        ctx.outboundByteBuffer().release();
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
    public final void sendFile(ChannelHandlerContext ctx, FileRegion region, ChannelPromise promise) throws Exception {
        if (bufferChannel == null) {
            bufferChannel = new BufferChannel(ctx.outboundByteBuffer());
        }
        long written = 0;
        try {
            for (;;) {
                long localWritten = region.transferTo(bufferChannel, written);
                if (localWritten == -1) {
                    checkEOF(region, written);
                    flush(ctx, promise);
                    break;
                }
                written += localWritten;
                if (written >= region.count()) {
                    flush(ctx, promise);
                    break;
                }
            }
        } catch (IOException e) {
            promise.setFailure(e);
        } finally {
            region.release();
        }
    }

    private static void checkEOF(FileRegion region, long writtenBytes) throws IOException {
        if (writtenBytes < region.count()) {
            throw new EOFException("Expected to be able to write "
                    + region.count() + " bytes, but only wrote "
                    + writtenBytes);
        }
    }

    private static final class BufferChannel implements WritableByteChannel {
        private final ByteBuf buffer;

        BufferChannel(ByteBuf buffer) {
            this.buffer = buffer;
        }
        @Override
        public int write(ByteBuffer src) {
            int bytes = src.remaining();
            buffer.writeBytes(src);
            return bytes;
        }

        @Override
        public boolean isOpen() {
            return buffer.refCnt() > 0;
        }

        @Override
        public void close() {
            // NOOP
        }
    }

    @Override
    public void flush(final ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        flush0(ctx, promise, false);
    }

    private void flush0(ChannelHandlerContext ctx, ChannelPromise promise, boolean internal) throws Exception {
        final ByteBuf in = ctx.outboundByteBuffer();
        final ByteBuf out = ctx.nextOutboundByteBuffer();

        // Do not encrypt the first write request if this handler is
        // created with startTLS flag turned on.
        if (!internal && startTls && !sentFirstMessage) {
            sentFirstMessage = true;
            out.writeBytes(in);
            ctx.flush(promise);
            return;
        }

        if (ctx.executor() == ctx.channel().eventLoop()) {
            flushFutureNotifier.add(promise, in.readableBytes());
        } else {
            synchronized (flushFutureNotifier) {
                flushFutureNotifier.add(promise, in.readableBytes());
            }
        }

        boolean unwrapLater = false;
        int bytesConsumed = 0;
        try {
            for (;;) {
                SSLEngineResult result = wrap(engine, in, out);
                bytesConsumed += result.bytesConsumed();
                if (result.getStatus() == Status.CLOSED) {
                    // SSLEngine has been closed already.
                    // Any further write attempts should be denied.
                    if (in.isReadable()) {
                        in.clear();
                        SSLException e = new SSLException("SSLEngine already closed");
                        promise.setFailure(e);
                        ctx.fireExceptionCaught(e);
                        flush0(ctx, bytesConsumed, e);
                        bytesConsumed = 0;
                    }
                    break;
                } else {
                    switch (result.getHandshakeStatus()) {
                    case NEED_WRAP:
                        ctx.flush();
                        continue;
                    case NEED_UNWRAP:
                        if (ctx.inboundByteBuffer().isReadable()) {
                            unwrapLater = true;
                        }
                        break;
                    case NEED_TASK:
                        runDelegatedTasks();
                        continue;
                    case FINISHED:
                        setHandshakeSuccess();
                        continue;
                    case NOT_HANDSHAKING:
                        // Workaround for TLS False Start problem reported at:
                        // https://github.com/netty/netty/issues/1108#issuecomment-14266970
                        if (ctx.inboundByteBuffer().isReadable()) {
                            unwrapLater = true;
                        }
                        break;
                    default:
                        throw new IllegalStateException("Unknown handshake status: " + result.getHandshakeStatus());
                    }

                    if (result.bytesConsumed() == 0 && result.bytesProduced() == 0) {
                        break;
                    }
                }
            }

            if (unwrapLater) {
                inboundBufferUpdated(ctx);
            }
        } catch (SSLException e) {
            setHandshakeFailure(e);
            throw e;
        } finally {
            flush0(ctx, bytesConsumed);
        }
    }

    private void flush0(final ChannelHandlerContext ctx, final int bytesConsumed) {
        ctx.flush(ctx.newPromise().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (ctx.executor() == ctx.channel().eventLoop()) {
                    notifyFlushFutures(bytesConsumed, future);
                } else {
                    synchronized (flushFutureNotifier) {
                        notifyFlushFutures(bytesConsumed, future);
                    }
                }
            }

            private void notifyFlushFutures(final int bytesConsumed, ChannelFuture future) {
                if (future.isSuccess()) {
                    flushFutureNotifier.increaseWriteCounter(bytesConsumed);
                    flushFutureNotifier.notifyFlushFutures();
                } else {
                    flushFutureNotifier.notifyFlushFutures(future.cause());
                }
            }
        }));
    }

    private void flush0(final ChannelHandlerContext ctx, final int bytesConsumed, final Throwable cause) {
        ChannelFuture flushFuture = ctx.flush(ctx.newPromise().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (ctx.executor() == ctx.channel().eventLoop()) {
                    notifyFlushFutures(bytesConsumed, cause, future);
                } else {
                    synchronized (flushFutureNotifier) {
                        notifyFlushFutures(bytesConsumed, cause, future);
                    }
                }
            }

            private void notifyFlushFutures(int bytesConsumed, Throwable cause, ChannelFuture future) {
                flushFutureNotifier.increaseWriteCounter(bytesConsumed);
                if (future.isSuccess()) {
                    flushFutureNotifier.notifyFlushFutures(cause);
                } else {
                    flushFutureNotifier.notifyFlushFutures(cause, future.cause());
                }
            }
        }));

        safeClose(ctx, flushFuture, ctx.newPromise());
    }

    private static SSLEngineResult wrap(SSLEngine engine, ByteBuf in, ByteBuf out) throws SSLException {
        ByteBuffer in0 = in.nioBuffer();
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
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // Make sure the handshake future is notified when a connection has
        // been closed during handshake.
        setHandshakeFailure(null);

        try {
            inboundBufferUpdated(ctx);
        } finally {
            ctx.fireChannelInactive();
        }
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
                    Class<?> clazz = getClass().getClassLoader().loadClass(classname);

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
        return getEncryptedPacketLength(buffer) != -1;
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
    private static int getEncryptedPacketLength(ByteBuf buffer) {
        if (buffer.readableBytes() < 5) {
            throw new IllegalArgumentException("buffer must have at least 5 readable bytes");
        }

        int packetLength = 0;

        // SSLv3 or TLS - Check ContentType
        boolean tls;
        switch (buffer.getUnsignedByte(buffer.readerIndex())) {
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
            int majorVersion = buffer.getUnsignedByte(buffer.readerIndex() + 1);
            if (majorVersion == 3) {
                // SSLv3 or TLS
                packetLength = (getShort(buffer, buffer.readerIndex() + 3) & 0xFFFF) + 5;
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
            int headerLength = (buffer.getUnsignedByte(
                    buffer.readerIndex()) & 0x80) != 0 ? 2 : 3;
            int majorVersion = buffer.getUnsignedByte(
                    buffer.readerIndex() + headerLength + 1);
            if (majorVersion == 2 || majorVersion == 3) {
                // SSLv2
                if (headerLength == 2) {
                    packetLength = (getShort(buffer, buffer.readerIndex()) & 0x7FFF) + 2;
                } else {
                    packetLength = (getShort(buffer, buffer.readerIndex()) & 0x3FFF) + 3;
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
    public void inboundBufferUpdated(final ChannelHandlerContext ctx) throws Exception {
        final ByteBuf in = ctx.inboundByteBuffer();

        if (in.readableBytes() < 5) {
            return;
        }

        int packetLength = getEncryptedPacketLength(in);

        if (packetLength == -1) {
            // Bad data - discard the buffer and raise an exception.
            NotSslRecordException e = new NotSslRecordException(
                    "not an SSL/TLS record: " + BufUtil.hexDump(in));
            in.skipBytes(in.readableBytes());
            ctx.fireExceptionCaught(e);
            setHandshakeFailure(e);
            return;
        }

        assert packetLength > 0;

        final ByteBuf out = ctx.nextInboundByteBuffer();

        boolean wrapLater = false;
        int bytesProduced = 0;
        try {
            loop:
            for (;;) {
                SSLEngineResult result = unwrap(engine, in, out);
                bytesProduced += result.bytesProduced();

                switch (result.getStatus()) {
                case CLOSED:
                    // notify about the CLOSED state of the SSLEngine. See #137
                    sslCloseFuture.trySuccess();
                    break;
                case BUFFER_UNDERFLOW:
                    break loop;
                }

                switch (result.getHandshakeStatus()) {
                case NEED_UNWRAP:
                    break;
                case NEED_WRAP:
                    wrapLater = true;
                    break;
                case NEED_TASK:
                    runDelegatedTasks();
                    break;
                case FINISHED:
                    setHandshakeSuccess();
                    wrapLater = true;
                    continue;
                case NOT_HANDSHAKING:
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown handshake status: " + result.getHandshakeStatus());
                }

                if (result.bytesConsumed() == 0 && result.bytesProduced() == 0) {
                    break;
                }
            }

            if (wrapLater) {
                flush0(ctx, ctx.newPromise(), true);
            }
        } catch (SSLException e) {
            setHandshakeFailure(e);
            throw e;
        } finally {
            if (bytesProduced > 0) {
                ctx.fireInboundBufferUpdated();
            }
        }
    }

    /**
     * Reads a big-endian short integer from the buffer.  Please note that we do not use
     * {@link ByteBuf#getShort(int)} because it might be a little-endian buffer.
     */
    private static short getShort(ByteBuf buf, int offset) {
        return (short) (buf.getByte(offset) << 8 | buf.getByte(offset + 1) & 0xFF);
    }

    private static SSLEngineResult unwrap(SSLEngine engine, ByteBuf in, ByteBuf out) throws SSLException {
        ByteBuffer in0 = in.nioBuffer();
        for (;;) {
            ByteBuffer out0 = out.nioBuffer(out.writerIndex(), out.writableBytes());
            SSLEngineResult result = engine.unwrap(in0, out0);
            in.skipBytes(result.bytesConsumed());
            out.writerIndex(out.writerIndex() + result.bytesProduced());
            switch (result.getStatus()) {
            case BUFFER_OVERFLOW:
                out.ensureWritable(engine.getSession().getApplicationBufferSize());
                break;
            default:
                return result;
            }
        }
    }

    private void runDelegatedTasks() {
        for (;;) {
            Runnable task = engine.getDelegatedTask();
            if (task == null) {
                break;
            }

            delegatedTaskExecutor.execute(task);
        }
    }

    /**
     * Notify all the handshake futures about the successfully handshake
     */
    private void setHandshakeSuccess() {
        if (handshakePromise.trySuccess()) {
            ctx.fireUserEventTriggered(HANDSHAKE_SUCCESS_EVENT);
        }
    }

    /**
     * Notify all the handshake futures about the failure during the handshake.
     */
    private void setHandshakeFailure(Throwable cause) {
        // Release all resources such as internal buffers that SSLEngine
        // is managing.
        engine.closeOutbound();

        final boolean disconnected = cause == null || cause instanceof ClosedChannelException;
        try {
            engine.closeInbound();
        } catch (SSLException e) {
            if (!disconnected) {
                logger.warn("SSLEngine.closeInbound() raised an exception after a handshake failure.", e);
            } else if (!closeNotifyWriteListener.done) {
                logger.warn("SSLEngine.closeInbound() raised an exception due to closed connection.", e);
            } else {
                // cause == null && sentCloseNotify
                // closeInbound() will raise an exception with bogus truncation attack warning.
            }
        }

        if (handshakePromise.tryFailure(cause)) {
            ctx.fireUserEventTriggered(new SslHandshakeCompletionEvent(cause));
        }
        flush0(ctx, 0, cause);
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

        ChannelPromise closeNotifyFuture = ctx.newPromise().addListener(closeNotifyWriteListener);
        flush0(ctx, closeNotifyFuture, true);
        safeClose(ctx, closeNotifyFuture, promise);
    }

    @Override
    public void afterAdd(final ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;

        if (ctx.channel().isActive()) {
            // channelActvie() event has been fired already, which means this.channelActive() will
            // not be invoked. We have to initialize here instead.
            handshake0();
        } else {
            // channelActive() event has not been fired yet.  this.channelOpen() will be invoked
            // and initialization will occur there.
        }
    }

    private ChannelFuture handshake0() {
        final ScheduledFuture<?> timeoutFuture;
        if (handshakeTimeoutMillis > 0) {
            timeoutFuture = ctx.executor().schedule(new Runnable() {
                @Override
                public void run() {
                    if (handshakePromise.isDone()) {
                        return;
                    }

                    SSLException e = new SSLException("handshake timed out");
                    if (handshakePromise.tryFailure(e)) {
                        ctx.fireExceptionCaught(e);
                        ctx.close();
                    }
                }
            }, handshakeTimeoutMillis, TimeUnit.MILLISECONDS);
        } else {
            timeoutFuture = null;
        }

        handshakePromise.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture f) throws Exception {
                if (timeoutFuture != null) {
                    timeoutFuture.cancel(false);
                }
            }
        });
        try {
            engine.beginHandshake();
            flush0(ctx, ctx.newPromise(), true);
        } catch (Exception e) {
            if (handshakePromise.tryFailure(e)) {
                ctx.fireExceptionCaught(e);
                ctx.close();
            }
        }
        return handshakePromise;
    }

    /**
     * Calls {@link #handshake()} once the {@link Channel} is connected
     */
    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        if (!startTls && engine.getUseClientMode()) {
            // issue and handshake and add a listener to it which will fire an exception event if
            // an exception was thrown while doing the handshake
            handshake0().addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (!future.isSuccess()) {
                        ctx.pipeline().fireExceptionCaught(future.cause());
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
                if (ctx.channel().isActive()) {
                    ctx.close(promise);
                }
            }
        });
    }

    private static final class CloseNotifyListener implements ChannelFutureListener {
        volatile boolean done;

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if (future.isSuccess()) {
                if (done) {
                    throw new IllegalStateException("notified twice");
                }
                done = true;
            }
        }
    }

    private final class LazyChannelPromise extends DefaultChannelPromise {
        public LazyChannelPromise() {
            super(null);
        }

        @Override
        public Channel channel() {
            if (ctx == null) {
                throw new IllegalStateException();
            } else {
                return ctx.channel();
            }
        }
    }
}
