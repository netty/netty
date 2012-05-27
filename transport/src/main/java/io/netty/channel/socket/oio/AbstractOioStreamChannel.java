package io.netty.channel.socket.oio;

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelBufferHolder;
import io.netty.channel.ChannelBufferHolders;
import io.netty.channel.ChannelPipeline;

import java.io.IOException;

abstract class AbstractOioStreamChannel extends AbstractOioChannel {

    private final ChannelBufferHolder<?> firstOut = ChannelBufferHolders.byteBuffer();

    protected AbstractOioStreamChannel(Channel parent, Integer id) {
        super(parent, id);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ChannelBufferHolder<Object> firstOut() {
        return (ChannelBufferHolder<Object>) firstOut;
    }

    @Override
    protected Unsafe newUnsafe() {
        return new OioStreamUnsafe();
    }

    private class OioStreamUnsafe extends AbstractOioUnsafe {
        @Override
        public void read() {
            assert eventLoop().inEventLoop();

            final ChannelPipeline pipeline = pipeline();
            final ChannelBufferHolder<Object> buf = pipeline.nextIn();
            boolean closed = false;
            boolean read = false;
            try {
                ChannelBuffer byteBuf = buf.byteBuffer();
                expandReadBuffer(byteBuf);
                int localReadAmount = doReadBytes(byteBuf);
                if (localReadAmount > 0) {
                    read = true;
                } else if (localReadAmount < 0) {
                    closed = true;
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
        while (buf.readable()) {
            int localFlushedAmount = doWriteBytes(buf);
            if (localFlushedAmount > 0) {
                writeCounter += localFlushedAmount;
                notifyFlushFutures();
            }
        }
        buf.clear();
    }

    protected abstract int available();
    protected abstract int doReadBytes(ChannelBuffer buf) throws Exception;
    protected abstract int doWriteBytes(ChannelBuffer buf) throws Exception;

    private void expandReadBuffer(ChannelBuffer byteBuf) {
        int available = available();
        if (available > 0) {
            byteBuf.ensureWritableBytes(available);
        } else if (!byteBuf.writable()) {
            // FIXME: Magic number
            byteBuf.ensureWritableBytes(4096);
        }
    }
}
