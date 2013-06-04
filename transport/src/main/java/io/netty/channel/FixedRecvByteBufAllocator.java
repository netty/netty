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
package io.netty.channel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;


/**
 * The {@link RecvByteBufAllocator} that always yields the same buffer
 * size prediction.  This predictor ignores the feed back from the I/O thread.
 */
public class FixedRecvByteBufAllocator implements RecvByteBufAllocator {

    private static final class HandleImpl implements Handle {

        private final int bufferSize;

        HandleImpl(int bufferSize) {
            this.bufferSize = bufferSize;
        }

        @Override
        public ByteBuf allocate(ByteBufAllocator alloc) {
            return alloc.ioBuffer(bufferSize);
        }

        @Override
        public int guess() {
            return bufferSize;
        }

        @Override
        public void record(int actualReadBytes) { }
    }

    private final Handle handle;

    /**
     * Creates a new predictor that always returns the same prediction of
     * the specified buffer size.
     */
    public FixedRecvByteBufAllocator(int bufferSize) {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException(
                    "bufferSize must greater than 0: " + bufferSize);
        }

        handle = new HandleImpl(bufferSize);
    }

    @Override
    public Handle newHandle() {
        return handle;
    }
}
