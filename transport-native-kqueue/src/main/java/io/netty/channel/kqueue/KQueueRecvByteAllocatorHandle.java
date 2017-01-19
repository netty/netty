/*
 * Copyright 2016 The Netty Project
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
package io.netty.channel.kqueue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelConfig;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.util.UncheckedBooleanSupplier;
import io.netty.util.internal.ObjectUtil;

import static java.lang.Math.max;
import static java.lang.Math.min;

final class KQueueRecvByteAllocatorHandle implements RecvByteBufAllocator.ExtendedHandle {
    private final RecvByteBufAllocator.ExtendedHandle delegate;
    private final UncheckedBooleanSupplier defaultMaybeMoreDataSupplier = new UncheckedBooleanSupplier() {
        @Override
        public boolean get() {
            return maybeMoreDataToRead();
        }
    };
    private boolean overrideGuess;
    private boolean readEOF;
    private long numberBytesPending;

    KQueueRecvByteAllocatorHandle(RecvByteBufAllocator.ExtendedHandle handle) {
        this.delegate = ObjectUtil.checkNotNull(handle, "handle");
    }

    @Override
    public int guess() {
        return overrideGuess ? guess0() : delegate.guess();
    }

    @Override
    public void reset(ChannelConfig config) {
        overrideGuess = ((KQueueChannelConfig) config).getRcvAllocTransportProvidesGuess();
        delegate.reset(config);
    }

    @Override
    public void incMessagesRead(int numMessages) {
        delegate.incMessagesRead(numMessages);
    }

    @Override
    public ByteBuf allocate(ByteBufAllocator alloc) {
        return overrideGuess ? alloc.ioBuffer(guess0()) : delegate.allocate(alloc);
    }

    @Override
    public void lastBytesRead(int bytes) {
        numberBytesPending = bytes < 0 ? 0 : max(0, numberBytesPending - bytes);
        delegate.lastBytesRead(bytes);
    }

    @Override
    public int lastBytesRead() {
        return delegate.lastBytesRead();
    }

    @Override
    public void attemptedBytesRead(int bytes) {
        delegate.attemptedBytesRead(bytes);
    }

    @Override
    public int attemptedBytesRead() {
        return delegate.attemptedBytesRead();
    }

    @Override
    public void readComplete() {
        delegate.readComplete();
    }

    @Override
    public boolean continueReading(UncheckedBooleanSupplier maybeMoreDataSupplier) {
        return delegate.continueReading(maybeMoreDataSupplier);
    }

    @Override
    public boolean continueReading() {
        // We must override the supplier which determines if there maybe more data to read.
        return delegate.continueReading(defaultMaybeMoreDataSupplier);
    }

    void readEOF() {
        readEOF = true;
    }

    void numberBytesPending(long numberBytesPending) {
        this.numberBytesPending = numberBytesPending;
    }

    boolean maybeMoreDataToRead() {
        /**
         * kqueue with EV_CLEAR flag set requires that we read until we consume "data" bytes
         * (see <a href="https://www.freebsd.org/cgi/man.cgi?kqueue">kqueue man</a>). However in order to
         * respect auto read we supporting reading to stop if auto read is off. If auto read is on we force reading to
         * continue to avoid a {@link StackOverflowError} between channelReadComplete and reading from the
         * channel. It is expected that the {@link #KQueueSocketChannel} implementations will track if all data was not
         * read, and will force a EVFILT_READ ready event.
         *
         * If EOF has been read we must read until we get an error.
         */
        return numberBytesPending != 0 || readEOF;
    }

    private int guess0() {
        return (int) min(numberBytesPending, Integer.MAX_VALUE);
    }
}
