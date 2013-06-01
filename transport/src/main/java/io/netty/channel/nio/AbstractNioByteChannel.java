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
package io.netty.channel.nio;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.FileRegion;
import io.netty.channel.MessageList;
import io.netty.channel.socket.ChannelInputShutdownEvent;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

/**
 * {@link AbstractNioChannel} base class for {@link Channel}s that operate on bytes.
 */
public abstract class AbstractNioByteChannel extends AbstractNioChannel {

    /**
     * Create a new instance
     *
     * @param parent            the parent {@link Channel} by which this instance was created. May be {@code null}
     * @param id                the id of this instance or {@code null} if one should be generated
     * @param ch                the underlying {@link SelectableChannel} on which it operates
     */
    protected AbstractNioByteChannel(
            Channel parent, Integer id, SelectableChannel ch) {
        super(parent, id, ch, SelectionKey.OP_READ);
    }

    @Override
    protected AbstractNioUnsafe newUnsafe() {
        return new NioByteUnsafe();
    }

    private final class NioByteUnsafe extends AbstractNioUnsafe {
        @Override
        public void read() {
            assert eventLoop().inEventLoop();
            final SelectionKey key = selectionKey();
            if (!config().isAutoRead()) {
                int interestOps = key.interestOps();
                if ((interestOps & readInterestOp) != 0) {
                    // only remove readInterestOp if needed
                    key.interestOps(interestOps & ~readInterestOp);
                }
            }

            final ChannelPipeline pipeline = pipeline();
            // FIXME: calculate size as in 3.x
            final ByteBuf byteBuf = alloc().ioBuffer(1024);
            boolean closed = false;
            boolean read = false;
            boolean firedChannelReadSuspended = false;
            try {
                expandReadBuffer(byteBuf);
                loop: for (;;) {
                    int localReadAmount = doReadBytes(byteBuf);
                    if (localReadAmount > 0) {
                        read = true;
                    } else if (localReadAmount < 0) {
                        closed = true;
                        break;
                    }

                    switch (expandReadBuffer(byteBuf)) {
                    case 0:
                        // Read all - stop reading.
                        break loop;
                    case 1:
                        // Keep reading until everything is read.
                        break;
                    case 2:
                        // Let the inbound handler drain the buffer and continue reading.
                        if (read) {
                            read = false;
                            pipeline.fireMessageReceived(byteBuf);
                            // clear the buffer after the event was fired
                            byteBuf.clear();
                        }
                        if (!config().isAutoRead()) {
                            // stop reading until next Channel.read() call
                            // See https://github.com/netty/netty/issues/1363
                            break loop;
                        }
                    }
                }
            } catch (Throwable t) {
                if (read) {
                    read = false;
                    pipeline.fireMessageReceived(byteBuf);
                }

                if (t instanceof IOException) {
                    closed = true;
                } else if (!closed) {
                    firedChannelReadSuspended = true;
                    pipeline.fireChannelReadSuspended();
                }
                pipeline().fireExceptionCaught(t);
            } finally {
                if (read) {
                    pipeline.fireMessageReceived(byteBuf);
                }

                if (closed) {
                    setInputShutdown();
                    if (isOpen()) {
                        if (Boolean.TRUE.equals(config().getOption(ChannelOption.ALLOW_HALF_CLOSURE))) {
                            key.interestOps(key.interestOps() & ~readInterestOp);
                            pipeline.fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE);
                        } else {
                            close(voidPromise());
                        }
                    }
                } else if (!firedChannelReadSuspended) {
                    pipeline.fireChannelReadSuspended();
                }
            }
        }
    }

    @Override
    protected int doWrite(MessageList<Object> msgs, int index) throws Exception {
        Object msg = msgs.get(index);

        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            for (int i = config().getWriteSpinCount() - 1; i >= 0; i --) {
                int localFlushedAmount = doWriteBytes(buf, i == 0);
                if (localFlushedAmount > 0 || !buf.isReadable()) {
                    break;
                }
            }
            // We may could optimize this to write multiple buffers at once (scattering)
            if (!buf.isReadable()) {
                buf.release();
                return 1;
            }
        } else if (msg instanceof FileRegion) {
            FileRegion region = (FileRegion) msg;

            for (int i = config().getWriteSpinCount() - 1; i >= 0; i --) {
                long localFlushedAmount = doWriteFileRegion(region, i == 0);
                if (localFlushedAmount == -1) {
                    checkEOF(region);
                    return 1;
                }
                if (localFlushedAmount > 0) {
                    break;
                }
            }
            if (region.transfered() >= region.count()) {
                return 1;
            }
        } else {
            throw new UnsupportedOperationException("Not support writing of message " + msg);
        }

        return index;
    }

    /**
     * Write a {@link FileRegion}
     *
     * @param region        the {@link FileRegion} from which the bytes should be written
     * @param lastSpin      {@code true} if this is the last write try
     * @return amount       the amount of written bytes
     * @throws Exception    thrown if an error accour
     */
    protected abstract long doWriteFileRegion(FileRegion region, boolean lastSpin) throws Exception;

    /**
     * Read bytes into the given {@link ByteBuf} and return the amount.
     */
    protected abstract int doReadBytes(ByteBuf buf) throws Exception;

    /**
     * Write bytes form the given {@link ByteBuf} to the underlying {@link java.nio.channels.Channel}.
     * @param buf           the {@link ByteBuf} from which the bytes should be written
     * @param lastSpin      {@code true} if this is the last write try
     * @return amount       the amount of written bytes
     * @throws Exception    thrown if an error accour
     */
    protected abstract int doWriteBytes(ByteBuf buf, boolean lastSpin) throws Exception;

}
