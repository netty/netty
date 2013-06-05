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
import io.netty.channel.MessageList;

import java.io.IOException;

/**
 * Abstract base class for OIO which reads and writes objects from/to a Socket
 */
public abstract class AbstractOioMessageChannel extends AbstractOioChannel {

    protected AbstractOioMessageChannel(Channel parent, Integer id) {
        super(parent, id);
    }

    @Override
    protected void doRead() {
        final ChannelPipeline pipeline = pipeline();
        boolean closed = false;
        MessageList<Object> msgs = MessageList.newInstance();
        Throwable exception = null;
        try {
            int localReadAmount = doReadMessages(msgs);
            if (localReadAmount < 0) {
                closed = true;
            }
        } catch (Throwable t) {
            exception = t;
        }

        pipeline.fireMessageReceived(msgs);

        if (exception != null) {
            if (exception instanceof IOException) {
                closed = true;
            }

            pipeline().fireExceptionCaught(exception);
        }

        if (closed) {
            if (isOpen()) {
                unsafe().close(unsafe().voidPromise());
            }
        } else {
            pipeline.fireChannelReadSuspended();
        }
    }

    /**
     * Read messages into the given array and return the amount which was read.
     */
    protected abstract int doReadMessages(MessageList<Object> msgs) throws Exception;
}
