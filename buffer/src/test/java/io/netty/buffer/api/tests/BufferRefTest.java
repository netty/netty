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
package io.netty.buffer.api.tests;

import io.netty.buffer.api.Buffer;
import io.netty.buffer.api.BufferAllocator;
import io.netty.buffer.api.BufferClosedException;
import io.netty.buffer.api.BufferRef;
import io.netty.buffer.api.Send;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BufferRefTest {
    @Test
    public void closingBufRefMustCloseOwnedBuf() {
        try (BufferAllocator allocator = BufferAllocator.heap()) {
            BufferRef ref;
            try (Buffer b = allocator.allocate(8)) {
                ref = new BufferRef(b.send());
            }
            ref.contents().writeInt(42);
            assertThat(ref.contents().readInt()).isEqualTo(42);
            ref.close();
            assertThrows(BufferClosedException.class, () -> ref.contents().writeInt(32));
        }
    }

    @Test
    public void closingBufRefMustCloseOwnedBufFromSend() {
        try (BufferAllocator allocator = BufferAllocator.heap();
             Buffer buf = allocator.allocate(8)) {
            BufferRef ref = new BufferRef(buf.send());
            ref.contents().writeInt(42);
            assertThat(ref.contents().readInt()).isEqualTo(42);
            ref.close();
            assertThrows(BufferClosedException.class, () -> ref.contents().writeInt(32));
        }
    }

    @Test
    public void mustCloseOwnedBufferWhenReplacedFromSend() {
        try (BufferAllocator allocator = BufferAllocator.heap()) {
            AtomicReference<Buffer> orig = new AtomicReference<>();
            BufferRef ref;
            Send<Buffer> s = allocator.allocate(8).send();
            ref = new BufferRef(Send.sending(Buffer.class, () -> {
                Buffer b = s.receive();
                orig.set(b);
                return b;
            }));

            orig.get().writeInt(42);
            assertThat(ref.contents().readInt()).isEqualTo(42);

            try (Buffer buf = allocator.allocate(8)) {
                ref.replace(buf.send()); // Pass replacement via send().
            }

            assertThrows(BufferClosedException.class, () -> orig.get().writeInt(32));
            ref.contents().writeInt(42);
            assertThat(ref.contents().readInt()).isEqualTo(42);
            ref.close();
            assertThrows(BufferClosedException.class, () -> ref.contents().writeInt(32));
        }
    }

    @Test
    public void sendingRefMustSendBuffer() {
        try (BufferAllocator allocator = BufferAllocator.heap();
             BufferRef refA = new BufferRef(allocator.allocate(8).send())) {
            refA.contents().writeInt(42);
            Send<BufferRef> send = refA.send();
            assertThrows(BufferClosedException.class, () -> refA.contents().readInt());
            try (BufferRef refB = send.receive()) {
                assertThat(refB.contents().readInt()).isEqualTo(42);
            }
        }
    }
}
