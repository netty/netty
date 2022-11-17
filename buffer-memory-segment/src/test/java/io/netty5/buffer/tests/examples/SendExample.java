/*
 * Copyright 2021 The Netty Project
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
import io.netty5.util.Send;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.concurrent.Executors.newSingleThreadExecutor;

public class SendExample {

    static final class Ex1 {
        public static void main(String[] args) throws Exception {
            ExecutorService executor =
                    newSingleThreadExecutor();
            BufferAllocator allocator = BufferAllocator.onHeapUnpooled();

            var future = beginTask(executor, allocator);
            future.get();

            allocator.close();
            executor.shutdown();
        }

        private static Future<?> beginTask(
                ExecutorService executor, BufferAllocator allocator) {
            try (Buffer buf = allocator.allocate(32)) {
                // !!! pit-fall: buffer life-time ends before task completes
                return executor.submit(new Task(buf));
            }
        }

        private static class Task implements Runnable {
            private final Buffer buf;

            Task(Buffer buf) {
                this.buf = buf;
            }

            @Override
            public void run() {
                // !!! danger: access out-side owning thread.
                while (buf.writableBytes() > 0) {
                    buf.writeByte((byte) 42);
                }
            }
        }
    }

    static final class Ex2 {
        public static void main(String[] args) throws Exception {
            ExecutorService executor = newSingleThreadExecutor();
            BufferAllocator allocator = BufferAllocator.onHeapUnpooled();

            var future = beginTask(executor, allocator);
            future.get();

            allocator.close();
            executor.shutdown();
        }

        private static Future<?> beginTask(
                ExecutorService executor, BufferAllocator allocator) {
            try (Buffer buf = allocator.allocate(32)) {
                return executor.submit(new Task(buf.send()));
            }
        }

        private static class Task implements Runnable {
            private final Send<Buffer> send;

            Task(Send<Buffer> send) {
                this.send = send;
            }

            @Override
            public void run() {
                try (Buffer buf = send.receive()) {
                    while (buf.writableBytes() > 0) {
                        buf.writeByte((byte) 42);
                    }
                }
            }
        }
    }

    static final class Ex3 {
        public static void main(String[] args) throws Exception {
            ExecutorService executor = newFixedThreadPool(4);
            BufferAllocator allocator = BufferAllocator.onHeapUnpooled();

            try (Buffer buf = allocator.allocate(4096)) {
                var futA = executor.submit(new Task(buf.writerOffset(1024).split().send()));
                var futB = executor.submit(new Task(buf.writerOffset(1024).split().send()));
                var futC = executor.submit(new Task(buf.writerOffset(1024).split().send()));
                var futD = executor.submit(new Task(buf.send()));
                futA.get();
                futB.get();
                futC.get();
                futD.get();
            }

            allocator.close();
            executor.shutdown();
        }

        private static class Task implements Runnable {
            private final Send<Buffer> send;

            Task(Send<Buffer> send) {
                this.send = send;
            }

            @Override
            public void run() {
                try (Buffer buf = send.receive().writerOffset(0)) {
                    while (buf.writableBytes() > 0) {
                        buf.writeByte((byte) 42);
                    }
                }
            }
        }
    }
}
