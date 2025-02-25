/*
 * Copyright 2024 The Netty Project
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
package io.netty.channel.uring;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelConfig;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.unix.PreferredDirectByteBufAllocator;
import io.netty.util.UncheckedBooleanSupplier;

final class IoUringRecvByteAllocatorHandle extends RecvByteBufAllocator.DelegatingHandle
        implements RecvByteBufAllocator.ExtendedHandle {
    private final PreferredDirectByteBufAllocator preferredDirectByteBufAllocator =
            new PreferredDirectByteBufAllocator();

    // We need to continue reading as long as we received something when using io_uring. Otherwise
    // we will not be able to batch things in an efficient way.
    private final UncheckedBooleanSupplier defaultSupplier = () -> lastBytesRead() > 0;

    IoUringRecvByteAllocatorHandle(RecvByteBufAllocator.ExtendedHandle handle) {
        super(handle);
    }

    private boolean firstRead;
    private boolean rdHupReceived;
    private boolean readComplete;

    @Override
    public void reset(ChannelConfig config) {
        super.reset(config);
        readComplete = false;
        firstRead = true;
    }

    void rdHupReceived() {
        this.rdHupReceived = true;
    }

    @Override
    public ByteBuf allocate(ByteBufAllocator alloc) {
        // We need to ensure we always allocate a direct ByteBuf as we can only use a direct buffer to read via JNI.
        preferredDirectByteBufAllocator.updateAllocator(alloc);
        return delegate().allocate(preferredDirectByteBufAllocator);
    }

    @Override
    public boolean continueReading() {
        // Ensure we use the our own supplier that will take care of reading data until there is nothing left.
        return continueReading(defaultSupplier);
    }

    @Override
    public boolean continueReading(UncheckedBooleanSupplier maybeMoreDataSupplier) {
        // If we received an POLLRDHUP we need to continue draining the input until there is nothing left.
        return ((RecvByteBufAllocator.ExtendedHandle) delegate()).continueReading(maybeMoreDataSupplier)
                || rdHupReceived;
    }

    public boolean isFirstRead() {
        return firstRead;
    }

    @Override
    public void readComplete() {
        super.readComplete();
        readComplete = true;
    }

    boolean isReadComplete() {
        return readComplete;
    }

    @Override
    public void lastBytesRead(int bytes) {
        firstRead = false;
        super.lastBytesRead(bytes);
    }

    @Override
    public void incMessagesRead(int numMessages) {
        firstRead = false;
        super.incMessagesRead(numMessages);
    }
}
