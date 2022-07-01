/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty5.channel;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.CompositeBuffer;
import io.netty5.util.concurrent.Promise;

import static java.util.Objects.requireNonNull;

/**
 * A FIFO queue of bytes where producers add bytes by repeatedly adding {@link Buffer} and consumers take bytes in
 * arbitrary lengths. This allows producers to add lots of small buffers and the consumer to take all the bytes out in a
 * single buffer. Conversely, the producer may add larger buffers and the consumer could take the bytes in many small
 * buffers.
 *
 * <p>Bytes are added and removed with promises. If the last byte of a buffer added with a promise is removed then
 * that promise will complete when the promise passed to {@link #remove} completes.
 *
 * <p>This functionality is useful for aggregating or partitioning writes into fixed size buffers for framing protocols
 * such as HTTP2.
 */
public final class CoalescingBufferQueue extends AbstractCoalescingBufferQueue {
    private final Channel channel;

    public CoalescingBufferQueue(Channel channel) {
        this(channel, 4);
    }

    public CoalescingBufferQueue(Channel channel, int initSize) {
        super(initSize);
        this.channel = requireNonNull(channel, "channel");
    }

    /**
     * Remove a {@link Buffer} from the queue with the specified number of bytes. Any added buffer whose bytes are fully
     * consumed during removal will have it's promise completed when the passed aggregate {@link Promise} completes.
     *
     * @param bytes the maximum number of readable bytes in the returned {@link Buffer}, if {@code bytes} is greater
     *              than {@link #readableBytes()} then a buffer of length {@link #readableBytes()} is returned.
     * @param aggregatePromise used to aggregate the promises and listeners for the constituent buffers.
     * @return a {@link Buffer} composed of the enqueued buffers.
     */
    public Buffer remove(int bytes, Promise<Void> aggregatePromise) {
        return remove(channel.bufferAllocator(), bytes, aggregatePromise);
    }

    /**
     *  Release all buffers in the queue and complete all listeners and promises.
     */
    public void releaseAndFailAll(Throwable cause) {
        releaseAndFailAll(channel, cause);
    }

    @Override
    protected Buffer compose(BufferAllocator alloc, Buffer cumulation, Buffer next) {
        if (cumulation instanceof CompositeBuffer) {
            CompositeBuffer composite = (CompositeBuffer) cumulation;
            composite.extendWith(next.send());
            return composite;
        }
        return composeIntoComposite(alloc, cumulation, next);
    }

    @Override
    protected Buffer removeEmptyValue() {
        return BufferAllocator.offHeapUnpooled().allocate(0);
    }
}
