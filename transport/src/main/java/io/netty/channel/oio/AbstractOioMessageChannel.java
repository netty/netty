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
package io.netty.channel.oio;

import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.RecvByteBufAllocator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for OIO which reads and writes objects from/to a Socket
 */
public abstract class AbstractOioMessageChannel extends AbstractOioChannel {

    private final List<Object> readBuf = new ArrayList<Object>();

    protected AbstractOioMessageChannel(Channel parent) {
        super(parent);
    }

    @Override
    protected void doRead() {
        final ChannelConfig config = config();
        if (!config.isAutoRead() && !isReadPending()) {
            // ChannelConfig.setAutoRead(false) was called in the meantime
            return;
        }
        // OIO reads are scheduled as a runnable object, the read is not pending as soon as the runnable is run.
        setReadPending(false);

        final ChannelPipeline pipeline = pipeline();
        final RecvByteBufAllocator.Handle allocHandle = unsafe().recvBufAllocHandle();
        allocHandle.reset(config);

        boolean closed = false;
        Throwable exception = null;
        try {
            do {
                // Perform a read.
                int localRead = doReadMessages(readBuf);
                if (localRead == 0) {
                    break;
                }
                if (localRead < 0) {
                    closed = true;
                    break;
                }

                allocHandle.incMessagesRead(localRead);
            } while (allocHandle.continueReading());
        } catch (Throwable t) {
            exception = t;
        }

        int size = readBuf.size();
        for (int i = 0; i < size; i ++) {
            pipeline.fireChannelRead(readBuf.get(i));
        }
        readBuf.clear();
        allocHandle.readComplete();
        pipeline.fireChannelReadComplete();

        if (exception != null) {
            if (exception instanceof IOException) {
                closed = true;
            }

            pipeline.fireExceptionCaught(exception);
        }

        if (closed) {
            if (isOpen()) {
                unsafe().close(unsafe().voidPromise());
            }
        } else if (allocHandle.lastBytesRead() == 0 && isActive()) {
            // If the read amount was 0 and the channel is still active we need to trigger a new read()
            // as otherwise we will never try to read again and the user will never know.
            // Just call read() is ok here as it will be submitted to the EventLoop as a task and so we are
            // able to process the rest of the tasks in the queue first.
            //
            // See https://github.com/netty/netty/issues/2404
            read();
        }
    }

    /**
     * Read messages into the given array and return the amount which was read.
     */
    protected abstract int doReadMessages(List<Object> msgs) throws Exception;
}
