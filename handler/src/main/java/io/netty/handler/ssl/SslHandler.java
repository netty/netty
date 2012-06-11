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
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundByteHandler;
import io.netty.channel.ChannelOutboundByteHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultChannelFuture;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;

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
 * the connection was established. if {@link #setIssueHandshake(boolean)} is set to <code>true</code>
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
 * @apiviz.landmark
 * @apiviz.uses io.netty.handler.ssl.SslBufferPool
 */
public class SslHandler
        extends ChannelHandlerAdapter
        implements ChannelInboundByteHandler, ChannelOutboundByteHandler {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(SslHandler.class);

    private static final Pattern IGNORABLE_ERROR_MESSAGE = Pattern.compile(
            "^.*(?:connection.*reset|connection.*closed|broken.*pipe).*$",
            Pattern.CASE_INSENSITIVE);

    private volatile ChannelHandlerContext ctx;
    private final SSLEngine engine;
    private final Executor delegatedTaskExecutor;

    private final boolean startTls;
    private boolean sentFirstMessage;

    private final Queue<ChannelFuture> handshakeFutures = new ArrayDeque<ChannelFuture>();
    private final SSLEngineInboundCloseFuture sslCloseFuture = new SSLEngineInboundCloseFuture();

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

    /**
     * Returns the {@link SSLEngine} which is used by this handler.
     */
    public SSLEngine getEngine() {
        return engine;
    }

    public ChannelFuture handshake() {
        return handshake(ctx.newFuture());
    }

    /**
     * Starts an SSL / TLS handshake for the specified channel.
     *
     * @return a {@link ChannelFuture} which is notified when the handshake
     *         succeeds or fails.
     */
    public ChannelFuture handshake(final ChannelFuture future) {
        final ChannelHandlerContext ctx = this.ctx;

        ctx.executor().schedule(new Runnable() {
            @Override
            public void run() {
                if (future.isDone()) {
                    return;
                }

                SSLException e = new SSLException("handshake timed out");
                future.setFailure(e);
                ctx.fireExceptionCaught(e);
                ctx.close();
            }
        }, 10, TimeUnit.SECONDS); // FIXME: Magic value
        ctx.executor().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    engine.beginHandshake();
                    handshakeFutures.add(future);
                    flush(ctx, ctx.newFuture());
                } catch (Exception e) {
                    future.setFailure(e);
                    ctx.fireExceptionCaught(e);
                }
            }
        });

        return future;
    }

    /**
     * Sends an SSL {@code close_notify} message to the specified channel and
     * destroys the underlying {@link SSLEngine}.
     */
    public ChannelFuture close() {
        return close(ctx.newFuture());
    }

    public ChannelFuture close(final ChannelFuture future) {
        final ChannelHandlerContext ctx = this.ctx;
        ctx.executor().execute(new Runnable() {
            @Override
            public void run() {
                engine.closeOutbound();
                ctx.flush(future);
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
    public ByteBuf newOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
        return Unpooled.dynamicBuffer();
    }

    @Override
    public ByteBuf newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
        return Unpooled.dynamicBuffer();
    }

    @Override
    public void disconnect(final ChannelHandlerContext ctx,
            final ChannelFuture future) throws Exception {
        closeOutboundAndChannel(ctx, future, true);
    }

    @Override
    public void close(final ChannelHandlerContext ctx,
            final ChannelFuture future) throws Exception {
        closeOutboundAndChannel(ctx, future, false);
    }


    @Override
    public void flush(final ChannelHandlerContext ctx, ChannelFuture future) throws Exception {
        final ByteBuf in = ctx.outboundByteBuffer();
        final ByteBuf out = ctx.nextOutboundByteBuffer();

        out.discardReadBytes();

        // Do not encrypt the first write request if this handler is
        // created with startTLS flag turned on.
        if (startTls && !sentFirstMessage) {
            sentFirstMessage = true;
            out.writeBytes(in);
            ctx.flush(future);
            return;
        }

        boolean unwrapLater = false;
        int bytesProduced = 0;
        try {
            for (;;) {
                SSLEngineResult result = wrap(engine, in, out);
                bytesProduced += result.bytesProduced();
                if (result.getStatus() == Status.CLOSED) {
                    // SSLEngine has been closed already.
                    // Any further write attempts should be denied.
                    if (in.readable()) {
                        in.clear();
                        SSLException e = new SSLException("SSLEngine already closed");
                        future.setFailure(e);
                        ctx.fireExceptionCaught(e);
                    }
                    break;
                } else {
                    switch (result.getHandshakeStatus()) {
                    case NEED_WRAP:
                        ctx.flush();
                        continue;
                    case NEED_UNWRAP:
                        if (ctx.inboundByteBuffer().readable()) {
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
            if (bytesProduced > 0) {
                in.discardReadBytes();
                ctx.flush(future);
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
                out.ensureWritableBytes(engine.getSession().getPacketBufferSize());
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
            engine.closeOutbound();
            try {
                engine.closeInbound();
            } catch (SSLException ex) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Failed to clean up SSLEngine.", ex);
                }
            }
            ctx.fireChannelInactive();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof IOException && engine.isOutboundDone()) {
            String message = String.valueOf(cause.getMessage()).toLowerCase();
            if (IGNORABLE_ERROR_MESSAGE.matcher(message).matches()) {
                // It is safe to ignore the 'connection reset by peer' or
                // 'broken pipe' error after sending closure_notify.
                if (logger.isDebugEnabled()) {
                    logger.debug(
                            "Swallowing a 'connection reset by peer / " +
                            "broken pipe' error occurred while writing " +
                            "'closure_notify'", cause);
                }

                // Close the connection explicitly just in case the transport
                // did not close the connection automatically.
                if (ctx.channel().isActive()) {
                    ctx.close();
                }
                return;
            }
        }
        super.exceptionCaught(ctx, cause);
    }

    @Override
    public void inboundBufferUpdated(final ChannelHandlerContext ctx) throws Exception {
        final ByteBuf in = ctx.inboundByteBuffer();
        final ByteBuf out = ctx.nextInboundByteBuffer();
        out.discardReadBytes();

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
                    sslCloseFuture.setClosed();
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
                flush(ctx, ctx.newFuture());
            }
        } catch (SSLException e) {
            setHandshakeFailure(e);
            throw e;
        } finally {
            if (bytesProduced > 0) {
                in.discardReadBytes();
                ctx.fireInboundBufferUpdated();
            }
        }
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
                out.ensureWritableBytes(engine.getSession().getApplicationBufferSize());
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

    private void setHandshakeSuccess() {
        for (;;) {
            ChannelFuture f = handshakeFutures.poll();
            if (f == null) {
                break;
            }
            f.setSuccess();
        }
    }

    private void setHandshakeFailure(Throwable cause) {
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

        for (;;) {
            ChannelFuture f = handshakeFutures.poll();
            if (f == null) {
                break;
            }
            if (cause == null) {
                cause = new ClosedChannelException();
            }
            f.setFailure(cause);
        }
    }

    private void closeOutboundAndChannel(
            final ChannelHandlerContext ctx, final ChannelFuture future, boolean disconnect) throws Exception {
        if (!ctx.channel().isActive()) {
            if (disconnect) {
                ctx.disconnect(future);
            } else {
                ctx.close(future);
            }
            return;
        }

        engine.closeOutbound();

        ChannelFuture closeNotifyFuture = ctx.newFuture();
        flush(ctx, closeNotifyFuture);

        // Force-close the connection if close_notify is not fully sent in time.
        final ScheduledFuture<?> timeoutFuture = ctx.executor().schedule(new Runnable() {
            @Override
            public void run() {
                if (future.setSuccess()) {
                    logger.debug("close_notify write attempt timed out. Force-closing the connection.");
                    ctx.close(ctx.newFuture());
                }
            }
        }, 3, TimeUnit.SECONDS); // FIXME: Magic value

        // Close the connection if close_notify is sent in time.
        closeNotifyFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture f)
                    throws Exception {
                timeoutFuture.cancel(false);
                ctx.close(future);
            }
        });
    }

    @Override
    public void beforeAdd(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
    }

    @Override
    public void afterAdd(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isActive()) {
            // channelActvie() event has been fired already, which means this.channelActive() will
            // not be invoked. We have to initialize here instead.
            handshake();
        } else {
            // channelActive() event has not been fired yet.  this.channelOpen() will be invoked
            // and initialization will occur there.
        }
    }

    /**
     * Calls {@link #handshake()} once the {@link Channel} is connected
     */
    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        if (!startTls && engine.getUseClientMode()) {
            // issue and handshake and add a listener to it which will fire an exception event if
            // an exception was thrown while doing the handshake
            handshake().addListener(new ChannelFutureListener() {

                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (!future.isSuccess()) {
                        ctx.pipeline().fireExceptionCaught(future.cause());
                    } else {
                        // Send the event upstream after the handshake was completed without an error.
                        //
                        // See https://github.com/netty/netty/issues/358
                       ctx.fireChannelActive();
                    }

                }
            });
        } else {
            ctx.fireChannelActive();
        }
    }

    private final class SSLEngineInboundCloseFuture extends DefaultChannelFuture {
        public SSLEngineInboundCloseFuture() {
            super(null, true);
        }

        void setClosed() {
            super.setSuccess();
        }

        @Override
        public Channel channel() {
            if (ctx == null) {
                // Maybe we should better throw an IllegalStateException() ?
                return null;
            } else {
                return ctx.channel();
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
