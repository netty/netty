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

import io.netty.buffer.MessageBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

/**
 * {@link AbstractNioChannel} base class for {@link Channel}s that operate on messages.
 */
public abstract class AbstractNioMessageChannel extends AbstractNioChannel {

    // Hard coded for now.
    private static final int READ_BATCH_SIZE = 16;

    /**
     * @see {@link AbstractNioChannel#AbstractNioChannel(Channel, Integer, SelectableChannel, int)}
     */
    protected AbstractNioMessageChannel(
            Channel parent, Integer id, SelectableChannel ch, int readInterestOp) {
        super(parent, id, ch, readInterestOp);
    }

    @Override
    protected AbstractNioUnsafe newUnsafe() {
        return new NioMessageUnsafe();
    }

    private final class NioMessageUnsafe extends AbstractNioUnsafe {
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
            final MessageBuf<Object> msgBuf = pipeline.inboundMessageBuffer();
            boolean closed = false;
            boolean read = false;
            boolean firedChannelReadSuspended = false;
            loop: for (;;) {
                int reads = 0;

                try {
                    for (;;) {
                        int localReadAmount = doReadMessages(msgBuf);
                        if (localReadAmount > 0) {
                            read = true;
                        } else if (localReadAmount == 0) {
                            break loop;
                        } else if (localReadAmount < 0) {
                            closed = true;
                            break loop;
                        }

                        if (reads++ > READ_BATCH_SIZE) {
                            break;
                        }
                        if (!config().isAutoRead()) {
                            break loop;
                        }
                    }
                } catch (Throwable t) {
                    if (read) {
                        read = false;
                        pipeline.fireInboundBufferUpdated();
                    }

                    if (t instanceof IOException) {
                        closed = true;
                    } else if (!closed) {
                        firedChannelReadSuspended = true;
                        pipeline.fireChannelReadSuspended();
                    }

                    pipeline().fireExceptionCaught(t);

                    // break the loop now
                    break;
                } finally {
                    if (read) {
                        pipeline.fireInboundBufferUpdated();
                    }
                    if (closed && isOpen()) {
                        close(voidPromise());
                    } else if (!firedChannelReadSuspended) {
                        pipeline.fireChannelReadSuspended();
                    }
                }
            }
        }
    }

    @Override
    protected void doFlushMessageBuffer(MessageBuf<Object> buf) throws Exception {
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

    /**
     * Read messages into the given {@link MessageBuf} and return the amount.
     */
    protected abstract int doReadMessages(MessageBuf<Object> buf) throws Exception;

    /**
     * Write messages form the given {@link MessageBuf} to the underlying {@link java.nio.channels.Channel}.
     * @param buf           the {@link MessageBuf} from which the bytes should be written
     * @param lastSpin      {@code true} if this is the last write try
     * @return amount       the amount of written bytes
     * @throws Exception    thrown if an error accour
     */
    protected abstract int doWriteMessages(MessageBuf<Object> buf, boolean lastSpin) throws Exception;
}
