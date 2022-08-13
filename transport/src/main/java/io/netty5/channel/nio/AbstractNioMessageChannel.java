/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.channel.nio;

import io.netty5.channel.Channel;
import io.netty5.channel.ChannelOutboundBuffer;
import io.netty5.channel.ChannelShutdownDirection;
import io.netty5.channel.EventLoop;
import io.netty5.channel.ReadHandleFactory;

import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

/**
 * {@link AbstractNioChannel} base class for {@link Channel}s that operate on messages.
 */
public abstract class AbstractNioMessageChannel<P extends Channel, L extends SocketAddress, R extends SocketAddress>
        extends AbstractNioChannel<P, L, R> {

    /**
     * @see AbstractNioChannel#AbstractNioChannel(Channel, EventLoop,
     * boolean, ReadHandleFactory, SelectableChannel, int)
     */
    protected AbstractNioMessageChannel(P parent, EventLoop eventLoop, boolean supportingDisconnect,
                                        ReadHandleFactory defaultReadHandleFactory,
                                        SelectableChannel ch, int readInterestOp) {
        super(parent, eventLoop, supportingDisconnect, defaultReadHandleFactory, ch, readInterestOp);
    }

    @Override
    protected final boolean doReadNow(ReadSink readSink) throws Exception {
        boolean closed = false;
        do {
            int localRead = doReadMessages(readSink);
            if (localRead == 0) {
                break;
            }
            if (localRead < 0) {
                closed = true;
                break;
            }
        } while (!isShutdown(ChannelShutdownDirection.Inbound));

        return closed;
    }

    @Override
    protected final void doWrite(ChannelOutboundBuffer in) throws Exception {
        final SelectionKey key = selectionKey();
        if (key == null) {
            return;
        }
        final int interestOps = key.interestOps();

        int maxMessagesPerWrite = getMaxMessagesPerWrite();
        while (maxMessagesPerWrite > 0) {
            Object msg = in.current();
            if (msg == null) {
                break;
            }
            try {
                boolean done = false;
                for (int i = getWriteSpinCount() - 1; i >= 0; i--) {
                    if (doWriteMessage(msg, in)) {
                        done = true;
                        break;
                    }
                }

                if (done) {
                    maxMessagesPerWrite--;
                    in.remove();
                } else {
                    break;
                }
            } catch (Exception e) {
                if (continueOnWriteError()) {
                    maxMessagesPerWrite--;
                    in.remove(e);
                } else {
                    throw e;
                }
            }
        }
        if (in.isEmpty()) {
            // Wrote all messages.
            if ((interestOps & SelectionKey.OP_WRITE) != 0) {
                key.interestOps(interestOps & ~SelectionKey.OP_WRITE);
            }
        } else {
            // Did not write all messages.
            if ((interestOps & SelectionKey.OP_WRITE) == 0) {
                key.interestOps(interestOps | SelectionKey.OP_WRITE);
            }
        }
    }

    /**
     * Returns {@code true} if we should continue the write loop on a write error.
     */
    protected boolean continueOnWriteError() {
        return false;
    }

    /**
     * Read messages into the given array and return the amount which was read.
     */
    protected abstract int doReadMessages(ReadSink readSink) throws Exception;

    /**
     * Write a message to the underlying {@link java.nio.channels.Channel}.
     *
     * @return {@code true} if and only if the message has been written
     */
    protected abstract boolean doWriteMessage(Object msg, ChannelOutboundBuffer in) throws Exception;
}
