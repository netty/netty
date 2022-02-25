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
package io.netty5.buffer.api.tests;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.BufferClosedException;
import io.netty5.buffer.api.BufferRef;
import io.netty5.buffer.api.Send;
import io.netty5.buffer.api.internal.ResourceSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;

import static io.netty5.buffer.api.internal.Statics.acquire;
import static io.netty5.buffer.api.internal.Statics.isOwned;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BufferSendTest extends BufferTestSupport {
    @ParameterizedTest
    @MethodSource("allocators")
    void allocateAndSendToThread(Fixture fixture) throws Exception {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            ArrayBlockingQueue<Send<Buffer>> queue = new ArrayBlockingQueue<>(10);
            Future<Byte> future = executor.submit(() -> {
                try (Buffer byteBuf = queue.take().receive()) {
                    return byteBuf.readByte();
                }
            });

            try (Buffer buf = allocator.allocate(8)) {
                buf.writeByte((byte) 42);
                assertTrue(queue.offer(buf.send()));
            }

            assertEquals((byte) 42, future.get().byteValue());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void allocateAndSendToThreadViaSyncQueue(Fixture fixture) throws Exception {
        SynchronousQueue<Send<Buffer>> queue = new SynchronousQueue<>();
        Future<Byte> future = executor.submit(() -> {
            try (Buffer byteBuf = queue.take().receive()) {
                return byteBuf.readByte();
            }
        });

        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThat(buf.writeByte((byte) 42)).isSameAs(buf);
            queue.put(buf.send());
        }

        assertEquals((byte) 42, future.get().byteValue());
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void sendMustThrowWhenBufIsAcquired(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            try (Buffer ignored = acquire((ResourceSupport<?, ?>) buf)) {
                assertFalse(isOwned((ResourceSupport<?, ?>) buf));
                assertThrows(IllegalStateException.class, buf::send);
            }
            // Now send() should work again.
            assertTrue(isOwned((ResourceSupport<?, ?>) buf));
            buf.send().receive().close();
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void originalBufferMustNotBeAccessibleAfterSend(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer orig = allocator.allocate(24)) {
            orig.writeLong(42);
            var send = orig.send();
            verifyInaccessible(orig);
            try (Buffer receive = send.receive()) {
                assertEquals(42, receive.readLong());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void cannotSendMoreThanOnce(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            var send = buf.send();
            var exc = assertThrows(BufferClosedException.class, () -> buf.send());
            send.receive().close();
            assertThat(exc).hasMessageContaining("closed");
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void sendMustNotMakeSplitBuffersInaccessible(Fixture fixture) throws Exception {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(16)) {
            buf.writeInt(64);
            var splitA = buf.split();
            buf.writeInt(42);
            var send = buf.split().send();
            buf.writeInt(72);
            var splitB = buf.split();
            var fut = executor.submit(() -> {
                try (Buffer receive = send.receive()) {
                    assertEquals(42, receive.readInt());
                }
            });
            fut.get();
            buf.writeInt(32);
            assertEquals(32, buf.readInt());
            assertEquals(64, splitA.readInt());
            assertEquals(72, splitB.readInt());
        }
    }

    @Test
    public void isSendOfMustCheckObjectTypes() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled()) {
            Send<Buffer> bufferSend = allocator.allocate(8).send();
            Send<BufferRef> bufferRefSend = new BufferRef(allocator.allocate(8).send()).send();
            try {
                assertTrue(Send.isSendOf(Buffer.class, bufferSend));
                assertFalse(Send.isSendOf(BufferRef.class, bufferSend));
                assertFalse(Send.isSendOf(Buffer.class, bufferRefSend));
                assertTrue(Send.isSendOf(BufferRef.class, bufferRefSend));
                assertFalse(Send.isSendOf(Buffer.class, new Object()));
                assertFalse(Send.isSendOf(Object.class, new Object()));
            } finally {
                bufferSend.close();
                bufferRefSend.close();
            }
            // Type checks must still pass after the sends have been received.
            assertTrue(Send.isSendOf(Buffer.class, bufferSend));
            assertTrue(Send.isSendOf(BufferRef.class, bufferRefSend));
        }
    }
}
