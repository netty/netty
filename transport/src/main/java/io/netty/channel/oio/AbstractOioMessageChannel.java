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
import io.netty.channel.ChannelPipeline;

import java.io.IOException;
import java.net.Socket;

/**
 * Abstract base class for OIO which reads and writes objects from/to a Socket
 */
public abstract class AbstractOioMessageChannel extends AbstractOioChannel {
    // maximal read is 16 messages at once
    private final Object[] msgBuf = new Object[16];

    /**
     * @see AbstractOioChannel#AbstractOioChannel(Channel, Integer)
     */
    protected AbstractOioMessageChannel(Channel parent, Integer id) {
        super(parent, id);
    }

    @Override
    protected void doRead() {
        final ChannelPipeline pipeline = pipeline();
        boolean closed = false;
        boolean read = false;
        boolean firedChannelReadSuspended = false;
        int localReadAmount = 0;
        try {
            localReadAmount = doReadMessages(msgBuf, 0);
            if (localReadAmount > 0) {
                read = true;
            } else if (localReadAmount < 0) {
                closed = true;
            }
        } catch (Throwable t) {
            if (read) {
                read = false;
                pipeline.fireMessageReceived(msgBuf, 0, localReadAmount);
            }
            firedChannelReadSuspended = true;
            pipeline.fireChannelReadSuspended();
            pipeline.fireExceptionCaught(t);
            if (t instanceof IOException) {
                unsafe().close(unsafe().voidPromise());
            }
        } finally {
            if (read) {
                pipeline.fireMessageReceived(msgBuf, 0, localReadAmount);
            }
            if (!firedChannelReadSuspended) {
                pipeline.fireChannelReadSuspended();
            }
            if (closed && isOpen()) {
                unsafe().close(unsafe().voidPromise());
            }
        }
    }

    @Override
    protected int doWrite(Object[] msgs, int index, int length) throws Exception {
        int written = doWriteMessages(msgs, index, length);
        if (written > 0) {
            return index + written;
        }
        return index;
    }
    /**
     * Read messages into the given array and return the amount which was read.
     */
    protected abstract int doReadMessages(Object[] buf, int index) throws Exception;

    /**
     * Write messages to the underlying {@link Socket}.
     *
     * @param msg           Object to write
     * @return written      the amount of written messages
     * @throws Exception    thrown if an error accour
     */
    protected abstract int doWriteMessages(Object[] msg, int index, int length) throws Exception;
}
