/*
 * Copyright 2020 The Netty Project
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
package io.netty.channel.uring;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.unix.PreferredDirectByteBufAllocator;
import io.netty.util.UncheckedBooleanSupplier;

final class IOUringRecvByteAllocatorHandle extends RecvByteBufAllocator.DelegatingHandle
        implements RecvByteBufAllocator.ExtendedHandle {
    private final PreferredDirectByteBufAllocator preferredDirectByteBufAllocator =
            new PreferredDirectByteBufAllocator();

    IOUringRecvByteAllocatorHandle(RecvByteBufAllocator.ExtendedHandle handle) {
        super(handle);
    }

    private boolean rdHupReceived;

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
        // If we received an POLLRDHUP we need to continue draining the input until there is nothing left.
        return super.continueReading() || rdHupReceived;
    }

    @Override
    public boolean continueReading(UncheckedBooleanSupplier maybeMoreDataSupplier) {
        // If we received an POLLRDHUP we need to continue draining the input until there is nothing left.
        return ((RecvByteBufAllocator.ExtendedHandle) delegate()).continueReading(maybeMoreDataSupplier)
                || rdHupReceived;
    }
}
