/*
 * Copyright 2014 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.channel.ChannelOutboundBuffer;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.nio.ByteBuffer;

/**
 * Special {@link ChannelOutboundBuffer} implementation which allows to obtain a {@link IovArray}
 * and so doing gathering writes without the need to create a {@link ByteBuffer} internally. This reduce
 * GC pressure a lot.
 */
final class EpollChannelOutboundBuffer extends ChannelOutboundBuffer {
    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(EpollChannelOutboundBuffer.class);

    EpollChannelOutboundBuffer(EpollSocketChannel channel) {
        super(channel);
    }

    /**
     * Returns a {@link IovArray} if the currently pending messages.
     * <p>
     * Note that the returned {@link IovArray} is reused and thus should not escape
     * {@link io.netty.channel.AbstractChannel#doWrite(ChannelOutboundBuffer)}.
     */
    IovArray iovArray() {
       IovArray array = IovArray.get();
        try {
            forEachFlushedMessage(array);
        } catch (Exception e) {
            e.printStackTrace();
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error("Error during filling IovArray", e);
            }
        }
        return array;
    }
}
