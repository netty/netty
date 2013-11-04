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
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.ImmediateExecutor;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.PendingWrite;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.RecyclableArrayList;
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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
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
    private final Executor delegatedTaskExecutor;

    private final boolean startTls;
    private boolean sentFirstMessage;

    private final LazyChannelPromise handshakePromise = new LazyChannelPromise();
    private final LazyChannelPromise sslCloseFuture = new LazyChannelPromise();
    private final Deque<PendingWrite> pendingUnencryptedWrites = new ArrayDeque<PendingWrite>();

    private int packetLength;
    private ByteBuf decodeOut;

    private volatile long handshakeTimeoutMillis = 10000;
    private volatile long closeNotifyTimeoutMillis = 3000;

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
        if (decodeOut != null) {
            decodeOut.release();
            decodeOut = null;
        }
        for (;;) {
            PendingWrite write = pendingUnencryptedWrites.poll();
            if (write == null) {
                break;
            }
            write.failAndRecycle(new ChannelException("Pending write on removal of SslHandler"));
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
        pendingUnencryptedWrites.add(PendingWrite.newInstance(msg, promise));
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        // Do not encrypt the first write request if this handler is
        // created with startTLS flag turned on.
        if (startTls && !sentFirstMessage) {
            sentFirstMessage = true;
            for (;;) {
                PendingWrite pendingWrite = pendingUnencryptedWrites.poll();
                if (pendingWrite == null) {
                    break;
                }
                ctx.write(pendingWrite.msg(), (ChannelPromise) pendingWrite.recycleAndGet());
            }
            ctx.flush();
            return;
        }
        if (pendingUnencryptedWrites.isEmpty()) {
            pendingUnencryptedWrites.add(PendingWrite.newInstance(Unpooled.EMPTY_BUFFER, null));
        }
        flush0(ctx);
    }

    private void flush0(ChannelHandlerContext ctx) throws SSLException {
        ByteBuf out = null;
        ChannelPromise promise = null;
        try {
            for (;;) {
                PendingWrite pending = pendingUnencryptedWrites.peek();
                if (pending == null) {
                    break;
                }
                if (out == null) {
                    out = ctx.alloc().buffer();
                }
                ByteBuf buf = (ByteBuf) pending.msg();
                SSLEngineResult result = wrap(engine, buf, out);

                if (!buf.isReadable()) {
                    buf.release();
                    promise = (ChannelPromise) pending.recycleAndGet();
                    pendingUnencryptedWrites.remove();
                } else {
                    promise = null;
                }

                if (result.getStatus() == Status.CLOSED) {
                    // SSLEngine has been closed already.
                    // Any further write attempts should be denied.
                    for (;;) {
                        PendingWrite w = pendingUnencryptedWrites.poll();
                        if (w == null) {
                            break;
                        }
                        w.failAndRecycle(SSLENGINE_CLOSED);
                    }
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
                        case NEED_WRAP:
                            if (promise != null) {
                                ctx.writeAndFlush(out, promise);
                                promise = null;
                            } else {
                                ctx.writeAndFlush(out);
                            }
                            out = ctx.alloc().buffer();
                            break;
                        case NEED_UNWRAP:
                            return;
                        default:
                            throw new IllegalStateException("Unknown handshake status: " + result.getHandshakeStatus());
                    }
                }
            }
        } catch (SSLException e) {
            setHandshakeFailure(e);
            throw e;
        } finally {
            if (out != null && out.isReadable()) {
                if (promise != null) {
                    ctx.writeAndFlush(out, promise);
                } else {
                    ctx.writeAndFlush(out);
                }
                out = null;
            } else if (promise != null) {
                promise.trySuccess();
            }
            if (out != null) {
                out.release();
            }
        }
    }

    private void flushNonAppData0(ChannelHandlerContext ctx, boolean inUnwrap) throws SSLException {
        ByteBuf out = null;
        try {
            for (;;) {
                if (out == null) {
                    out = ctx.alloc().buffer();
                }
                SSLEngineResult result = wrap(engine, Unpooled.EMPTY_BUFFER, out);

                if (result.bytesProduced() > 0) {
                    ctx.writeAndFlush(out);
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
                            unwrap(ctx);
                        }
                        break;
                    case NEED_WRAP:
                        break;
                    case NOT_HANDSHAKING:
                        // Workaround for TLS False Start problem reported at:
                        // https://github.com/netty/netty/issues/1108#issuecomment-14266970
                        if (!inUnwrap) {
                            unwrap(ctx);
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
        if (buffer.readableBytes() < 5) {
            throw new IllegalArgumentException("buffer must have at least 5 readable bytes");
        }
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
        int first = buffer.readerIndex();
        int packetLength = 0;

        // SSLv3 or TLS - Check ContentType
        boolean tls;
        switch (buffer.getUnsignedByte(first)) {
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
            int majorVersion = buffer.getUnsignedByte(first + 1);
            if (majorVersion == 3) {
                // SSLv3 or TLS
                packetLength = buffer.getUnsignedShort(first + 3) + 5;
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
            int headerLength = (buffer.getUnsignedByte(first) & 0x80) != 0 ? 2 : 3;
            int majorVersion = buffer.getUnsignedByte(first + headerLength + 1);
            if (majorVersion == 2 || majorVersion == 3) {
                // SSLv2
                if (headerLength == 2) {
                    packetLength = (buffer.getShort(first) & 0x7FFF) + 2;
                } else {
                    packetLength = (buffer.getShort(first) & 0x3FFF) + 3;
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
        int packetLength = this.packetLength;
        final int readableBytes = in.readableBytes();

        if (packetLength == 0) {
            // the previous packet was consumed so try to read the length of the next packet
            if (readableBytes < 5) {
                // not enough bytes readable to read the packet length
                return;
            }

            packetLength = getEncryptedPacketLength(in);
            if (packetLength == -1) {
                // Not an SSL/TLS packet
                NotSslRecordException e = new NotSslRecordException(
                        "not an SSL/TLS record: " + ByteBufUtil.hexDump(in));
                in.skipBytes(readableBytes);
                ctx.fireExceptionCaught(e);
                setHandshakeFailure(e);
                return;
            }

            assert packetLength > 0;
            this.packetLength = packetLength;
        }

        if (readableBytes < packetLength) {
            // wait until the whole packet can be read
            return;
        }

        // Slice out the whole packet so unwrap will only be called with complete packets.
        // Also directly reset the packetLength. This is needed as unwrap(..) may trigger
        // decode(...) again via:
        // 1) unwrap(..) is called
        // 2) wrap(...) is called from within unwrap(...)
        // 3) wrap(...) calls unwrapLater(...)
        // 4) unwrapLater(...) calls decode(...)
        //
        // See https://github.com/netty/netty/issues/1534
        int readerIndex = in.readerIndex();
        in.skipBytes(packetLength);
        ByteBuffer buffer = in.nioBuffer(readerIndex, packetLength);
        this.packetLength = 0;

        unwrap(ctx, buffer, out);
    }

    private void unwrap(ChannelHandlerContext ctx) throws SSLException {
        RecyclableArrayList out = RecyclableArrayList.newInstance();
        try {
            unwrap(ctx, Unpooled.EMPTY_BUFFER.nioBuffer(), out);
            final int size = out.size();
            for (int i = 0; i < size; i++) {
                ctx.fireChannelRead(out.get(i));
            }
        } finally {
            out.recycle();
        }
    }

    private void unwrap(ChannelHandlerContext ctx, ByteBuffer packet, List<Object> out) throws SSLException {
        boolean wrapLater = false;
        int totalProduced = 0;
        try {
            for (;;) {
                if (decodeOut == null) {
                    decodeOut = ctx.alloc().buffer();
                }

                final SSLEngineResult result = unwrap(engine, packet, decodeOut);
                final Status status = result.getStatus();
                final HandshakeStatus handshakeStatus = result.getHandshakeStatus();
                final int produced = result.bytesProduced();
                final int consumed = result.bytesConsumed();

                totalProduced += produced;
                if (status == Status.CLOSED) {
                    // notify about the CLOSED state of the SSLEngine. See #137
                    sslCloseFuture.trySuccess(ctx.channel());
                    break;
                }

                switch (handshakeStatus) {
                    case NEED_UNWRAP:
                        break;
                    case NEED_WRAP:
                        flushNonAppData0(ctx, true);
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
                        throw new IllegalStateException("Unknown handshake status: " + handshakeStatus);
                }

                if (status == Status.BUFFER_UNDERFLOW || consumed == 0 && produced == 0) {
                    break;
                }
            }

            if (wrapLater) {
                flush0(ctx);
            }
        } catch (SSLException e) {
            setHandshakeFailure(e);
            throw e;
        } finally {
            if (totalProduced > 0) {
                ByteBuf decodeOut = this.decodeOut;
                this.decodeOut = null;
                out.add(decodeOut);
            }
        }
    }

    private static SSLEngineResult unwrap(SSLEngine engine, ByteBuffer in, ByteBuf out) throws SSLException {
        for (;;) {
            ByteBuffer out0 = out.nioBuffer(out.writerIndex(), out.writableBytes());
            SSLEngineResult result = engine.unwrap(in, out0);
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
        if (handshakePromise.trySuccess(ctx.channel())) {
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
        for (;;) {
            PendingWrite write = pendingUnencryptedWrites.poll();
            if (write == null) {
                break;
            }
            write.failAndRecycle(cause);
        }
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

        if (ctx.channel().isActive()) {
            // channelActive() event has been fired already, which means this.channelActive() will
            // not be invoked. We have to initialize here instead.
            handshake0();
        } else {
            // channelActive() event has not been fired yet.  this.channelOpen() will be invoked
            // and initialization will occur there.
        }
    }

    private Future<Channel> handshake0() {
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
            flushNonAppData0(ctx, false);
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
            handshake0().addListener(new GenericFutureListener<Future<Channel>>() {
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
                if (ctx.channel().isActive()) {
                    ctx.close(promise);
                }
            }
        });
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
