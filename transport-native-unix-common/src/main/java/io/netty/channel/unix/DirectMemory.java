/*
 * Copyright 2023 The Netty Project
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
package io.netty.channel.unix;


import java.nio.ByteBuffer;

public final class DirectMemory {

    private final ByteBuffer memory;
    private final long memoryAddress;

    public DirectMemory(int capacity) {
        this.memory = ByteBuffer.allocateDirect(capacity);
        this.memoryAddress = Buffer.memoryAddress(memory);
    }

    public long memoryAddress() {
        return memoryAddress;
    }

    public int length() {
        return memory.capacity();
    }

    public void free() {
        Buffer.free(memory);
    }
}
