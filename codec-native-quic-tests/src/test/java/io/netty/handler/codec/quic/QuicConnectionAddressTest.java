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
package io.netty.handler.codec.quic;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class QuicConnectionAddressTest extends AbstractQuicTest {

    @Test
    public void testNullByteArray() {
        assertThrows(NullPointerException.class, () -> new QuicConnectionAddress((byte[]) null));
    }

    @Test
    public void testNullByteBuffer() {
        assertThrows(NullPointerException.class, () -> new QuicConnectionAddress((ByteBuffer) null));
    }

    @Test
    public void testByteArrayIsCloned() {
        byte[] bytes = new byte[8];
        ThreadLocalRandom.current().nextBytes(bytes);
        QuicConnectionAddress address = new QuicConnectionAddress(bytes);
        assertEquals(ByteBuffer.wrap(bytes), address.id());
        ThreadLocalRandom.current().nextBytes(bytes);
        assertNotEquals(ByteBuffer.wrap(bytes), address.id());
    }

    @Test
    public void tesByteBufferIsDuplicated() {
        byte[] bytes = new byte[8];
        ThreadLocalRandom.current().nextBytes(bytes);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        QuicConnectionAddress address = new QuicConnectionAddress(bytes);
        assertEquals(buffer, address.id());
        buffer.position(1);
        assertNotEquals(buffer, address.id());
    }
}
