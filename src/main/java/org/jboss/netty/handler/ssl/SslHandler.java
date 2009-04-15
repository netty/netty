/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.handler.ssl;

import static org.jboss.netty.channel.Channels.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.DownstreamMessageEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.frame.FrameDecoder;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.internal.ImmediateExecutor;

/**
 * Adds <a href="http://en.wikipedia.org/wiki/Transport_Layer_Security">SSL
 * &middot; TLS</a> and StartTLS support to a {@link Channel}.  Please refer
 * to the <strong>"SecureChat"</strong> example in the distribution or the web
 * site for the detailed usage.
 *
 * <h3>Beginning the handshake</h3>
 * <p>
 * A user should make sure not to write a message while the
 * {@linkplain #handshake(Channel) handshake} is in progress unless it is a
 * renegotiation.  You will be notified by the {@link ChannelFuture} which is
 * returned by the {@link #handshake(Channel)} method when the handshake
 * process succeeds or fails.
 *
 * <h3>Renegotiation</h3>
 * <p>
 * Once the initial handshake is done successfully.  You can always call
 * {@link #handshake(Channel)} again to renegotiate the SSL session parameters.
 *
 * <h3>Closing the session</h3>
 * <p>
 * To close the SSL session, the {@link #close(Channel)} method should be
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
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 * @apiviz.uses org.jboss.netty.handler.ssl.SslBufferPool
 */
public class SslHandler extends FrameDecoder {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(SslHandler.class);

    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    private static final Pattern CONNECTION_RESET = Pattern.compile(
            "^.*(Connection\\s*reset|Broken\\s*pipe).*$",
            Pattern.CASE_INSENSITIVE);

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

    private final SSLEngine engine;
    private final SslBufferPool bufferPool;
    private final Executor delegatedTaskExecutor;
    private final boolean startTls;

    final Object handshakeLock = new Object();
    private boolean initialHandshake;
    private boolean handshaking;
    private volatile boolean handshaken;
    private volatile ChannelFuture handshakeFuture;

    private final AtomicBoolean sentFirstMessage = new AtomicBoolean();
    private final AtomicBoolean sentCloseNotify = new AtomicBoolean();
    final AtomicBoolean ignoreClosedChannelException = new AtomicBoolean();
    private final Queue<PendingWrite> pendingUnencryptedWrites = new LinkedList<PendingWrite>();
    private final Queue<MessageEvent> pendingEncryptedWrites = new LinkedList<MessageEvent>();

    /**
     * Creates a new instance.
     *
     * @param engine  the {@link SSLEngine} this handler will use
     */
    public SslHandler(SSLEngine engine) {
        this(engine, getDefaultBufferPool(), ImmediateExecutor.INSTANCE);
    }

    /**
     * Creates a new instance.
     *
     * @param engine      the {@link SSLEngine} this handler will use
     * @param bufferPool  the {@link SslBufferPool} where this handler will
     *                    acquire the buffers required by the {@link SSLEngine}
     */
    public SslHandler(SSLEngine engine, SslBufferPool bufferPool) {
        this(engine, bufferPool, ImmediateExecutor.INSTANCE);
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
        this(engine, bufferPool, startTls, ImmediateExecutor.INSTANCE);
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
        this(engine, getDefaultBufferPool(), delegatedTaskExecutor);
    }

    /**
     * Creates a new instance.
     *
     * @param engine
     *        the {@link SSLEngine} this handler will use
     * @param bufferPool
     *        the {@link SslBufferPool} where this handler will acquire
     *        the buffers required by the {@link SSLEngine}
     * @param delegatedTaskExecutor
     *        the {@link Executor} which will execute the delegated task
     *        that {@link SSLEngine#getDelegatedTask()} will return
     */
    public SslHandler(SSLEngine engine, SslBufferPool bufferPool, Executor delegatedTaskExecutor) {
        this(engine, bufferPool, false, delegatedTaskExecutor);
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
        this(engine, getDefaultBufferPool(), startTls, delegatedTaskExecutor);
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
     * @param delegatedTaskExecutor
     *        the {@link Executor} which will execute the delegated task
     *        that {@link SSLEngine#getDelegatedTask()} will return
     */
    public SslHandler(SSLEngine engine, SslBufferPool bufferPool, boolean startTls, Executor delegatedTaskExecutor) {
        if (engine == null) {
            throw new NullPointerException("engine");
        }
        if (bufferPool == null) {
            throw new NullPointerException("bufferPool");
        }
        if (delegatedTaskExecutor == null) {
            throw new NullPointerException("delegatedTaskExecutor");
        }
        this.engine = engine;
        this.bufferPool = bufferPool;
        this.delegatedTaskExecutor = delegatedTaskExecutor;
        this.startTls = startTls;
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
    public ChannelFuture handshake(Channel channel) throws SSLException {
        ChannelFuture handshakeFuture;
        synchronized (handshakeLock) {
            if (handshaking) {
                return this.handshakeFuture;
            } else {
                engine.beginHandshake();
                runDelegatedTasks();
                handshakeFuture = this.handshakeFuture = newHandshakeFuture(channel);
                handshaking = true;
            }
        }

        wrapNonAppData(context(channel), channel);
        return handshakeFuture;
    }

    /**
     * Sends an SSL {@code close_notify} message to the specified channel and
     * destroys the underlying {@link SSLEngine}.
     */
    public ChannelFuture close(Channel channel) throws SSLException {
        ChannelHandlerContext ctx = context(channel);
        engine.closeOutbound();
        return wrapNonAppData(ctx, channel);
    }

    private ChannelHandlerContext context(Channel channel) {
        return channel.getPipeline().getContext(getClass());
    }

    @Override
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
        if (startTls && sentFirstMessage.compareAndSet(false, true)) {
            context.sendDownstream(evt);
            return;
        }

        // Otherwise, all messages are encrypted.
        ChannelBuffer msg = (ChannelBuffer) e.getMessage();
        PendingWrite pendingWrite =
            new PendingWrite(evt.getFuture(), msg.toByteBuffer(msg.readerIndex(), msg.readableBytes()));
        synchronized (pendingUnencryptedWrites) {
            boolean offered = pendingUnencryptedWrites.offer(pendingWrite);
            assert offered;
        }

        wrap(context, evt.getChannel());
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx,
            ChannelStateEvent e) throws Exception {

        // Make sure the handshake future is notified when a connection has
        // been closed during handshake.
        synchronized (handshakeLock) {
            if (handshaking) {
                handshakeFuture.setFailure(new ClosedChannelException());
            }
        }

        try {
            super.channelDisconnected(ctx, e);
        } finally {
            unwrap(ctx, e.getChannel(), ChannelBuffers.EMPTY_BUFFER, 0, 0);
            engine.closeOutbound();
            if (!sentCloseNotify.get() && handshaken) {
                try {
                    engine.closeInbound();
                } catch (SSLException ex) {
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
                if (ignoreClosedChannelException.compareAndSet(true, false)) {
                    logger.debug(
                            "Swallowing an exception raised while writing " +
                            "'closure_notify'", cause);
                    return;
                }
            } else if (engine.isOutboundDone()) {
                String message = String.valueOf(cause.getMessage()).toLowerCase();
                if (CONNECTION_RESET.matcher(message).matches()) {
                    // It is safe to ignore the 'connection reset by peer' error
                    // after sending closure_notify.
                    logger.debug(
                            "Swallowing a 'connection reset by peer' error " +
                            "occurred while writing 'closure_notify'", cause);

                    // Close the connection explicitly just in case the transport
                    // did not close the connection automatically.
                    Channels.close(ctx, succeededFuture(e.getChannel()));
                    return;
                }
            }
        }

        ctx.sendUpstream(e);
    }

    @Override
    protected Object decode(
            final ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer) throws Exception {
        if (buffer.readableBytes() < 2) {
            return null;
        }

        int packetLength = buffer.getShort(buffer.readerIndex()) & 0xFFFF;
        if ((packetLength & 0x8000) != 0) {
            // Detected a SSLv2 packet
            packetLength &= 0x7FFF;
            packetLength += 2;
        } else  if (buffer.readableBytes() < 5) {
            return null;
        } else {
            // Detected a SSLv3 / TLSv1 packet
            packetLength = (buffer.getShort(buffer.readerIndex() + 3) & 0xFFFF) + 5;
        }

        if (buffer.readableBytes() < packetLength) {
            return null;
        }

        ChannelBuffer frame;
        try {
            frame = unwrap(ctx, channel, buffer, buffer.readerIndex(), packetLength);
        } finally {
            buffer.skipBytes(packetLength);
        }

        return frame;
    }

    private ChannelFuture wrap(ChannelHandlerContext context, Channel channel)
            throws SSLException {

        ChannelFuture future = null;
        ChannelBuffer msg;
        ByteBuffer outNetBuf = bufferPool.acquire();
        boolean success = true;
        boolean offered = false;
        boolean needsUnwrap = false;
        try {
            loop:
            for (;;) {
                // Acquire a lock to make sure unencrypted data is polled
                // in order and their encrypted counterpart is offered in
                // order.
                synchronized (pendingUnencryptedWrites) {
                    PendingWrite pendingWrite = pendingUnencryptedWrites.peek();
                    if (pendingWrite == null) {
                        break;
                    }

                    ByteBuffer outAppBuf = pendingWrite.outAppBuf;
                    SSLEngineResult result = null;
                    try {
                        synchronized (handshakeLock) {
                            result = engine.wrap(outAppBuf, outNetBuf);
                        }
                    } finally {
                        if (!outAppBuf.hasRemaining()) {
                            pendingUnencryptedWrites.remove();
                        }
                    }

                    if (result.bytesProduced() > 0) {
                        outNetBuf.flip();
                        msg = ChannelBuffers.buffer(outNetBuf.remaining());
                        msg.writeBytes(outNetBuf.array(), 0, msg.capacity());
                        outNetBuf.clear();

                        if (pendingWrite.outAppBuf.hasRemaining()) {
                            // pendingWrite's future shouldn't be notified if
                            // only partial data is written.
                            future = succeededFuture(channel);
                        } else {
                            future = pendingWrite.future;
                        }

                        MessageEvent encryptedWrite = new DownstreamMessageEvent(
                                channel, future, msg, channel.getRemoteAddress());
                        if (Thread.holdsLock(pendingEncryptedWrites)) {
                            offered = pendingEncryptedWrites.offer(encryptedWrite);
                        } else {
                            synchronized (pendingEncryptedWrites) {
                                offered = pendingEncryptedWrites.offer(encryptedWrite);
                            }
                        }
                        assert offered;
                    } else {
                        HandshakeStatus handshakeStatus = result.getHandshakeStatus();
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
                        case NOT_HANDSHAKING:
                            if (handshakeStatus == HandshakeStatus.FINISHED) {
                                setHandshakeSuccess(channel);
                            }
                            if (result.getStatus() == Status.CLOSED) {
                                success = false;
                            }
                            break loop;
                        default:
                            throw new IllegalStateException(
                                    "Unknown handshake status: " +
                                    result.getHandshakeStatus());
                        }
                    }
                }
            }
        } catch (SSLException e) {
            success = false;
            setHandshakeFailure(channel, e);
            throw e;
        } finally {
            bufferPool.release(outNetBuf);

            if (offered) {
                flushPendingEncryptedWrites(context);
            }

            if (!success) {
                IllegalStateException cause =
                    new IllegalStateException("SSLEngine already closed");
                // Mark all remaining pending writes as failure if anything
                // wrong happened before the write requests are wrapped.
                // Please note that we do not call setFailure while a lock is
                // acquired, to avoid a potential dead lock.
                for (;;) {
                    PendingWrite pendingWrite;
                    synchronized (pendingUnencryptedWrites) {
                        pendingWrite = pendingUnencryptedWrites.poll();
                        if (pendingWrite == null) {
                            break;
                        }
                    }

                    pendingWrite.future.setFailure(cause);
                }
            }
        }

        if (needsUnwrap) {
            unwrap(context, channel, ChannelBuffers.EMPTY_BUFFER, 0, 0);
        }

        if (future == null) {
            future = succeededFuture(channel);
        }
        return future;
    }

    private void flushPendingEncryptedWrites(ChannelHandlerContext ctx) {
        // Avoid possible dead lock and data integrity issue
        // which is caused by cross communication between more than one channel
        // in the same VM.
        if (Thread.holdsLock(pendingEncryptedWrites)) {
            return;
        }

        synchronized (pendingEncryptedWrites) {
            if (pendingEncryptedWrites.isEmpty()) {
                return;
            }
        }

        synchronized (pendingEncryptedWrites) {
            MessageEvent e;
            while ((e = pendingEncryptedWrites.poll()) != null) {
                ctx.sendDownstream(e);
            }
        }
    }

    private ChannelFuture wrapNonAppData(ChannelHandlerContext ctx, Channel channel) throws SSLException {
        ChannelFuture future = null;
        ByteBuffer outNetBuf = bufferPool.acquire();

        SSLEngineResult result;
        try {
            for (;;) {
                synchronized (handshakeLock) {
                    result = engine.wrap(EMPTY_BUFFER, outNetBuf);
                }

                if (result.bytesProduced() > 0) {
                    outNetBuf.flip();
                    ChannelBuffer msg = ChannelBuffers.buffer(outNetBuf.remaining());
                    msg.writeBytes(outNetBuf.array(), 0, msg.capacity());
                    outNetBuf.clear();
                    if (channel.isConnected()) {
                        future = future(channel);
                        write(ctx, future, msg);
                    }
                }

                switch (result.getHandshakeStatus()) {
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
                        unwrap(ctx, channel, ChannelBuffers.EMPTY_BUFFER, 0, 0);
                    }
                    break;
                case NOT_HANDSHAKING:
                case NEED_WRAP:
                    break;
                default:
                    throw new IllegalStateException(
                            "Unexpected handshake status: " +
                            result.getHandshakeStatus());
                }

                if (result.bytesProduced() == 0) {
                    break;
                }
            }
        } catch (SSLException e) {
            setHandshakeFailure(channel, e);
            throw e;
        } finally {
            bufferPool.release(outNetBuf);
        }

        if (future == null) {
            future = succeededFuture(channel);
        }
        return future;
    }

    private ChannelBuffer unwrap(
            ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer, int offset, int length) throws SSLException {
        ByteBuffer inNetBuf = buffer.toByteBuffer(offset, length);
        ByteBuffer outAppBuf = bufferPool.acquire();

        try {
            boolean needsWrap = false;
            loop:
            for (;;) {
                SSLEngineResult result;
                synchronized (handshakeLock) {
                    if (initialHandshake && !engine.getUseClientMode() &&
                        !engine.isInboundDone() && !engine.isOutboundDone()) {
                        handshake(channel);
                        initialHandshake = false;
                    }

                    try {
                        result = engine.unwrap(inNetBuf, outAppBuf);
                    } catch (SSLException e) {
                        throw e;
                    }

                    switch (result.getHandshakeStatus()) {
                    case NEED_UNWRAP:
                        if (inNetBuf.hasRemaining() && !engine.isInboundDone()) {
                            break;
                        } else {
                            break loop;
                        }
                    case NEED_WRAP:
                        wrapNonAppData(ctx, channel);
                        break;
                    case NEED_TASK:
                        runDelegatedTasks();
                        break;
                    case FINISHED:
                        setHandshakeSuccess(channel);
                        needsWrap = true;
                        break loop;
                    case NOT_HANDSHAKING:
                        needsWrap = true;
                        break loop;
                    default:
                        throw new IllegalStateException(
                                "Unknown handshake status: " +
                                result.getHandshakeStatus());
                    }
                }
            }

            if (needsWrap) {
                wrap(ctx, channel);
            }

            outAppBuf.flip();

            if (outAppBuf.hasRemaining()) {
                ChannelBuffer frame = ChannelBuffers.buffer(outAppBuf.remaining());
                frame.writeBytes(outAppBuf.array(), 0, frame.capacity());
                return frame;
            } else {
                return null;
            }
        } catch (SSLException e) {
            setHandshakeFailure(channel, e);
            throw e;
        } finally {
            bufferPool.release(outAppBuf);
        }
    }

    private void runDelegatedTasks() {
        for (;;) {
            final Runnable task;
            synchronized (handshakeLock) {
                task = engine.getDelegatedTask();
            }

            if (task == null) {
                break;
            }

            delegatedTaskExecutor.execute(new Runnable() {
                public void run() {
                    synchronized (handshakeLock) {
                        task.run();
                    }
                }
            });
        }
    }

    private void setHandshakeSuccess(Channel channel) {
        synchronized (handshakeLock) {
            handshaking = false;
            handshaken = true;

            if (handshakeFuture == null) {
                handshakeFuture = newHandshakeFuture(channel);
            }
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
                handshakeFuture = newHandshakeFuture(channel);
            }
        }
        handshakeFuture.setFailure(cause);
    }

    private void closeOutboundAndChannel(
            final ChannelHandlerContext context, final ChannelStateEvent e) throws SSLException {
        unwrap(context, e.getChannel(), ChannelBuffers.EMPTY_BUFFER, 0, 0);
        if (!engine.isInboundDone()) {
            if (sentCloseNotify.compareAndSet(false, true)) {
                engine.closeOutbound();
                ChannelFuture closeNotifyFuture = wrapNonAppData(context, e.getChannel());
                closeNotifyFuture.addListener(new ChannelFutureListener() {
                    public void operationComplete(ChannelFuture closeNotifyFuture) throws Exception {
                        if (closeNotifyFuture.getCause() instanceof ClosedChannelException) {
                            ignoreClosedChannelException.set(true);
                        } else {
                            Channels.close(context, e.getFuture());
                        }
                    }
                });
                return;
            }
        }

        context.sendDownstream(e);
    }

    private static ChannelFuture newHandshakeFuture(Channel channel) {
        ChannelFuture future = future(channel);
        future.addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture future)
                    throws Exception {
                if (!future.isSuccess()) {
                    fireExceptionCaught(future.getChannel(), future.getCause());
                }
            }
        });
        return future;
    }

    private static final class PendingWrite {
        final ChannelFuture future;
        final ByteBuffer outAppBuf;

        PendingWrite(ChannelFuture future, ByteBuffer outAppBuf) {
            this.future = future;
            this.outAppBuf = outAppBuf;
        }
    }
}
