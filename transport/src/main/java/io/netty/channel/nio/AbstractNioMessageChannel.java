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

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.MessageList;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

/**
 * {@link AbstractNioChannel} base class for {@link Channel}s that operate on messages.
 */
public abstract class AbstractNioMessageChannel extends AbstractNioChannel {

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
            boolean closed = false;
            MessageList<Object> msgBuf = MessageList.newInstance();
            Throwable exception = null;
            loop: for (;;) {
                try {
                    for (;;) {
                        int localRead = doReadMessages(msgBuf);
                        if (localRead == 0) {
                            break loop;
                        }
                        if (localRead < 0) {
                            closed = true;
                            break loop;
                        }
                        if (!config().isAutoRead()) {
                            break loop;
                        }
                    }
                } catch (Throwable t) {
                    exception = t;
                    break;
                }
            }

            pipeline.fireMessageReceived(msgBuf);

            if (exception != null) {
                if (exception instanceof IOException) {
                    closed = true;
                }

                pipeline().fireExceptionCaught(exception);
            }

            if (closed) {
                if (isOpen()) {
                    close(voidPromise());
                }
            } else {
                pipeline.fireChannelReadSuspended();
            }
        }
    }

    @Override
    protected int doWrite(MessageList<Object> msgs, int index) throws Exception {
        final int writeSpinCount = config().getWriteSpinCount() - 1;
        for (int i = writeSpinCount; i >= 0; i --) {
            int written = doWriteMessages(msgs, index, i == 0);
            if (written > 0) {
                return written;
            }
        }
        return 0;
    }

    /**
     * Read messages into the given array and return the amount which was read.
     */
    protected abstract int doReadMessages(MessageList<Object> buf) throws Exception;

    /**
     * Write messages to the underlying {@link java.nio.channels.Channel}.
     * @param msg           Object to write
     * @param lastSpin      {@code true} if this is the last write try
     * @return written      the amount of written messages
     * @throws Exception    thrown if an error accour
     */
    protected abstract int doWriteMessages(MessageList<Object> msg, int index, boolean lastSpin) throws Exception;
}
