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
package io.netty5.channel.kqueue;

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.ByteBufAllocator;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.DefaultBufferAllocators;
import io.netty5.buffer.api.StandardAllocationTypes;
import io.netty5.channel.ChannelConfig;
import io.netty5.channel.RecvBufferAllocator.DelegatingHandle;
import io.netty5.channel.RecvBufferAllocator.Handle;
import io.netty5.channel.unix.PreferredDirectByteBufAllocator;
import io.netty5.util.UncheckedBooleanSupplier;

import static java.lang.Math.max;
import static java.lang.Math.min;

final class KQueueRecvBufferAllocatorHandle extends DelegatingHandle {
    private final PreferredDirectByteBufAllocator preferredDirectByteBufAllocator =
            new PreferredDirectByteBufAllocator();

    private final UncheckedBooleanSupplier defaultMaybeMoreDataSupplier = this::maybeMoreDataToRead;
    private boolean overrideGuess;
    private boolean readEOF;
    private long numberBytesPending;

    KQueueRecvBufferAllocatorHandle(Handle handle) {
        super(handle);
    }

    @Override
    public int guess() {
        return overrideGuess ? guess0() : delegate().guess();
    }

    @Override
    public void reset(ChannelConfig config) {
        overrideGuess = ((KQueueChannelConfig) config).getRcvAllocTransportProvidesGuess();
        delegate().reset(config);
    }

    @Override
    public ByteBuf allocate(ByteBufAllocator alloc) {
        // We need to ensure we always allocate a direct ByteBuf as we can only use a direct buffer to read via JNI.
        preferredDirectByteBufAllocator.updateAllocator(alloc);
        return overrideGuess ? preferredDirectByteBufAllocator.ioBuffer(guess0()) :
                delegate().allocate(preferredDirectByteBufAllocator);
    }

    @Override
    public Buffer allocate(BufferAllocator alloc) {
        // We need to ensure we always allocate a direct ByteBuf as we can only use a direct buffer to read via JNI.
        if (alloc.getAllocationType() != StandardAllocationTypes.OFF_HEAP) {
            alloc = DefaultBufferAllocators.offHeapAllocator();
        }
        if (overrideGuess) {
            return alloc.allocate(guess0());
        }
        return super.allocate(alloc);
    }

    @Override
    public void lastBytesRead(int bytes) {
        numberBytesPending = bytes < 0 ? 0 : max(0, numberBytesPending - bytes);
        delegate().lastBytesRead(bytes);
    }

    @Override
    public boolean continueReading() {
        // We must override the supplier which determines if there maybe more data to read.
        return continueReading(defaultMaybeMoreDataSupplier);
    }

    void readEOF() {
        readEOF = true;
    }

    boolean isReadEOF() {
        return readEOF;
    }

    void numberBytesPending(long numberBytesPending) {
        this.numberBytesPending = numberBytesPending;
    }

    /**
     * kqueue with EV_CLEAR flag set requires that we read until we consume "data" bytes (see kqueue man:
     * https://www.freebsd.org/cgi/man.cgi?kqueue). However, in order to respect auto read we support reading to stop if
     * auto read is off. If auto read is on we force reading to continue to avoid a {@link StackOverflowError} between
     * channelReadComplete and reading from the channel. It is expected that the {@link KQueueSocketChannel}
     * implementations will track if all data was not read, and will force a EVFILT_READ ready event.
     * <p>
     * It is assumed EOF is handled externally by checking {@link #isReadEOF()}.
     */
    boolean maybeMoreDataToRead() {
        return numberBytesPending != 0;
    }

    private int guess0() {
        return (int) min(numberBytesPending, Integer.MAX_VALUE);
    }
}
