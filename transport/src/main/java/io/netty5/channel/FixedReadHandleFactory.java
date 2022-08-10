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
package io.netty5.channel;

import static io.netty5.util.internal.ObjectUtil.checkPositive;

/**
 * The {@link ReadHandleFactory} that always yields the same buffer
 * size prediction. This handle ignores the feedback from the I/O thread.
 */
public class FixedReadHandleFactory extends MaxMessagesReadHandleFactory {
    private final int bufferSize;

    private static final class ReadHandleImpl extends MaxMessageReadHandle {
        private final int bufferSize;

        ReadHandleImpl(int maxMessagesPerRead, int bufferSize) {
            super(maxMessagesPerRead);
            this.bufferSize = bufferSize;
        }

        @Override
        public int estimatedBufferCapacity() {
            return bufferSize;
        }
    }

    /**
     * Creates a new predictor that always returns the same prediction of
     * the specified buffer size.
     */
    public FixedReadHandleFactory(int bufferSize) {
        this.bufferSize = checkPositive(bufferSize, "bufferSize");
    }

    public FixedReadHandleFactory(int maxMessagesPerRead, int bufferSize) {
        super(maxMessagesPerRead);
        this.bufferSize = checkPositive(bufferSize, "bufferSize");
    }

    @Override
    public MaxMessageReadHandle newMaxMessageHandle(int maxMessagesPerRead) {
        return new ReadHandleImpl(maxMessagesPerRead, bufferSize);
    }
}
