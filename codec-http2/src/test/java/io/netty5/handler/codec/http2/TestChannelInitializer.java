/*
 * Copyright 2016 The Netty Project
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
package io.netty5.handler.codec.http2;

import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ReadHandleFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Channel initializer useful in tests.
 */
public class TestChannelInitializer extends ChannelInitializer<Channel> {
    ChannelHandler handler;
    AtomicInteger maxReads;

    @Override
    public boolean isSharable() {
        return true;
    }

    @Override
    public void initChannel(Channel channel) {
        if (handler != null) {
            channel.pipeline().addLast(handler);
            handler = null;
        }
        if (maxReads != null) {
            channel.setOption(ChannelOption.READ_HANDLE_FACTORY, new TestNumReadsReadHandleFactory(maxReads));
        }
    }

    /**
     * Designed to read a single byte at a time to control the number of reads done at a fine granularity.
     */
    static final class TestNumReadsReadHandleFactory implements ReadHandleFactory {
        private final AtomicInteger numReads;
        private TestNumReadsReadHandleFactory(AtomicInteger numReads) {
            this.numReads = numReads;
        }

        @Override
        public ReadHandle newHandle() {
            return new ReadHandle() {
                private int numMessagesRead;

                @Override
                public int estimatedBufferCapacity() {
                    return 1; // only ever allocate buffers of size 1 to ensure the number of reads is controlled.
                }

                @Override
                public void lastRead(int attemptedBytesRead, int actualBytesRead, int numMessagesRead) {
                    this.numMessagesRead += numMessagesRead;
                }

                @Override
                public boolean continueReading(boolean autoRead) {
                    return numMessagesRead < numReads.get();
                }

                @Override
                public void readComplete() {
                    numMessagesRead = 0;
                }
            };
        }
    }
}
