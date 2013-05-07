/*
 * Copyright 2012 The Netty Project
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

package io.netty.microbench.buffer;

import com.google.caliper.Param;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.microbench.util.DefaultBenchmark;

import java.util.ArrayDeque;
import java.util.Deque;

public class ByteBufAllocatorBenchmark extends DefaultBenchmark {

    private static final ByteBufAllocator UNPOOLED_ALLOCATOR_HEAP = new UnpooledByteBufAllocator(false);
    private static final ByteBufAllocator UNPOOLED_ALLOCATOR_DIRECT = new UnpooledByteBufAllocator(true);
    private static final ByteBufAllocator POOLED_ALLOCATOR_HEAP = new PooledByteBufAllocator(false);
    private static final ByteBufAllocator POOLED_ALLOCATOR_DIRECT = new PooledByteBufAllocator(true);

    @Param({ "0", "256", "1024", "4096", "16384", "65536" })
    private int size;

    @Param
    private Allocator allocator;

    private final Deque<ByteBuf> queue = new ArrayDeque<ByteBuf>();
    private ByteBufAllocator alloc;

    @Override
    protected void setUp() throws Exception {
        alloc = allocator.alloc();
        for (int i = 0; i < 2560; i ++) {
            queue.add(alloc.buffer(size));
        }
    }

    @Override
    protected void tearDown() throws Exception {
        for (ByteBuf b: queue) {
            b.release();
        }
        queue.clear();
    }

    public void timeAllocAndFree(int reps) {
        final ByteBufAllocator alloc = this.alloc;
        final Deque<ByteBuf> queue = this.queue;
        final int size = this.size;

        for (int i = 0; i < reps; i ++) {
            queue.add(alloc.buffer(size));
            queue.removeFirst().release();
        }
    }

    public enum Allocator {
        UNPOOLED_HEAP {
            @Override
            ByteBufAllocator alloc() {
                return UNPOOLED_ALLOCATOR_HEAP;
            }
        },
        UNPOOLED_DIRECT {
            @Override
            ByteBufAllocator alloc() {
                return UNPOOLED_ALLOCATOR_DIRECT;
            }
        },
        POOLED_HEAP {
            @Override
            ByteBufAllocator alloc() {
                return POOLED_ALLOCATOR_HEAP;
            }
        },
        POOLED_DIRECT {
            @Override
            ByteBufAllocator alloc() {
                return POOLED_ALLOCATOR_DIRECT;
            }
        };

        abstract ByteBufAllocator alloc();
    }
}
