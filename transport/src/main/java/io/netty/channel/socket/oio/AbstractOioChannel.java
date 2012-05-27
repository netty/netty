package io.netty.channel.socket.oio;

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelBufferHolder;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Queue;

abstract class AbstractOioChannel extends AbstractChannel {

    protected AbstractOioChannel(Channel parent, Integer id) {
        super(parent, id);
    }

    @Override
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) super.localAddress();
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return (InetSocketAddress) super.remoteAddress();
    }

    @Override
    public OioUnsafe unsafe() {
        return (OioUnsafe) super.unsafe();
    }

    @Override
    protected OioUnsafe newUnsafe() {
        return new DefaultOioUnsafe();
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof OioChildEventLoop;
    }

    @Override
    protected void doRegister() throws Exception {
        // NOOP
    }

    @Override
    protected void doDeregister() throws Exception {
        // NOOP
    }

    @Override
    protected boolean isFlushPending() {
        return false;
    }

    public interface OioUnsafe extends Unsafe {
        void read();
    }

    private class DefaultOioUnsafe extends AbstractUnsafe implements OioUnsafe {

        @Override
        public void connect(
                final SocketAddress remoteAddress,
                final SocketAddress localAddress, final ChannelFuture future) {
            if (eventLoop().inEventLoop()) {
                if (!ensureOpen(future)) {
                    return;
                }

                try {
                    boolean wasActive = isActive();
                    doConnect(remoteAddress, localAddress);
                    future.setSuccess();
                    if (!wasActive && isActive()) {
                        pipeline().fireChannelActive();
                    }
                } catch (Throwable t) {
                    future.setFailure(t);
                    pipeline().fireExceptionCaught(t);
                    closeIfClosed();
                }
            } else {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        connect(remoteAddress, localAddress, future);
                    }
                });
            }
        }

        @Override
        public void read() {
            assert eventLoop().inEventLoop();

            final ChannelPipeline pipeline = pipeline();
            final ChannelBufferHolder<Object> buf = pipeline.nextIn();
            boolean closed = false;
            boolean read = false;
            try {
                if (buf.hasMessageBuffer()) {
                    Queue<Object> msgBuf = buf.messageBuffer();
                    int localReadAmount = doReadMessages(msgBuf);
                    if (localReadAmount > 0) {
                        read = true;
                    } else if (localReadAmount < 0) {
                        closed = true;
                    }
                } else {
                    ChannelBuffer byteBuf = buf.byteBuffer();
                    int localReadAmount = doReadBytes(byteBuf);
                    if (localReadAmount > 0) {
                        read = true;
                    } else if (localReadAmount < 0) {
                        closed = true;
                    }
                }
            } catch (Throwable t) {
                if (read) {
                    read = false;
                    pipeline.fireInboundBufferUpdated();
                }
                pipeline().fireExceptionCaught(t);
                if (t instanceof IOException) {
                    close(voidFuture());
                }
            } finally {
                if (read) {
                    pipeline.fireInboundBufferUpdated();
                }
                if (closed && isOpen()) {
                    close(voidFuture());
                }
            }
        }
    }

    protected abstract void doConnect(
            SocketAddress remoteAddress, SocketAddress localAddress) throws Exception;

    protected int doReadMessages(Queue<Object> buf) throws Exception {
        throw new UnsupportedOperationException();
    }

    protected int doReadBytes(ChannelBuffer buf) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void doFlush(ChannelBufferHolder<Object> buf) throws Exception {
        if (buf.hasByteBuffer()) {
            flushByteBuf(buf.byteBuffer());
        } else {
            flushMessageBuf(buf.messageBuffer());
        }
    }

    private void flushByteBuf(ChannelBuffer buf) throws Exception {
        while (buf.readable()) {
            int localFlushedAmount = doWriteBytes(buf);
            if (localFlushedAmount > 0) {
                writeCounter += localFlushedAmount;
                notifyFlushFutures();
            }
        }
        buf.clear();
    }

    private void flushMessageBuf(Queue<Object> buf) throws Exception {
        while (!buf.isEmpty()) {
            int localFlushedAmount = doWriteMessages(buf);
            if (localFlushedAmount > 0) {
                writeCounter += localFlushedAmount;
                notifyFlushFutures();
            }
        }
    }

    protected int doWriteMessages(Queue<Object> buf) throws Exception {
        throw new UnsupportedOperationException();
    }

    protected int doWriteBytes(ChannelBuffer buf) throws Exception {
        throw new UnsupportedOperationException();
    }
}
