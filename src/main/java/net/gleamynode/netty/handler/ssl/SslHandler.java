/*
 * Copyright (C) 2008  Trustin Heuiseung Lee
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, 5th Floor, Boston, MA 02110-1301 USA
 */
package net.gleamynode.netty.handler.ssl;

import static net.gleamynode.netty.channel.Channels.*;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;

import net.gleamynode.netty.buffer.ChannelBuffer;
import net.gleamynode.netty.buffer.ChannelBuffers;
import net.gleamynode.netty.channel.Channel;
import net.gleamynode.netty.channel.ChannelDownstreamHandler;
import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.ChannelFuture;
import net.gleamynode.netty.channel.ChannelFutureListener;
import net.gleamynode.netty.channel.ChannelHandlerContext;
import net.gleamynode.netty.channel.ChannelStateEvent;
import net.gleamynode.netty.channel.Channels;
import net.gleamynode.netty.channel.MessageEvent;
import net.gleamynode.netty.handler.codec.frame.FrameDecoder;
import net.gleamynode.netty.util.ImmediateExecutor;

/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.uses net.gleamynode.netty.handler.ssl.SslBufferPool
 */
public class SslHandler extends FrameDecoder implements ChannelDownstreamHandler {

    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    private static SslBufferPool defaultBufferPool;

    private static synchronized SslBufferPool getDefaultBufferPool() {
        if (defaultBufferPool == null) {
            defaultBufferPool = new SslBufferPool();
        }
        return defaultBufferPool;
    }

    private final SSLEngine engine;
    private final SslBufferPool bufferPool;
    private final Executor delegatedTaskExecutor;
    private final boolean startTls;

    private final Object handshakeLock = new Object();
    private volatile boolean handshaking;
    private volatile boolean handshaken;
    private volatile ChannelFuture handshakeFuture;

    private final AtomicBoolean sentFirstMessage = new AtomicBoolean();
    private final AtomicBoolean sentCloseNotify = new AtomicBoolean();
    final Queue<ChannelFuture> closeFutures = new ConcurrentLinkedQueue<ChannelFuture>();
    private final Queue<PendingWrite> pendingUnencryptedWrites = new LinkedList<PendingWrite>();
    private final Queue<MessageEvent> pendingEncryptedWrites = new LinkedList<MessageEvent>();

    public SslHandler(SSLEngine engine) {
        this(engine, getDefaultBufferPool(), ImmediateExecutor.INSTANCE);
    }

    public SslHandler(SSLEngine engine, SslBufferPool bufferPool) {
        this(engine, bufferPool, ImmediateExecutor.INSTANCE);
    }

    public SslHandler(SSLEngine engine, boolean startTls) {
        this(engine, getDefaultBufferPool(), startTls);
    }

    public SslHandler(SSLEngine engine, SslBufferPool bufferPool, boolean startTls) {
        this(engine, bufferPool, startTls, ImmediateExecutor.INSTANCE);
    }

    public SslHandler(SSLEngine engine, Executor delegatedTaskExecutor) {
        this(engine, getDefaultBufferPool(), delegatedTaskExecutor);
    }

    public SslHandler(SSLEngine engine, SslBufferPool bufferPool, Executor delegatedTaskExecutor) {
        this(engine, bufferPool, false, delegatedTaskExecutor);
    }

    public SslHandler(SSLEngine engine, boolean startTls, Executor delegatedTaskExecutor) {
        this(engine, getDefaultBufferPool(), startTls, delegatedTaskExecutor);
    }

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

    public SSLEngine getEngine() {
        return engine;
    }

    public ChannelFuture handshake(Channel channel) throws SSLException {
        ChannelFuture handshakeFuture;
        synchronized (handshakeLock) {
            if (handshaking) {
                return this.handshakeFuture;
            } else {
                handshakeFuture = this.handshakeFuture = future(channel);
                handshaking = true;
            }
        }

        ChannelHandlerContext ctx = context(channel);
        engine.beginHandshake();
        wrapNonAppData(ctx, channel);
        return handshakeFuture;
    }

    public ChannelFuture close(Channel channel) throws SSLException {
        ChannelHandlerContext ctx = context(channel);
        engine.closeOutbound();
        return wrapNonAppData(ctx, channel);
    }

    private ChannelHandlerContext context(Channel channel) {
        return channel.getPipeline().getContext(getClass());
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

        // Don't encrypt the first write request if this handler is
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
            pendingUnencryptedWrites.offer(pendingWrite);
        }

        wrap(context, evt.getChannel());
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx,
            ChannelStateEvent e) throws Exception {
        super.channelDisconnected(ctx, e);
        unwrap(ctx, e.getChannel(), ChannelBuffer.EMPTY_BUFFER, 0, 0);
        engine.closeOutbound();
        if (!sentCloseNotify.get() && handshaken) {
            try {
                engine.closeInbound();
            } catch (SSLException ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer) throws Exception {
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

        try {
            Object frame = unwrap(ctx, channel, buffer, buffer.readerIndex(), packetLength);
            if (frame == null && engine.isInboundDone()) {
                for (;;) {
                    ChannelFuture future = closeFutures.poll();
                    if (future == null) {
                        break;
                    }
                    Channels.close(ctx, channel, future);
                }
            }
            return frame;
        } finally {
            buffer.skipBytes(packetLength);
        }
    }

    private ChannelFuture wrap(ChannelHandlerContext context, Channel channel)
            throws SSLException {

        ChannelFuture future = null;
        ChannelBuffer msg;
        ByteBuffer outNetBuf = bufferPool.acquire();
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

                    SSLEngineResult result;
                    try {
                        result = engine.wrap(outAppBuf, outNetBuf);
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

                        MessageEvent encryptedWrite = messageEvent(channel, future, msg);
                        if (Thread.holdsLock(pendingEncryptedWrites)) {
                            pendingEncryptedWrites.offer(encryptedWrite);
                        } else {
                            synchronized (pendingEncryptedWrites) {
                                pendingEncryptedWrites.offer(encryptedWrite);
                            }
                        }
                    } else {
                        switch (result.getHandshakeStatus()) {
                        case NEED_WRAP:
                            if (outAppBuf.hasRemaining()) {
                                break;
                            } else {
                                break loop;
                            }
                        case NEED_UNWRAP:
                            break loop;
                        case NEED_TASK:
                            runDelegatedTasks();
                            break;
                        case FINISHED:
                            setHandshakeSuccess();
                        default:
                            break loop;
                        }
                    }
                }
            }
        } catch (SSLException e) {
            if (handshaking) {
                setHandshakeFailure(e);
            }
            throw e;
        } finally {
            bufferPool.release(outNetBuf);
        }

        flushPendingEncryptedWrites(context);

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
                result = engine.wrap(EMPTY_BUFFER, outNetBuf);

                if (result.bytesProduced() > 0) {
                    outNetBuf.flip();
                    ChannelBuffer msg = ChannelBuffers.buffer(outNetBuf.remaining());
                    msg.writeBytes(outNetBuf.array(), 0, msg.capacity());
                    outNetBuf.clear();
                    if (channel.isConnected()) {
                        future = future(channel);
                        write(ctx, channel, future, msg);
                    }
                }

                switch (result.getHandshakeStatus()) {
                case FINISHED:
                    setHandshakeSuccess();
                    break;
                case NEED_TASK:
                    runDelegatedTasks();
                    break;
                }

                if (result.bytesProduced() == 0) {
                    break;
                }
            }
        } catch (SSLException e) {
            if (handshaking) {
                setHandshakeFailure(e);
            }
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
            loop:
            for (;;) {
                SSLEngineResult result = engine.unwrap(inNetBuf, outAppBuf);

                switch (result.getHandshakeStatus()) {
                case NEED_UNWRAP:
                    if (inNetBuf.hasRemaining()) {
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
                    setHandshakeSuccess();
                case NOT_HANDSHAKING:
                    wrap(ctx, channel);
                    break loop;
                default:
                    break loop;
                }
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
            if (handshaking) {
                setHandshakeFailure(e);
            }
            throw e;
        } finally {
            bufferPool.release(outAppBuf);
        }
    }

    private void runDelegatedTasks() {
        Runnable task;
        while ((task = engine.getDelegatedTask()) != null) {
            delegatedTaskExecutor.execute(task);
        }
    }

    private void setHandshakeSuccess() {
        synchronized (handshakeLock) {
            handshaking = false;
            handshaken = true;
        }
        handshakeFuture.setSuccess();
    }

    private void setHandshakeFailure(SSLException cause) {
        synchronized (handshakeLock) {
            handshaking = false;
            handshaken = false;
        }
        handshakeFuture.setFailure(cause);
    }

    private void closeOutboundAndChannel(
            final ChannelHandlerContext context, final ChannelStateEvent e) throws SSLException {
        unwrap(context, e.getChannel(), ChannelBuffer.EMPTY_BUFFER, 0, 0);
        if (!engine.isInboundDone()) {
            if (sentCloseNotify.compareAndSet(false, true)) {
                engine.closeOutbound();
                ChannelFuture closeNotifyFuture = wrapNonAppData(context, e.getChannel());
                closeNotifyFuture.addListener(new ChannelFutureListener() {
                    public void operationComplete(ChannelFuture closeNotifyFuture) throws Exception {
                        closeFutures.offer(e.getFuture());
                    }
                });
                return;
            }
        }

        context.sendDownstream(e);
    }

    private static class PendingWrite {
        final ChannelFuture future;
        final ByteBuffer outAppBuf;

        PendingWrite(ChannelFuture future, ByteBuffer outAppBuf) {
            this.future = future;
            this.outAppBuf = outAppBuf;
        }
    }
}
