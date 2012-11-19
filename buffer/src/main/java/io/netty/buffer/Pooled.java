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
package io.netty.buffer;

public final class Pooled {

    /* Default global pool implementation */
    private static final ByteBufAllocator POOL = new UnpooledByteBufAllocator();

    static {
        // Add shutdown hook only when it's absolutely sure that it doesn't leak.
        if (Pooled.class.getClassLoader() == ClassLoader.getSystemClassLoader()) {
            try {
                Runtime.getRuntime().addShutdownHook(new Thread() {
                    @Override
                    public void run() {
                        POOL.shutdown();
                    }
                });
            } catch (Exception ignored) {
                // Couldn't register the shutdown hook probably due to a rejection from SecurityManager.
            }
        }
    }

    public static ByteBufAllocator globalPool() {
        return POOL;
    }

    public static ByteBuf buffer() {
        return POOL.buffer();
    }

    public static ByteBuf buffer(int initialCapacity) {
        return POOL.buffer(initialCapacity);
    }

    public static ByteBuf buffer(int initialCapacity, int maxCapacity) {
        return POOL.buffer(initialCapacity, maxCapacity);
    }

    public static ByteBuf heapBuffer() {
        return POOL.heapBuffer();
    }

    public static ByteBuf heapBuffer(int initialCapacity) {
        return POOL.heapBuffer(initialCapacity);
    }

    public static ByteBuf heapBuffer(int initialCapacity, int maxCapacity) {
        return POOL.heapBuffer(initialCapacity, maxCapacity);
    }

    public static ByteBuf directBuffer() {
        return POOL.directBuffer();
    }

    public static ByteBuf directBuffer(int initialCapacity) {
        return POOL.directBuffer(initialCapacity);
    }

    public static ByteBuf directBuffer(int initialCapacity, int maxCapacity) {
        return POOL.directBuffer(initialCapacity, maxCapacity);
    }

    private Pooled() {
        // Unused
    }
}
