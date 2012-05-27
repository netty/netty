package io.netty.channel.socket.nio;

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelBufferHolder;
import io.netty.channel.ChannelBufferHolders;
import io.netty.channel.ChannelPipeline;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

abstract class AbstractNioStreamChannel extends AbstractNioChannel {

    private final ChannelBufferHolder<?> firstOut = ChannelBufferHolders.byteBuffer();

    protected AbstractNioStreamChannel(
            Channel parent, Integer id, SelectableChannel ch) {
        super(parent, id, ch, SelectionKey.OP_READ);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ChannelBufferHolder<Object> firstOut() {
        return (ChannelBufferHolder<Object>) firstOut;
    }

    @Override
    protected Unsafe newUnsafe() {
        return new NioStreamUnsafe();
    }

    private class NioStreamUnsafe extends AbstractNioUnsafe {
        @Override
        public void read() {
            assert eventLoop().inEventLoop();

            final ChannelPipeline pipeline = pipeline();
            final ChannelBufferHolder<Object> buf = pipeline.nextIn();
            boolean closed = false;
            boolean read = false;
            try {
                ChannelBuffer byteBuf = buf.byteBuffer();
                for (;;) {
                    int localReadAmount = doReadBytes(byteBuf);
                    if (localReadAmount > 0) {
                        read = true;
                    } else if (localReadAmount < 0) {
                        closed = true;
                        break;
                    }
                    if (!expandReadBuffer(byteBuf)) {
                        break;
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

    @Override
    protected void doFlush(ChannelBufferHolder<Object> buf) throws Exception {
        flushByteBuf(buf.byteBuffer());
    }

    private void flushByteBuf(ChannelBuffer buf) throws Exception {
        if (!buf.readable()) {
            // Reset reader/writerIndex to 0 if the buffer is empty.
            buf.clear();
            return;
        }

        for (int i = config().getWriteSpinCount() - 1; i >= 0; i --) {
            int localFlushedAmount = doWriteBytes(buf, i == 0);
            if (localFlushedAmount > 0) {
                writeCounter += localFlushedAmount;
                notifyFlushFutures();
                break;
            }
            if (!buf.readable()) {
                // Reset reader/writerIndex to 0 if the buffer is empty.
                buf.clear();
                break;
            }
        }
    }

    protected abstract int doReadBytes(ChannelBuffer buf) throws Exception;
    protected abstract int doWriteBytes(ChannelBuffer buf, boolean lastSpin) throws Exception;

    private static boolean expandReadBuffer(ChannelBuffer byteBuf) {
        if (!byteBuf.writable()) {
            // FIXME: Magic number
            byteBuf.ensureWritableBytes(4096);
            return true;
        }

        return false;
    }
}
