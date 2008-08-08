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

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;

import net.gleamynode.netty.array.ByteArray;
import net.gleamynode.netty.array.ByteArrayBuffer;
import net.gleamynode.netty.array.HeapByteArray;
import net.gleamynode.netty.channel.Channel;
import net.gleamynode.netty.channel.ChannelDownstreamHandler;
import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.ChannelFuture;
import net.gleamynode.netty.channel.ChannelFutureListener;
import net.gleamynode.netty.channel.ChannelHandlerContext;
import net.gleamynode.netty.channel.ChannelStateEvent;
import net.gleamynode.netty.channel.ChannelUtil;
import net.gleamynode.netty.channel.DefaultChannelFuture;
import net.gleamynode.netty.channel.DefaultMessageEvent;
import net.gleamynode.netty.channel.MessageEvent;
import net.gleamynode.netty.channel.SucceededChannelFuture;
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
                handshakeFuture = this.handshakeFuture =
                    new DefaultChannelFuture(channel, false);
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
            final ChannelHandlerContext context, final ChannelEvent element) throws Exception {
        if (element instanceof ChannelStateEvent) {
            ChannelStateEvent e = (ChannelStateEvent) element;
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
        if (!(element instanceof MessageEvent)) {
            context.sendDownstream(element);
            return;
        }

        MessageEvent e = (MessageEvent) element;
        if (!(e.getMessage() instanceof ByteArray)) {
            context.sendDownstream(element);
            return;
        }

        // Don't encrypt the first write request if this handler is
        // created with startTLS flag turned on.
        if (startTls && sentFirstMessage.compareAndSet(false, true)) {
            context.sendDownstream(element);
            return;
        }

        // Otherwise, all messages are encrypted.
        ByteArray msg = (ByteArray) e.getMessage();
        PendingWrite pendingWrite =
            new PendingWrite(element.getFuture(), msg.getByteBuffer());
        synchronized (pendingUnencryptedWrites) {
            pendingUnencryptedWrites.offer(pendingWrite);
        }

        wrap(context, element.getChannel());
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx,
            ChannelStateEvent e) throws Exception {
        super.channelDisconnected(ctx, e);
        unwrap(ctx, e.getChannel(), ByteArray.EMPTY_BUFFER);
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
    protected Object readFrame(
            ChannelHandlerContext ctx, Channel channel, ByteArrayBuffer buffer) throws Exception {
        if (buffer.length() < 2) {
            return null;
        }

        int packetLength = buffer.getBE16(buffer.firstIndex()) & 0xFFFF;
        if ((packetLength & 0x8000) != 0) {
            // Detected a SSLv2 packet
            packetLength &= 0x7FFF;
            packetLength += 2;
        } else  if (buffer.length() < 5) {
            return null;
        } else {
            // Detected a SSLv3 / TLSv1 packet
            packetLength = (buffer.getBE16(buffer.firstIndex() + 3) & 0xFFFF) + 5;
        }

        if (buffer.length() < packetLength) {
            return null;
        }

        Object frame = unwrap(ctx, channel, buffer.read(packetLength));
        if (frame == null && engine.isInboundDone()) {
            for (;;) {
                ChannelFuture future = closeFutures.poll();
                if (future == null) {
                    break;
                }
                ChannelUtil.close(ctx, channel, future);
            }
        }
        return frame;
    }

    private ChannelFuture wrap(ChannelHandlerContext context, Channel channel)
            throws SSLException {

        ChannelFuture future = null;
        ByteArray msg;
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
                        msg = new HeapByteArray(outNetBuf.remaining());
                        msg.set(msg.firstIndex(), outNetBuf.array(), 0, msg.length());
                        outNetBuf.clear();

                        if (pendingWrite.outAppBuf.hasRemaining()) {
                            // pendingWrite's future shouldn't be notified if
                            // only partial data is written.
                            future = new SucceededChannelFuture(channel);
                        } else {
                            future = pendingWrite.future;
                        }

                        MessageEvent encryptedWrite =
                            new DefaultMessageEvent(channel, future, msg, null);

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
            future = new SucceededChannelFuture(channel);
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
                    ByteArray msg = new HeapByteArray(outNetBuf.remaining());
                    msg.set(msg.firstIndex(), outNetBuf.array(), 0, msg.length());
                    outNetBuf.clear();
                    if (channel.isConnected()) {
                        future = new DefaultChannelFuture(channel, false);
                        ChannelUtil.write(ctx, channel, future, msg);
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
            future = new SucceededChannelFuture(channel);
        }
        return future;
    }

    private ByteArray unwrap(
            ChannelHandlerContext ctx, Channel channel, ByteArray packet) throws SSLException {
        ByteBuffer inNetBuf = packet.getByteBuffer();
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
                ByteArray frame = new HeapByteArray(outAppBuf.remaining());
                frame.set(frame.firstIndex(), outAppBuf.array(), 0, frame.length());
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
        unwrap(context, e.getChannel(), ByteArray.EMPTY_BUFFER);
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
