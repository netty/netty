/*
 * Copyright 2020 The Netty Project
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
package io.netty5.buffer.tests.examples;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class ComposingAndSplittingExample {
    public static void main(String[] args) {
        try (BufferAllocator allocator = BufferAllocator.offHeapPooled();
             Buffer buf = createBigBuffer(allocator)) {

            ThreadLocalRandom tlr = ThreadLocalRandom.current();
            for (int i = 0; i < tlr.nextInt(4, 200); i++) {
                buf.writeByte((byte) tlr.nextInt());
            }

            try (Buffer split = buf.split()) {
                split.send();
                System.out.println("buf.capacity() = " + buf.capacity());
                System.out.println("buf.readableBytes() = " + buf.readableBytes());
                System.out.println("---");
                System.out.println("split.capacity() = " + split.capacity());
                System.out.println("split.readableBytes() = " + split.readableBytes());
            }
        }
    }

    private static Buffer createBigBuffer(BufferAllocator allocator) {
        return allocator.compose(List.of(
                allocator.allocate(64).send(),
                allocator.allocate(64).send(),
                allocator.allocate(64).send(),
                allocator.allocate(64).send()));
    }
}
