package io.netty.channel.socket.nio;

import io.netty.channel.Channel;
import io.netty.channel.ChannelBufferHolder;
import io.netty.channel.ChannelBufferHolders;
import io.netty.channel.ChannelPipeline;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.util.Queue;

abstract class AbstractNioMessageChannel extends AbstractNioChannel {

    private final ChannelBufferHolder<Object> firstOut = ChannelBufferHolders.messageBuffer();

    protected AbstractNioMessageChannel(
            Channel parent, Integer id, SelectableChannel ch, int defaultInterestOps) {
        super(parent, id, ch, defaultInterestOps);
    }

    @Override
    protected ChannelBufferHolder<Object> firstOut() {
        return firstOut;
    }

    @Override
    protected Unsafe newUnsafe() {
        return new NioMessageUnsafe();
    }

    private class NioMessageUnsafe extends AbstractNioUnsafe {
        @Override
        public void read() {
            assert eventLoop().inEventLoop();

            final ChannelPipeline pipeline = pipeline();
            final ChannelBufferHolder<Object> buf = pipeline.nextIn();
            boolean closed = false;
            boolean read = false;
            try {
                Queue<Object> msgBuf = buf.messageBuffer();
                for (;;) {
                    int localReadAmount = doReadMessages(msgBuf);
                    if (localReadAmount > 0) {
                        read = true;
                    } else if (localReadAmount == 0) {
                        break;
                    } else if (localReadAmount < 0) {
                        closed = true;
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
        flushMessageBuf(buf.messageBuffer());
    }

    private void flushMessageBuf(Queue<Object> buf) throws Exception {
        final int writeSpinCount = config().getWriteSpinCount() - 1;
        while (!buf.isEmpty()) {
            boolean wrote = false;
            for (int i = writeSpinCount; i >= 0; i --) {
                int localFlushedAmount = doWriteMessages(buf, i == 0);
                if (localFlushedAmount > 0) {
                    wrote = true;
                    break;
                }
            }

            if (!wrote) {
                break;
            }
        }
    }

    protected abstract int doReadMessages(Queue<Object> buf) throws Exception;
    protected abstract int doWriteMessages(Queue<Object> buf, boolean lastSpin) throws Exception;
}
