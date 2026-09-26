/*
 * Copyright 2025 The Netty Project
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
package io.netty.buffer;

import io.netty.util.internal.RecyclableArrayList;

import java.util.Objects;

public final class DecodeBuffer implements AutoCloseable {
    private final ByteBufAllocator alloc;
    private RecyclableArrayList items;
    private ByteBuf decoding;

    private DecodeBuffer(ByteBufAllocator alloc) {
        this.alloc = alloc;
        this.items = RecyclableArrayList.newInstance();
    }

    public static DecodeBuffer create(ByteBufAllocator alloc) {
        return new DecodeBuffer(alloc);
    }

    public void add(ByteBuf buf) {
        if (decoding != null) {
            throw new IllegalStateException("Decoding in progress");
        }
        if (!buf.isReadable()) {
            buf.release();
            return;
        }
        // TODO: limit number of components?
        items.add(buf);
    }

    public ByteBuf startDecode() {
        if (decoding != null) {
            throw new IllegalStateException("Decoding in progress");
        }

        // create a view of the buffered data with independent readerIndex
        ByteBuf b;
        if (items.isEmpty()) {
            b = Unpooled.EMPTY_BUFFER;
        } else if (items.size() == 1) {
            b = ((ByteBuf) items.get(0)).retainedSlice();
        } else {
            CompositeByteBuf cbb = alloc.compositeBuffer(items.size());
            for (Object item : items) {
                cbb.addComponent(true, ((ByteBuf) item).retain());
            }
            b = cbb;
        }
        assert b.readerIndex() == 0;
        this.decoding = b;
        return b;
    }

    public void stopDecode(ByteBuf b) {
        if (decoding != Objects.requireNonNull(b, "b")) {
            throw new IllegalArgumentException("Decoding not in progress, or wrong buffer passed to stopDecode");
        }

        int read = b.readerIndex();
        int toDrop = 0;
        while (read > 0 && toDrop < items.size()) {
            ByteBuf head = (ByteBuf) items.get(toDrop);
            if (head.readableBytes() > read) {
                // apply readerIndex of temporary decode buffer onto our permanent copy
                head.skipBytes(read);
                break;
            } else {
                read -= head.readableBytes();
                head.release();
                toDrop++;
            }
        }
        // drop any buffers that have been fully decoded
        items.subList(0, toDrop).clear();

        this.decoding = null;
    }

    @Override
    public void close() {
        for (Object item : items) {
            ((ByteBuf) item).release();
        }
        items.recycle();
        items = null; // safety net, makes other operations fail
    }
}
