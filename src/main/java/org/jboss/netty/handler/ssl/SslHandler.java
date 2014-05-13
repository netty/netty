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
package org.jboss.netty.handler.ssl;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelDownstreamHandler;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.DefaultChannelFuture;
import org.jboss.netty.channel.DownstreamMessageEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.frame.FrameDecoder;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;
import org.jboss.netty.util.internal.DetectionUtil;
import org.jboss.netty.util.internal.NonReentrantLock;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.regex.Pattern;

import static org.jboss.netty.channel.Channels.*;

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
 *
 * <h3>Handshake</h3>
 * <p>
 * If {@link #isIssueHandshake()} is {@code false}
 * (default) you will need to take care of calling {@link #handshake()} by your own. In most
 * situations were {@link SslHandler} is used in 'client mode' you want to issue a handshake once
 * the connection was established. if {@link #setIssueHandshake(boolean)} is set to {@code true}
 * you don't need to worry about this as the {@link SslHandler} will take care of it.
 * <p>
 *
 * <h3>Renegotiation</h3>
 * <p>
 * If {@link #isEnableRenegotiation() enableRenegotiation} is {@code true}
 * (default) and the initial handshake has been done successfully, you can call
 * {@link #handshake()} to trigger the renegotiation.
 * <p>
 * If {@link #isEnableRenegotiation() enableRenegotiation} is {@code false},
 * an attempt to trigger renegotiation will result in the connection closure.
 * <p>
 * Please note that TLS renegotiation had a security issue before.  If your
 * runtime environment did not fix it, please make sure to disable TLS
 * renegotiation by calling {@link #setEnableRenegotiation(boolean)} with
 * {@code false}.  For more information, please refer to the following documents:
 * <ul>
 *   <li><a href="http://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2009-3555">CVE-2009-3555</a></li>
 *   <li><a href="http://www.ietf.org/rfc/rfc5746.txt">RFC5746</a></li>
 *   <li><a href="http://www.oracle.com/technetwork/java/javase/documentation/tlsreadme2-176330.html">Phased
 *       Approach to Fixing the TLS Renegotiation Issue</a></li>
 * </ul>
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
 * @apiviz.landmark
 * @apiviz.uses org.jboss.netty.handler.ssl.SslBufferPool
 */
public class SslHandler extends FrameDecoder
                        implements ChannelDownstreamHandler {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SslHandler.class);

    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    private static final Pattern IGNORABLE_CLASS_IN_STACK = Pattern.compile(
            "^.*(?:Socket|Datagram|Sctp|Udt)Channel.*$");
    private static final Pattern IGNORABLE_ERROR_MESSAGE = Pattern.compile(
            "^.*(?:connection.*(?:reset|closed|abort|broken)|broken.*pipe).*$", Pattern.CASE_INSENSITIVE);

    private static SslBufferPool defaultBufferPool;

    /**
     * Returns the default {@link SslBufferPool} used when no pool is
     * specified in the constructor.
     */
    public static synchronized SslBufferPool getDefaultBufferPool() {
        if (defaultBufferPool == null) {
            defaultBufferPool = new SslBufferPool();
        }
        return defaultBufferPool;
    }

    private volatile ChannelHandlerContext ctx;
    private final SSLEngine engine;
    private final SslBufferPool bufferPool;
    private final Executor delegatedTaskExecutor;
    private final boolean startTls;

    private volatile boolean enableRenegotiation = true;

    final Object handshakeLock = new Object();
    private boolean handshaking;
    private volatile boolean handshaken;
    private volatile ChannelFuture handshakeFuture;

    @SuppressWarnings("UnusedDeclaration")
    private volatile int sentFirstMessage;
    @SuppressWarnings("UnusedDeclaration")
    private volatile int sentCloseNotify;
    @SuppressWarnings("UnusedDeclaration")
    private volatile int closedOutboundAndChannel;

    private static final AtomicIntegerFieldUpdater<SslHandler> SENT_FIRST_MESSAGE_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(SslHandler.class, "sentFirstMessage");
    private static final AtomicIntegerFieldUpdater<SslHandler> SENT_CLOSE_NOTIFY_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(SslHandler.class, "sentCloseNotify");
    private static final AtomicIntegerFieldUpdater<SslHandler> CLOSED_OUTBOUND_AND_CHANNEL_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(SslHandler.class, "closedOutboundAndChannel");

    int ignoreClosedChannelException;
    final Object ignoreClosedChannelExceptionLock = new Object();
    private final Queue<PendingWrite> pendingUnencryptedWrites = new LinkedList<PendingWrite>();
    private final NonReentrantLock pendingUnencryptedWritesLock = new NonReentrantLock();
    private final Queue<MessageEvent> pendingEncryptedWrites = new ConcurrentLinkedQueue<MessageEvent>();
    private final NonReentrantLock pendingEncryptedWritesLock = new NonReentrantLock();

    private volatile boolean issueHandshake;
    private volatile boolean writeBeforeHandshakeDone;
    private final SSLEngineInboundCloseFuture sslEngineCloseFuture = new SSLEngineInboundCloseFuture();

    private boolean closeOnSSLException;

    private int packetLength;

    private final Timer timer;
    private final long handshakeTimeoutInMillis;
    private Timeout handshakeTimeout;

    /**
     * Creates a new instance.
     *
     * @param engine  the {@link SSLEngine} this handler will use
     */
    public SslHandler(SSLEngine engine) {
        this(engine, getDefaultBufferPool(), false, null, 0);
    }

    /**
     * Creates a new instance.
     *
     * @param engine      the {@link SSLEngine} this handler will use
     * @param bufferPool  the {@link SslBufferPool} where this handler will
     *                    acquire the buffers required by the {@link SSLEngine}
     */
    public SslHandler(SSLEngine engine, SslBufferPool bufferPool) {
        this(engine, bufferPool, false, null, 0);
    }

    /**
     * Creates a new instance.
     *
     * @param engine    the {@link SSLEngine} this handler will use
     * @param startTls  {@code true} if the first write request shouldn't be
     *                  encrypted by the {@link SSLEngine}
     */
    public SslHandler(SSLEngine engine, boolean startTls) {
        this(engine, getDefaultBufferPool(), startTls);
    }

    /**
     * Creates a new instance.
     *
     * @param engine      the {@link SSLEngine} this handler will use
     * @param bufferPool  the {@link SslBufferPool} where this handler will
     *                    acquire the buffers required by the {@link SSLEngine}
     * @param startTls    {@code true} if the first write request shouldn't be
     *                    encrypted by the {@link SSLEngine}
     */
    public SslHandler(SSLEngine engine, SslBufferPool bufferPool, boolean startTls) {
        this(engine, bufferPool, startTls, null, 0);
    }

    /**
     * Creates a new instance.
     *
     * @param engine
     *        the {@link SSLEngine} this handler will use
     * @param bufferPool
     *        the {@link SslBufferPool} where this handler will acquire
     *        the buffers required by the {@link SSLEngine}
     * @param startTls
     *        {@code true} if the first write request shouldn't be encrypted
     *        by the {@link SSLEngine}
     * @param timer
     *        the {@link Timer} which will be used to process the timeout of the {@link #handshake()}.
     *        Be aware that the given {@link Timer} will not get stopped automaticly, so it is up to you to cleanup
     *        once you not need it anymore
     * @param handshakeTimeoutInMillis
     *        the time in milliseconds after whic the {@link #handshake()}  will be failed, and so the future notified
     */
    @SuppressWarnings("deprecation")
    public SslHandler(SSLEngine engine, SslBufferPool bufferPool, boolean startTls,
                      Timer timer, long handshakeTimeoutInMillis) {
        this(engine, bufferPool, startTls, ImmediateExecutor.INSTANCE, timer, handshakeTimeoutInMillis);
    }

    /**
     * @deprecated Use {@link #SslHandler(SSLEngine)} instead.
     */
    @Deprecated
    public SslHandler(SSLEngine engine, Executor delegatedTaskExecutor) {
        this(engine, getDefaultBufferPool(), delegatedTaskExecutor);
    }

    /**
     * @deprecated Use {@link #SslHandler(SSLEngine, boolean)} instead.
     */
    @Deprecated
    public SslHandler(SSLEngine engine, SslBufferPool bufferPool, Executor delegatedTaskExecutor) {
        this(engine, bufferPool, false, delegatedTaskExecutor);
    }

    /**
     * @deprecated  Use {@link #SslHandler(SSLEngine, boolean)} instead.
     */
    @Deprecated
    public SslHandler(SSLEngine engine, boolean startTls, Executor delegatedTaskExecutor) {
        this(engine, getDefaultBufferPool(), startTls, delegatedTaskExecutor);
    }

    /**
     * @deprecated Use {@link #SslHandler(SSLEngine, SslBufferPool, boolean)} instead.
     */
    @Deprecated
    public SslHandler(SSLEngine engine, SslBufferPool bufferPool, boolean startTls, Executor delegatedTaskExecutor) {
        this(engine, bufferPool, startTls, delegatedTaskExecutor, null, 0);
    }

    /**
     * @deprecated Use {@link #SslHandler(SSLEngine engine, SslBufferPool bufferPool, boolean startTls, Timer timer,
     *             long handshakeTimeoutInMillis)} instead.
     */
    @Deprecated
    public SslHandler(SSLEngine engine, SslBufferPool bufferPool, boolean startTls, Executor delegatedTaskExecutor,
                      Timer timer, long handshakeTimeoutInMillis) {
        if (engine == null) {
            throw new NullPointerException("engine");
        }
        if (bufferPool == null) {
            throw new NullPointerException("bufferPool");
        }
        if (delegatedTaskExecutor == null) {
            throw new NullPointerException("delegatedTaskExecutor");
        }
        if (timer == null && handshakeTimeoutInMillis > 0) {
            throw new IllegalArgumentException("No Timer was given but a handshakeTimeoutInMillis, need both or none");
        }

        this.engine = engine;
        this.bufferPool = bufferPool;
        this.delegatedTaskExecutor = delegatedTaskExecutor;
        this.startTls = startTls;
        this.timer = timer;
        this.handshakeTimeoutInMillis = handshakeTimeoutInMillis;
    }

    /**
     * Returns the {@link SSLEngine} which is used by this handler.
     */
    public SSLEngine getEngine() {
        return engine;
    }

    /**
     * Starts an SSL / TLS handshake for the specified channel.
     *
     * @return a {@link ChannelFuture} which is notified when the handshake
     *         succeeds or fails.
     */
    public ChannelFuture handshake() {
        synchronized (handshakeLock) {
            if (handshaken && !isEnableRenegotiation()) {
                throw new IllegalStateException("renegotiation disabled");
            }

            final ChannelHandlerContext ctx = this.ctx;
            final Channel channel = ctx.getChannel();
            ChannelFuture handshakeFuture;
            Exception exception = null;

            if (handshaking) {
                return this.handshakeFuture;
            }

            handshaking = true;
            try {
                engine.beginHandshake();
                runDelegatedTasks();
                handshakeFuture = this.handshakeFuture = future(channel);
                if (handshakeTimeoutInMillis > 0) {
                    handshakeTimeout = timer.newTimeout(new TimerTask() {
                            public void run(Timeout timeout) throws Exception {
                            ChannelFuture future = SslHandler.this.handshakeFuture;
                            if (future != null && future.isDone()) {
                                return;
                            }

                            setHandshakeFailure(channel, new SSLException("Handshake did not complete within " +
                                            handshakeTimeoutInMillis + "ms"));
                        }
                        }, handshakeTimeoutInMillis, TimeUnit.MILLISECONDS);
                }
            } catch (Exception e) {
                handshakeFuture = this.handshakeFuture = failedFuture(channel, e);
                exception = e;
            }

            if (exception == null) { // Began handshake successfully.
                try {
                    final ChannelFuture hsFuture = handshakeFuture;
                    wrapNonAppData(ctx, channel).addListener(new ChannelFutureListener() {
                        public void operationComplete(ChannelFuture future) throws Exception {
                            if (!future.isSuccess()) {
                                Throwable cause = future.getCause();
                                hsFuture.setFailure(cause);

                                fireExceptionCaught(ctx, cause);
                                if (closeOnSSLException) {
                                    Channels.close(ctx, future(channel));
                                }
                            }
                        }
                    });
                } catch (SSLException e) {
                    handshakeFuture.setFailure(e);

                    fireExceptionCaught(ctx, e);
                    if (closeOnSSLException) {
                        Channels.close(ctx, future(channel));
                    }
                }
            } else { // Failed to initiate handshake.
                fireExceptionCaught(ctx, exception);
                if (closeOnSSLException) {
                    Channels.close(ctx, future(channel));
                }
            }
            return handshakeFuture;
        }
    }

    /**
     * @deprecated Use {@link #handshake()} instead.
     */
    @Deprecated
    public ChannelFuture handshake(@SuppressWarnings("unused") Channel channel) {
        return handshake();
    }

    /**
     * Sends an SSL {@code close_notify} message to the specified channel and
     * destroys the underlying {@link SSLEngine}.
     */
    public ChannelFuture close() {
        ChannelHandlerContext ctx = this.ctx;
        Channel channel = ctx.getChannel();
        try {
            engine.closeOutbound();
            return wrapNonAppData(ctx, channel);
        } catch (SSLException e) {
            fireExceptionCaught(ctx, e);
            if (closeOnSSLException) {
                Channels.close(ctx, future(channel));
            }
            return failedFuture(channel, e);
        }
    }

    /**
     * @deprecated Use {@link #close()} instead.
     */
    @Deprecated
    public ChannelFuture close(@SuppressWarnings("unused") Channel channel) {
        return close();
    }

    /**
     * Returns {@code true} if and only if TLS renegotiation is enabled.
     */
    public boolean isEnableRenegotiation() {
        return enableRenegotiation;
    }

    /**
     * Enables or disables TLS renegotiation.
     */
    public void setEnableRenegotiation(boolean enableRenegotiation) {
        this.enableRenegotiation = enableRenegotiation;
    }

    /**
     * Enables or disables the automatic handshake once the {@link Channel} is
     * connected. The value will only have affect if its set before the
     * {@link Channel} is connected.
     */
    public void setIssueHandshake(boolean issueHandshake) {
        this.issueHandshake = issueHandshake;
    }

    /**
     * Returns {@code true} if the automatic handshake is enabled
     */
    public boolean isIssueHandshake() {
        return issueHandshake;
    }

    /**
     * Return the {@link ChannelFuture} that will get notified if the inbound of the {@link SSLEngine} will get closed.
     *
     * This method will return the same {@link ChannelFuture} all the time.
     *
     * For more informations see the apidocs of {@link SSLEngine}
     *
     */
    public ChannelFuture getSSLEngineInboundCloseFuture() {
        return sslEngineCloseFuture;
    }

    /**
     * Return the timeout (in ms) after which the {@link ChannelFuture} of {@link #handshake()} will be failed, while
     * a handshake is in progress
     */
    public long getHandshakeTimeout() {
        return handshakeTimeoutInMillis;
    }

    /**
     * If set to {@code true}, the {@link Channel} will automatically get closed
     * one a {@link SSLException} was caught. This is most times what you want, as after this
     * its almost impossible to recover.
     *
     * Anyway the default is {@code false} to not break compatibility with older releases. This
     * will be changed to {@code true} in the next major release.
     *
     */
    public void setCloseOnSSLException(boolean closeOnSslException) {
        if (ctx != null) {
            throw new IllegalStateException("Can only get changed before attached to ChannelPipeline");
        }
        closeOnSSLException = closeOnSslException;
    }

    public boolean getCloseOnSSLException() {
        return closeOnSSLException;
    }

    public void handleDownstream(
            final ChannelHandlerContext context, final ChannelEvent evt) throws Exception {
        if (evt instanceof ChannelStateEvent) {
            ChannelStateEvent e = (ChannelStateEvent) evt;
            switch (e.getState()) {
            case OPEN:
            case CONNECTED:
            case BOUND:
                if (Boolean.FALSE.equals(e.getValue()) || e.getValue() == null) {
                    closeOutboundAndChannel(context, e);
                    return;
                }
            }
        }
        if (!(evt instanceof MessageEvent)) {
            context.sendDownstream(evt);
            return;
        }

        MessageEvent e = (MessageEvent) evt;
        if (!(e.getMessage() instanceof ChannelBuffer)) {
            context.sendDownstream(evt);
            return;
        }

        // Do not encrypt the first write request if this handler is
        // created with startTLS flag turned on.
        if (startTls && SENT_FIRST_MESSAGE_UPDATER.compareAndSet(this, 0, 1)) {
            context.sendDownstream(evt);
            return;
        }

        // Otherwise, all messages are encrypted.
        ChannelBuffer msg = (ChannelBuffer) e.getMessage();
        PendingWrite pendingWrite;

        if (msg.readable()) {
            pendingWrite = new PendingWrite(evt.getFuture(), msg.toByteBuffer(msg.readerIndex(), msg.readableBytes()));
        } else {
            pendingWrite = new PendingWrite(evt.getFuture(), null);
        }

        pendingUnencryptedWritesLock.lock();
        try {
            pendingUnencryptedWrites.add(pendingWrite);
        } finally {
            pendingUnencryptedWritesLock.unlock();
        }

        if (handshakeFuture == null || !handshakeFuture.isDone()) {
            writeBeforeHandshakeDone = true;
        }
        wrap(context, evt.getChannel());
    }

    private void cancelHandshakeTimeout() {
        if (handshakeTimeout != null) {
            // cancel the task as we will fail the handshake future now
            handshakeTimeout.cancel();
        }
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {

        // Make sure the handshake future is notified when a connection has
        // been closed during handshake.
        synchronized (handshakeLock) {
            if (handshaking) {
                cancelHandshakeTimeout();
                handshakeFuture.setFailure(new ClosedChannelException());
            }
        }

        try {
            super.channelDisconnected(ctx, e);
        } finally {
            unwrapNonAppData(ctx, e.getChannel());
            closeEngine();
        }
    }

    private void closeEngine() {
        engine.closeOutbound();
        if (sentCloseNotify == 0 && handshaken) {
            try {
                engine.closeInbound();
            } catch (SSLException ex) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Failed to clean up SSLEngine.", ex);
                }
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception {

        Throwable cause = e.getCause();
        if (cause instanceof IOException) {
            if (cause instanceof ClosedChannelException) {
                synchronized (ignoreClosedChannelExceptionLock) {
                    if (ignoreClosedChannelException > 0) {
                        ignoreClosedChannelException --;
                        if (logger.isDebugEnabled()) {
                            logger.debug(
                                    "Swallowing an exception raised while " +
                                    "writing non-app data", cause);
                        }

                        return;
                    }
                }
            } else {
                if (ignoreException(cause)) {
                    return;
                }
            }
        }

        ctx.sendUpstream(e);
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
        if (!(t instanceof SSLException) && t instanceof IOException && engine.isOutboundDone()) {
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
                if (classname.startsWith("org.jboss.netty.")) {
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
                    if (DetectionUtil.javaVersion() >= 7
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
     * Returns {@code true} if the given {@link ChannelBuffer} is encrypted. Be aware that this method
     * will not increase the readerIndex of the given {@link ChannelBuffer}.
     *
     * @param   buffer
     *                  The {@link ChannelBuffer} to read from. Be aware that it must have at least 5 bytes to read,
     *                  otherwise it will throw an {@link IllegalArgumentException}.
     * @return encrypted
     *                  {@code true} if the {@link ChannelBuffer} is encrypted, {@code false} otherwise.
     * @throws IllegalArgumentException
     *                  Is thrown if the given {@link ChannelBuffer} has not at least 5 bytes to read.
     */
    public static boolean isEncrypted(ChannelBuffer buffer) {
        return getEncryptedPacketLength(buffer, buffer.readerIndex()) != -1;
    }

    /**
     * Return how much bytes can be read out of the encrypted data. Be aware that this method will not increase
     * the readerIndex of the given {@link ChannelBuffer}.
     *
     * @param   buffer
     *                  The {@link ChannelBuffer} to read from. Be aware that it must have at least 5 bytes to read,
     *                  otherwise it will throw an {@link IllegalArgumentException}.
     * @return length
     *                  The length of the encrypted packet that is included in the buffer. This will
     *                  return {@code -1} if the given {@link ChannelBuffer} is not encrypted at all.
     * @throws IllegalArgumentException
     *                  Is thrown if the given {@link ChannelBuffer} has not at least 5 bytes to read.
     */
    private static int getEncryptedPacketLength(ChannelBuffer buffer, int offset) {
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
                packetLength = (getShort(buffer, offset + 3) & 0xFFFF) + 5;
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
                    packetLength = (getShort(buffer, offset) & 0x7FFF) + 2;
                } else {
                    packetLength = (getShort(buffer, offset) & 0x3FFF) + 3;
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
    protected Object decode(
            final ChannelHandlerContext ctx, Channel channel, ChannelBuffer in) throws Exception {

        final int startOffset = in.readerIndex();
        final int endOffset = in.writerIndex();
        int offset = startOffset;
        int totalLength = 0;

        // If we calculated the length of the current SSL record before, use that information.
        if (packetLength > 0) {
            if (endOffset - startOffset < packetLength) {
                return null;
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

        ChannelBuffer unwrapped = null;
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

            final ByteBuffer inNetBuf = in.toByteBuffer(in.readerIndex(), totalLength);
            unwrapped = unwrap(ctx, channel, in, inNetBuf, totalLength);
            assert !inNetBuf.hasRemaining() || engine.isInboundDone();
        }

        if (nonSslRecord) {
            // Not an SSL/TLS packet
            NotSslRecordException e = new NotSslRecordException(
                    "not an SSL/TLS record: " + ChannelBuffers.hexDump(in));
            in.skipBytes(in.readableBytes());
            if (closeOnSSLException) {
                // first trigger the exception and then close the channel
                fireExceptionCaught(ctx, e);
                Channels.close(ctx, future(channel));

                // just return null as we closed the channel before, that
                // will take care of cleanup etc
                return null;
            } else {
                throw e;
            }
        }

        return unwrapped;
    }

    /**
     * Reads a big-endian short integer from the buffer.  Please note that we do not use
     * {@link ChannelBuffer#getShort(int)} because it might be a little-endian buffer.
     */
    private static short getShort(ChannelBuffer buf, int offset) {
        return (short) (buf.getByte(offset) << 8 | buf.getByte(offset + 1) & 0xFF);
    }

    private void wrap(ChannelHandlerContext context, Channel channel) throws SSLException {
        ChannelBuffer msg;
        ByteBuffer outNetBuf = bufferPool.acquireBuffer();
        boolean success = true;
        boolean offered = false;
        boolean needsUnwrap = false;
        PendingWrite pendingWrite = null;

        try {
            loop:
            for (;;) {
                // Acquire a lock to make sure unencrypted data is polled
                // in order and their encrypted counterpart is offered in
                // order.
                pendingUnencryptedWritesLock.lock();
                try {
                    pendingWrite = pendingUnencryptedWrites.peek();
                    if (pendingWrite == null) {
                        break;
                    }

                    ByteBuffer outAppBuf = pendingWrite.outAppBuf;
                    if (outAppBuf == null) {
                        // A write request with an empty buffer
                        pendingUnencryptedWrites.remove();
                        offerEncryptedWriteRequest(
                                new DownstreamMessageEvent(
                                        channel, pendingWrite.future,
                                        ChannelBuffers.EMPTY_BUFFER,
                                        channel.getRemoteAddress()));
                        offered = true;
                    } else {
                        synchronized (handshakeLock) {
                            SSLEngineResult result = null;
                            try {
                                result = engine.wrap(outAppBuf, outNetBuf);
                            } finally {
                                if (!outAppBuf.hasRemaining()) {
                                    pendingUnencryptedWrites.remove();
                                }
                            }

                            if (result.bytesProduced() > 0) {
                                outNetBuf.flip();
                                int remaining = outNetBuf.remaining();
                                msg = ctx.getChannel().getConfig().getBufferFactory().getBuffer(remaining);

                                // Transfer the bytes to the new ChannelBuffer using some safe method that will also
                                // work with "non" heap buffers
                                //
                                // See https://github.com/netty/netty/issues/329
                                msg.writeBytes(outNetBuf);
                                outNetBuf.clear();

                                ChannelFuture future;
                                if (pendingWrite.outAppBuf.hasRemaining()) {
                                    // pendingWrite's future shouldn't be notified if
                                    // only partial data is written.
                                    future = succeededFuture(channel);
                                } else {
                                    future = pendingWrite.future;
                                }

                                MessageEvent encryptedWrite = new DownstreamMessageEvent(
                                        channel, future, msg, channel.getRemoteAddress());
                                offerEncryptedWriteRequest(encryptedWrite);
                                offered = true;
                            } else if (result.getStatus() == Status.CLOSED) {
                                // SSLEngine has been closed already.
                                // Any further write attempts should be denied.
                                success = false;
                                break;
                            } else {
                                final HandshakeStatus handshakeStatus = result.getHandshakeStatus();
                                handleRenegotiation(handshakeStatus);
                                switch (handshakeStatus) {
                                case NEED_WRAP:
                                    if (outAppBuf.hasRemaining()) {
                                        break;
                                    } else {
                                        break loop;
                                    }
                                case NEED_UNWRAP:
                                    needsUnwrap = true;
                                    break loop;
                                case NEED_TASK:
                                    runDelegatedTasks();
                                    break;
                                case FINISHED:
                                    setHandshakeSuccess(channel);
                                    if (result.getStatus() == Status.CLOSED) {
                                        success = false;
                                    }
                                    break loop;
                                case NOT_HANDSHAKING:
                                    setHandshakeSuccessIfStillHandshaking(channel);
                                    if (result.getStatus() == Status.CLOSED) {
                                        success = false;
                                    }
                                    break loop;
                                default:
                                    throw new IllegalStateException(
                                            "Unknown handshake status: " +
                                            handshakeStatus);
                                }
                            }
                        }
                    }
                } finally {
                    pendingUnencryptedWritesLock.unlock();
                }
            }
        } catch (SSLException e) {
            success = false;
            setHandshakeFailure(channel, e);
            throw e;
        } finally {
            bufferPool.releaseBuffer(outNetBuf);

            if (offered) {
                flushPendingEncryptedWrites(context);
            }

            if (!success) {
                IllegalStateException cause =
                    new IllegalStateException("SSLEngine already closed");

                // Check if we had a pendingWrite in process, if so we need to also notify as otherwise
                // the ChannelFuture will never get notified
                if (pendingWrite != null) {
                    pendingWrite.future.setFailure(cause);
                }

                // Mark all remaining pending writes as failure if anything
                // wrong happened before the write requests are wrapped.
                // Please note that we do not call setFailure while a lock is
                // acquired, to avoid a potential dead lock.
                for (;;) {
                    pendingUnencryptedWritesLock.lock();
                    try {
                        pendingWrite = pendingUnencryptedWrites.poll();
                        if (pendingWrite == null) {
                            break;
                        }
                    } finally {
                        pendingUnencryptedWritesLock.unlock();
                    }

                    pendingWrite.future.setFailure(cause);
                }
            }
        }

        if (needsUnwrap) {
            unwrapNonAppData(ctx, channel);
        }
    }

    private void offerEncryptedWriteRequest(MessageEvent encryptedWrite) {
        final boolean locked = pendingEncryptedWritesLock.tryLock();
        try {
            pendingEncryptedWrites.add(encryptedWrite);
        } finally {
            if (locked) {
                pendingEncryptedWritesLock.unlock();
            }
        }
    }

    private void flushPendingEncryptedWrites(ChannelHandlerContext ctx) {
        while (!pendingEncryptedWrites.isEmpty()) {
            // Avoid possible dead lock and data integrity issue
            // which is caused by cross communication between more than one channel
            // in the same VM.
            if (!pendingEncryptedWritesLock.tryLock()) {
                return;
            }

            try {
                MessageEvent e;
                while ((e = pendingEncryptedWrites.poll()) != null) {
                    ctx.sendDownstream(e);
                }
            } finally {
                pendingEncryptedWritesLock.unlock();
            }

            // Other thread might have added more elements at this point, so we loop again if the queue got unempty.
        }
    }

    private ChannelFuture wrapNonAppData(ChannelHandlerContext ctx, Channel channel) throws SSLException {
        ChannelFuture future = null;
        ByteBuffer outNetBuf = bufferPool.acquireBuffer();

        SSLEngineResult result;
        try {
            for (;;) {
                synchronized (handshakeLock) {
                    result = engine.wrap(EMPTY_BUFFER, outNetBuf);
                }

                if (result.bytesProduced() > 0) {
                    outNetBuf.flip();
                    ChannelBuffer msg =
                            ctx.getChannel().getConfig().getBufferFactory().getBuffer(outNetBuf.remaining());

                    // Transfer the bytes to the new ChannelBuffer using some safe method that will also
                    // work with "non" heap buffers
                    //
                    // See https://github.com/netty/netty/issues/329
                    msg.writeBytes(outNetBuf);
                    outNetBuf.clear();

                    future = future(channel);
                    future.addListener(new ChannelFutureListener() {
                        public void operationComplete(ChannelFuture future)
                                throws Exception {
                            if (future.getCause() instanceof ClosedChannelException) {
                                synchronized (ignoreClosedChannelExceptionLock) {
                                    ignoreClosedChannelException ++;
                                }
                            }
                        }
                    });

                    write(ctx, future, msg);
                }

                final HandshakeStatus handshakeStatus = result.getHandshakeStatus();
                handleRenegotiation(handshakeStatus);
                switch (handshakeStatus) {
                case FINISHED:
                    setHandshakeSuccess(channel);
                    runDelegatedTasks();
                    break;
                case NEED_TASK:
                    runDelegatedTasks();
                    break;
                case NEED_UNWRAP:
                    if (!Thread.holdsLock(handshakeLock)) {
                        // unwrap shouldn't be called when this method was
                        // called by unwrap - unwrap will keep running after
                        // this method returns.
                        unwrapNonAppData(ctx, channel);
                    }
                    break;
                case NOT_HANDSHAKING:
                    if (setHandshakeSuccessIfStillHandshaking(channel)) {
                        runDelegatedTasks();
                    }
                    break;
                case NEED_WRAP:
                    break;
                default:
                    throw new IllegalStateException(
                            "Unexpected handshake status: " + handshakeStatus);
                }

                if (result.bytesProduced() == 0) {
                    break;
                }
            }
        } catch (SSLException e) {
            setHandshakeFailure(channel, e);
            throw e;
        } finally {
            bufferPool.releaseBuffer(outNetBuf);
        }

        if (future == null) {
            future = succeededFuture(channel);
        }

        return future;
    }

    /**
     * Calls {@link SSLEngine#unwrap(ByteBuffer, ByteBuffer)} with an empty buffer to handle handshakes, etc.
     */
    private void unwrapNonAppData(ChannelHandlerContext ctx, Channel channel) throws SSLException {
        unwrap(ctx, channel, ChannelBuffers.EMPTY_BUFFER, EMPTY_BUFFER, -1);
    }

    /**
     * Unwraps inbound SSL records.
     */
    private ChannelBuffer unwrap(
            ChannelHandlerContext ctx, Channel channel,
            ChannelBuffer nettyInNetBuf, ByteBuffer nioInNetBuf,
            int initialNettyOutAppBufCapacity) throws SSLException {

        final ByteBuffer nioOutAppBuf = bufferPool.acquireBuffer();
        final int nettyInNetBufStartOffset = nettyInNetBuf.readerIndex();
        final int nioInNetBufStartOffset = nioInNetBuf.position();

        ChannelBuffer nettyOutAppBuf = null;

        try {
            boolean needsWrap = false;
            for (;;) {
                SSLEngineResult result;
                boolean needsHandshake = false;
                synchronized (handshakeLock) {
                    if (!handshaken && !handshaking &&
                        !engine.getUseClientMode() &&
                        !engine.isInboundDone() && !engine.isOutboundDone()) {
                        needsHandshake = true;
                    }
                }

                if (needsHandshake) {
                    handshake();
                }

                synchronized (handshakeLock) {
                    // Decrypt at least one record in the inbound network buffer.
                    // It is impossible to consume no record here because we made sure the inbound network buffer
                    // always contain at least one record in decode().  Therefore, if SSLEngine.unwrap() returns
                    // BUFFER_OVERFLOW, it is always resolved by retrying after emptying the application buffer.
                    for (;;) {
                        try {
                            result = engine.unwrap(nioInNetBuf, nioOutAppBuf);
                            switch (result.getStatus()) {
                                case CLOSED:
                                    // notify about the CLOSED state of the SSLEngine. See #137
                                    sslEngineCloseFuture.setClosed();
                                    break;
                                case BUFFER_OVERFLOW:
                                    // Flush the unwrapped data in the outAppBuf into frame and try again.
                                    // See the finally block.
                                    continue;
                            }

                            break;
                        } finally {
                            nioOutAppBuf.flip();

                            // Sync the offset of the inbound buffer.
                            nettyInNetBuf.readerIndex(
                                    nettyInNetBufStartOffset + nioInNetBuf.position() - nioInNetBufStartOffset);

                            // Copy the unwrapped data into a smaller buffer.
                            if (nioOutAppBuf.hasRemaining()) {
                                if (nettyOutAppBuf == null) {
                                    ChannelBufferFactory factory = ctx.getChannel().getConfig().getBufferFactory();
                                    nettyOutAppBuf = factory.getBuffer(initialNettyOutAppBufCapacity);
                                }
                                nettyOutAppBuf.writeBytes(nioOutAppBuf);
                            }
                            nioOutAppBuf.clear();
                        }
                    }

                    final HandshakeStatus handshakeStatus = result.getHandshakeStatus();
                    handleRenegotiation(handshakeStatus);
                    switch (handshakeStatus) {
                    case NEED_UNWRAP:
                        break;
                    case NEED_WRAP:
                        wrapNonAppData(ctx, channel);
                        break;
                    case NEED_TASK:
                        runDelegatedTasks();
                        break;
                    case FINISHED:
                        setHandshakeSuccess(channel);
                        needsWrap = true;
                        continue;
                    case NOT_HANDSHAKING:
                        if (setHandshakeSuccessIfStillHandshaking(channel)) {
                            needsWrap = true;
                            continue;
                        }
                        if (writeBeforeHandshakeDone) {
                            // We need to call wrap(...) in case there was a flush done before the handshake completed.
                            //
                            // See https://github.com/netty/netty/pull/2437
                            writeBeforeHandshakeDone = false;
                            needsWrap = true;
                        }
                        break;
                    default:
                        throw new IllegalStateException(
                                "Unknown handshake status: " + handshakeStatus);
                    }

                    if (result.getStatus() == Status.BUFFER_UNDERFLOW ||
                        result.bytesConsumed() == 0 && result.bytesProduced() == 0) {
                        break;
                    }
                }
            }

            if (needsWrap) {
                // wrap() acquires pendingUnencryptedWrites first and then
                // handshakeLock.  If handshakeLock is already hold by the
                // current thread, calling wrap() will lead to a dead lock
                // i.e. pendingUnencryptedWrites -> handshakeLock vs.
                //      handshakeLock -> pendingUnencryptedLock -> handshakeLock
                //
                // There is also the same issue between pendingEncryptedWrites
                // and pendingUnencryptedWrites.
                if (!Thread.holdsLock(handshakeLock) && !pendingEncryptedWritesLock.isHeldByCurrentThread()) {
                    wrap(ctx, channel);
                }
            }
        } catch (SSLException e) {
            setHandshakeFailure(channel, e);
            throw e;
        } finally {
            bufferPool.releaseBuffer(nioOutAppBuf);
        }

        if (nettyOutAppBuf != null && nettyOutAppBuf.readable()) {
            return nettyOutAppBuf;
        } else {
            return null;
        }
    }

    private void handleRenegotiation(HandshakeStatus handshakeStatus) {
        synchronized (handshakeLock) {
            if (handshakeStatus == HandshakeStatus.NOT_HANDSHAKING ||
                handshakeStatus == HandshakeStatus.FINISHED) {
                // Not handshaking
                return;
            }

            if (!handshaken) {
                // Not renegotiation
                return;
            }

            final boolean renegotiate;
            if (handshaking) {
                // Renegotiation in progress or failed already.
                // i.e. Renegotiation check has been done already below.
                return;
            }

            if (engine.isInboundDone() || engine.isOutboundDone()) {
                // Not handshaking but closing.
                return;
            }

            if (isEnableRenegotiation()) {
                // Continue renegotiation.
                renegotiate = true;
            } else {
                // Do not renegotiate.
                renegotiate = false;
                // Prevent reentrance of this method.
                handshaking = true;
            }

            if (renegotiate) {
                // Renegotiate.
                handshake();
            } else {
                // Raise an exception.
                fireExceptionCaught(
                        ctx, new SSLException(
                                "renegotiation attempted by peer; " +
                                "closing the connection"));

                // Close the connection to stop renegotiation.
                Channels.close(ctx, succeededFuture(ctx.getChannel()));
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
                final Runnable task;
                synchronized (handshakeLock) {
                    task = engine.getDelegatedTask();
                }

                if (task == null) {
                    break;
                }

                delegatedTaskExecutor.execute(task);
            }
        } else {
            final List<Runnable> tasks = new ArrayList<Runnable>(2);
            for (;;) {
                final Runnable task;
                synchronized (handshakeLock) {
                    task = engine.getDelegatedTask();
                }

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
                public void run() {
                    try {
                        for (Runnable task: tasks) {
                            task.run();
                        }
                    } catch (Exception e) {
                        fireExceptionCaught(ctx, e);
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
    private boolean setHandshakeSuccessIfStillHandshaking(Channel channel) {
        if (handshaking && !handshakeFuture.isDone()) {
            setHandshakeSuccess(channel);
            return true;
        }
        return false;
    }

    private void setHandshakeSuccess(Channel channel) {
        synchronized (handshakeLock) {
            handshaking = false;
            handshaken = true;

            if (handshakeFuture == null) {
                handshakeFuture = future(channel);
            }
            cancelHandshakeTimeout();
        }

        handshakeFuture.setSuccess();
    }

    private void setHandshakeFailure(Channel channel, SSLException cause) {
        synchronized (handshakeLock) {
            if (!handshaking) {
                return;
            }
            handshaking = false;
            handshaken = false;

            if (handshakeFuture == null) {
                handshakeFuture = future(channel);
            }

            // cancel the timeout now
            cancelHandshakeTimeout();

            // Release all resources such as internal buffers that SSLEngine
            // is managing.

            engine.closeOutbound();

            try {
                engine.closeInbound();
            } catch (SSLException e) {
                if (logger.isDebugEnabled()) {
                    logger.debug(
                            "SSLEngine.closeInbound() raised an exception after " +
                            "a handshake failure.", e);
                }
            }
        }

        handshakeFuture.setFailure(cause);
        if (closeOnSSLException) {
            Channels.close(ctx, future(channel));
        }
    }

    private void closeOutboundAndChannel(
            final ChannelHandlerContext context, final ChannelStateEvent e) {
        if (!e.getChannel().isConnected()) {
            context.sendDownstream(e);
            return;
        }

        // Ensure that the tear-down logic beyond this point is never invoked concurrently nor multiple times.
        if (!CLOSED_OUTBOUND_AND_CHANNEL_UPDATER.compareAndSet(this, 0, 1)) {
            // The other thread called this method already, and thus the connection will be closed eventually.
            // So, just wait until the connection is closed, and then forward the event so that the sink handles
            // the duplicate close attempt.
            e.getChannel().getCloseFuture().addListener(new ChannelFutureListener() {
                public void operationComplete(ChannelFuture future) throws Exception {
                    context.sendDownstream(e);
                }
            });
            return;
        }

        boolean passthrough = true;
        try {
            try {
                unwrapNonAppData(ctx, e.getChannel());
            } catch (SSLException ex) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Failed to unwrap before sending a close_notify message", ex);
                }
            }

            if (!engine.isOutboundDone()) {
                if (SENT_CLOSE_NOTIFY_UPDATER.compareAndSet(this, 0, 1)) {
                    engine.closeOutbound();
                    try {
                        ChannelFuture closeNotifyFuture = wrapNonAppData(context, e.getChannel());
                        closeNotifyFuture.addListener(
                                new ClosingChannelFutureListener(context, e));
                        passthrough = false;
                    } catch (SSLException ex) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Failed to encode a close_notify message", ex);
                        }
                    }
                }
            }
        } finally {
            if (passthrough) {
                context.sendDownstream(e);
            }
        }
    }

    private static final class PendingWrite {
        final ChannelFuture future;
        final ByteBuffer outAppBuf;

        PendingWrite(ChannelFuture future, ByteBuffer outAppBuf) {
            this.future = future;
            this.outAppBuf = outAppBuf;
        }
    }

    private static final class ClosingChannelFutureListener implements ChannelFutureListener {

        private final ChannelHandlerContext context;
        private final ChannelStateEvent e;

        ClosingChannelFutureListener(
                ChannelHandlerContext context, ChannelStateEvent e) {
            this.context = context;
            this.e = e;
        }

        public void operationComplete(ChannelFuture closeNotifyFuture) throws Exception {
            if (!(closeNotifyFuture.getCause() instanceof ClosedChannelException)) {
                Channels.close(context, e.getFuture());
            } else {
                e.getFuture().setSuccess();
            }
        }
    }

    @Override
    public void beforeAdd(ChannelHandlerContext ctx) throws Exception {
        super.beforeAdd(ctx);
        this.ctx = ctx;
    }

    /**
     * Fail all pending writes which we were not able to flush out
     */
    @Override
    public void afterRemove(ChannelHandlerContext ctx) throws Exception {
        closeEngine();

        // there is no need for synchronization here as we do not receive downstream events anymore
        Throwable cause = null;
        for (;;) {
            PendingWrite pw = pendingUnencryptedWrites.poll();
            if (pw == null) {
                break;
            }
            if (cause == null) {
                cause = new IOException("Unable to write data");
            }
            pw.future.setFailure(cause);
        }

        for (;;) {
            MessageEvent ev = pendingEncryptedWrites.poll();
            if (ev == null) {
                break;
            }
            if (cause == null) {
                cause = new IOException("Unable to write data");
            }
            ev.getFuture().setFailure(cause);
        }

        if (cause != null) {
            fireExceptionCaughtLater(ctx, cause);
        }
    }

    /**
     * Calls {@link #handshake()} once the {@link Channel} is connected
     */
    @Override
    public void channelConnected(final ChannelHandlerContext ctx, final ChannelStateEvent e) throws Exception {
        if (issueHandshake) {
            // issue and handshake and add a listener to it which will fire an exception event if
            // an exception was thrown while doing the handshake
            handshake().addListener(new ChannelFutureListener() {

                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        // Send the event upstream after the handshake was completed without an error.
                        //
                        // See https://github.com/netty/netty/issues/358
                        ctx.sendUpstream(e);
                    }
                }
            });
        } else {
            super.channelConnected(ctx, e);
        }
    }

    /**
     * Loop over all the pending writes and fail them.
     *
     * See <a href="https://github.com/netty/netty/issues/305">#305</a> for more details.
     */
    @Override
    public void channelClosed(final ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        // Move the fail of the writes to the IO-Thread to prevent possible deadlock
        // See https://github.com/netty/netty/issues/989
        ctx.getPipeline().execute(new Runnable() {
            public void run() {
                if (!pendingUnencryptedWritesLock.tryLock()) {
                    return;
                }

                Throwable cause = null;
                try {
                    for (;;) {
                        PendingWrite pw = pendingUnencryptedWrites.poll();
                        if (pw == null) {
                            break;
                        }
                        if (cause == null) {
                            cause = new ClosedChannelException();
                        }
                        pw.future.setFailure(cause);
                    }

                    for (;;) {
                        MessageEvent ev = pendingEncryptedWrites.poll();
                        if (ev == null) {
                            break;
                        }
                        if (cause == null) {
                            cause = new ClosedChannelException();
                        }
                        ev.getFuture().setFailure(cause);
                    }
                } finally {
                    pendingUnencryptedWritesLock.unlock();
                }

                if (cause != null) {
                    fireExceptionCaught(ctx, cause);
                }
            }
        });

        super.channelClosed(ctx, e);
    }

    private final class SSLEngineInboundCloseFuture extends DefaultChannelFuture {
        SSLEngineInboundCloseFuture() {
            super(null, true);
        }

        void setClosed() {
            super.setSuccess();
        }

        @Override
        public Channel getChannel() {
            if (ctx == null) {
                // Maybe we should better throw an IllegalStateException() ?
                return null;
            } else {
                return ctx.getChannel();
            }
        }

        @Override
        public boolean setSuccess() {
            return false;
        }

        @Override
        public boolean setFailure(Throwable cause) {
            return false;
        }
    }
}
