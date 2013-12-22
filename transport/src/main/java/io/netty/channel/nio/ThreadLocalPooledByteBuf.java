/*
 * Copyright 2013 The Netty Project
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
package io.netty.channel.nio;

import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.buffer.UnpooledDirectByteBuf;
import io.netty.util.Recycler;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

final class ThreadLocalPooledByteBuf extends UnpooledDirectByteBuf {
    private static final int threadLocalDirectBufferSize;
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ThreadLocalPooledByteBuf.class);

    static {
        threadLocalDirectBufferSize = SystemPropertyUtil.getInt("io.netty.threadLocalDirectBufferSize", 64 * 1024);
        logger.debug("-Dio.netty.threadLocalDirectBufferSize: {}", threadLocalDirectBufferSize);
    }
    private final Recycler.Handle handle;

    private static final Recycler<ThreadLocalPooledByteBuf> RECYCLER = new Recycler<ThreadLocalPooledByteBuf>() {
        @Override
        protected ThreadLocalPooledByteBuf newObject(Handle handle) {
            return new ThreadLocalPooledByteBuf(handle);
        }
    };

    private ThreadLocalPooledByteBuf(Recycler.Handle handle) {
        super(UnpooledByteBufAllocator.DEFAULT, 256, Integer.MAX_VALUE);
        this.handle = handle;
    }

    static ThreadLocalPooledByteBuf newInstance() {
        ThreadLocalPooledByteBuf buf = RECYCLER.get();
        buf.setRefCnt(1);
        return buf;
    }

    @Override
    protected void deallocate() {
        if (capacity() > threadLocalDirectBufferSize) {
            super.deallocate();
        } else {
            clear();
            RECYCLER.recycle(this, handle);
        }
    }
}
