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
import io.netty.channel.ChannelOutboundBuffer.MessageProcessor;
import io.netty.channel.unix.Buffer;
import io.netty.util.internal.PlatformDependent;

import java.nio.ByteBuffer;
import java.util.Stack;

import static io.netty.channel.unix.Limits.*;


final class IovecArrayPool implements MessageProcessor {
    private static final int ADDRESS_SIZE = Buffer.addressSize();
    private static final int IOV_SIZE = 2 * ADDRESS_SIZE;

    //Todo configurable
    private static int poolSize = 40;

    //Todo IOVEC entries shoule be lower IOVEMAX
    private static final int IOV_ENTRIES = 1024;

    private static final int IOVEC_ARRAY_SIZE = IOV_SIZE * IOV_ENTRIES;
    private static final int CAPACITY = IOVEC_ARRAY_SIZE * poolSize;

    private final Stack<Long> remainingIovec;
    private long maxBytes = SSIZE_MAX;

    private int count;
    private long size;
    private long currentIovecMemoryAddress;

    private final ByteBuffer iovecArrayMemory;
    private final long iovecArrayMemoryAddress;

    IovecArrayPool() {
        //setup array
        remainingIovec = new Stack<Long>();

        iovecArrayMemory = Buffer.allocateDirectWithNativeOrder(CAPACITY);
        iovecArrayMemoryAddress = Buffer.memoryAddress(iovecArrayMemory);

        for (long i = 0; i < poolSize; i++) {
            remainingIovec.push(i);
        }
    }

    //Todo better naming
    long createNewIovecMemoryAddress() {
        //clear
        size = 0;
        count = 0;

        if (remainingIovec.empty()) {
            // Todo allocate new Memory
            return -1;
        }
        long index = remainingIovec.pop();

        currentIovecMemoryAddress = index * IOVEC_ARRAY_SIZE + iovecArrayMemoryAddress;

        return currentIovecMemoryAddress;
    }

    //Todo error handling
    void releaseIovec(long iovecAddress) {
        long index = (iovecAddress - iovecArrayMemoryAddress) / IOVEC_ARRAY_SIZE;

        remainingIovec.push(index);
    }

    private boolean add(ByteBuf buf, int offset, int len) {
        if (count == IOV_ENTRIES) {
            // No more room!
            return false;
        } else if (buf.nioBufferCount() == 1) {
            if (len == 0) {
                return true;
            }
            if (buf.hasMemoryAddress()) {
                return add(buf.memoryAddress() + offset, len);
            } else {
                return false;
            }
        } else {
            ByteBuffer[] buffers = buf.nioBuffers(offset, len);
            for (ByteBuffer nioBuffer : buffers) {
                final int remaining = nioBuffer.remaining();
                if (remaining != 0 &&
                    (!add(Buffer.memoryAddress(nioBuffer) + nioBuffer.position(), remaining) || count ==
                                                                                                IOV_ENTRIES)) {
                    return false;
                }
            }
            return true;
        }
    }

    private boolean add(long addr, int len) {
        assert addr != 0;

        // If there is at least 1 entry then we enforce the maximum bytes. We want to accept at least one entry so we
        // will attempt to write some data and make progress.
        if (maxBytes - len < size && count > 0) {
            // If the size + len will overflow SSIZE_MAX we stop populate the IovArray. This is done as linux
            //  not allow to write more bytes then SSIZE_MAX with one writev(...) call and so will
            // return 'EINVAL', which will raise an IOException.
            //
            // See also:
            // - http://linux.die.net/man/2/writev
            return false;
        }
        final int baseOffset = idx(count);
        final int lengthOffset = baseOffset + ADDRESS_SIZE;

        size += len;
        ++count;

        if (ADDRESS_SIZE == 8) {
            // 64bit
            if (PlatformDependent.hasUnsafe()) {
                PlatformDependent.putLong(baseOffset + currentIovecMemoryAddress, addr);
                PlatformDependent.putLong(lengthOffset + currentIovecMemoryAddress, len);
            }
        }
        return true;
    }

    @Override
    public boolean processMessage(Object msg) {
        if (msg instanceof ByteBuf) {
            ByteBuf buffer = (ByteBuf) msg;
            return add(buffer, buffer.readerIndex(), buffer.readableBytes());
        }
        return false;
    }

    int count() {
        return count;
    }

    private static int idx(int index) {
        return IOV_SIZE * index;
    }

    void release() {
        Buffer.free(iovecArrayMemory);
    }
}
