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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ThreadLocalPooledDirectByteBuf;
import io.netty.channel.AbstractChannel;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.util.Recycler;

import java.util.Arrays;

final class EpollChannelOutboundBuffer extends ChannelOutboundBuffer {
    private AddressEntry[] addresses;
    private int addressCount;
    private long addressSize;
    private static final Recycler<EpollChannelOutboundBuffer> RECYCLER = new Recycler<EpollChannelOutboundBuffer>() {
        @Override
        protected EpollChannelOutboundBuffer newObject(Handle<EpollChannelOutboundBuffer> handle) {
            return new EpollChannelOutboundBuffer(handle);
        }
    };

    static EpollChannelOutboundBuffer newInstance(AbstractChannel channel) {
        EpollChannelOutboundBuffer buffer = RECYCLER.get();
        buffer.channel = channel;
        return buffer;
    }

    private EpollChannelOutboundBuffer(Recycler.Handle<? extends ChannelOutboundBuffer> handle) {
        super(handle);
        addresses = new AddressEntry[INITIAL_CAPACITY];
    }

    @Override
    protected Object message(Object msg) {
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            if (!buf.hasMemoryAddress()) {
                int readableBytes = buf.readableBytes();
                ByteBufAllocator alloc = channel.alloc();
                if (alloc.isDirectBufferPooled() || ThreadLocalPooledDirectByteBuf.threadLocalDirectBufferSize <= 0) {
                    ByteBuf directBuf = alloc.directBuffer(readableBytes);
                    directBuf.writeBytes(buf, buf.readerIndex(), readableBytes);
                    safeRelease(buf);
                    return directBuf;
                } else {
                    ByteBuf directBuf = ThreadLocalPooledDirectByteBuf.newInstance();
                    directBuf.writeBytes(buf, buf.readerIndex(), readableBytes);
                    safeRelease(buf);
                    return directBuf;
                }
            }
        }
        return msg;
    }

    public AddressEntry[] memoryAddresses() {
        long addressSize = 0;
        int addressCount = 0;
        final Entry[] buffer = entries();
        final int mask = buffer.length - 1;
        AddressEntry[] addresses = this.addresses;
        Object m;
        int unflushed = unflushed();
        int i = flushed();
        while (i != unflushed && (m = buffer[i].msg()) != null) {
            if (!(m instanceof ByteBuf)) {
                this.addressCount = 0;
                this.addressSize = 0;
                return null;
            }

            AddressEntry entry = (AddressEntry) buffer[i];

            if (!entry.isCancelled()) {
                ByteBuf buf = (ByteBuf) m;
                final int readerIndex = buf.readerIndex();
                final int readableBytes = buf.writerIndex() - readerIndex;

                if (readableBytes > 0) {
                    addressSize += readableBytes;
                    int neededSpace = addressCount + 1;
                    if (neededSpace > addresses.length) {
                        this.addresses = addresses =
                                expandAddressesArray(addresses, neededSpace, addressCount);
                    }
                    entry.memoryAddress = buf.memoryAddress();
                    entry.readerIndex = buf.readerIndex();
                    entry.writerIndex = buf.writerIndex();

                    addresses[addressCount ++] = entry;
                }
            }

            i = i + 1 & mask;
        }
        this.addressCount = addressCount;
        this.addressSize = addressSize;

        return addresses;
    }

    private static AddressEntry[] expandAddressesArray(AddressEntry[] array, int neededSpace, int size) {
        int newCapacity = array.length;
        do {
            // double capacity until it is big enough
            // See https://github.com/netty/netty/issues/1890
            newCapacity <<= 1;

            if (newCapacity < 0) {
                throw new IllegalStateException();
            }

        } while (neededSpace > newCapacity);

        AddressEntry[] newArray = new AddressEntry[newCapacity];
        System.arraycopy(array, 0, newArray, 0, size);

        return newArray;
    }

    public int addressCount() {
        return addressCount;
    }

    public long addressSize() {
        return addressSize;
    }

    @Override
    public void recycle() {
        if (addresses.length > INITIAL_CAPACITY) {
            addresses = new AddressEntry[INITIAL_CAPACITY];
        } else {
            // null out the nio buffers array so the can be GC'ed
            // https://github.com/netty/netty/issues/1763
            Arrays.fill(addresses, null);
        }
        super.recycle();
    }

    @Override
    protected AddressEntry newEntry() {
        return new AddressEntry();
    }

    static class AddressEntry extends Entry {
        long memoryAddress;
        int readerIndex;
        int writerIndex;

        @Override
        public void clear() {
            memoryAddress = -1;
            readerIndex = 0;
            writerIndex = 0;
            super.clear();
        }

        @Override
        public int cancel() {
            memoryAddress = -1;
            readerIndex = 0;
            writerIndex = 0;
            return super.cancel();
        }
    }
}
