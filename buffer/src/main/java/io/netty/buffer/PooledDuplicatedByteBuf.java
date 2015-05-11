/*
 * Copyright 2015 The Netty Project
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
package io.netty.buffer;

import io.netty.util.Recycler;

final class PooledDuplicatedByteBuf extends DuplicatedByteBuf {

    private static final Recycler<PooledDuplicatedByteBuf> RECYCLER = new Recycler<PooledDuplicatedByteBuf>() {
        @Override
        protected PooledDuplicatedByteBuf newObject(Handle handle) {
            return new PooledDuplicatedByteBuf(handle);
        }
    };

    static PooledDuplicatedByteBuf newInstance(ByteBuf buffer) {
        PooledDuplicatedByteBuf buf = RECYCLER.get();
        buf.init(buffer);
        return buf;
    }

    private final Recycler.Handle recyclerHandle;

    private PooledDuplicatedByteBuf(Recycler.Handle recyclerHandle) {
        super(0);
        this.recyclerHandle = recyclerHandle;
    }

    @Override
    public ByteBuf slice(int index, int length) {
        return PooledSlicedByteBuf.newInstance(this, index, length);
    }

    @Override
    public ByteBuf duplicate() {
        return newInstance(this);
    }

    @Override
    protected void deallocate() {
        RECYCLER.recycle(this, recyclerHandle);
    }
}
