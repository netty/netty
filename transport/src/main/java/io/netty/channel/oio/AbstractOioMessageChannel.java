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
        final ChannelPipeline pipeline = pipeline();
        boolean closed = false;
        final ChannelConfig config = config();
        final int maxMessagesPerRead = config.getMaxMessagesPerRead();

        Throwable exception = null;
        try {
            for (;;) {
                int localRead = doReadMessages(readBuf);
                if (localRead == 0) {
                    break;
                }
                if (localRead < 0) {
                    closed = true;
                    break;
                }

                if (readBuf.size() >= maxMessagesPerRead || !config.isAutoRead()) {
                    break;
                }
            }
        } catch (Throwable t) {
            exception = t;
        }

        int size = readBuf.size();
        for (int i = 0; i < size; i ++) {
            pipeline.fireChannelRead(readBuf.get(i));
        }
        readBuf.clear();
        pipeline.fireChannelReadComplete();

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
        }
    }

    /**
     * Read messages into the given array and return the amount which was read.
     */
    protected abstract int doReadMessages(List<Object> msgs) throws Exception;
}
