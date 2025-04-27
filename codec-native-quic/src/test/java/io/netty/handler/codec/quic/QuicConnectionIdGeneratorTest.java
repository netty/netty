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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class QuicConnectionIdGeneratorTest extends AbstractQuicTest {

    @Test
    public void testRandomness() {
        QuicConnectionIdGenerator idGenerator = QuicConnectionIdGenerator.randomGenerator();
        ByteBuffer id = idGenerator.newId(Quiche.QUICHE_MAX_CONN_ID_LEN);
        ByteBuffer id2 = idGenerator.newId(Quiche.QUICHE_MAX_CONN_ID_LEN);
        assertThat(id.remaining(), greaterThan(0));
        assertThat(id2.remaining(), greaterThan(0));
        assertNotEquals(id, id2);

        id = idGenerator.newId(10);
        id2 = idGenerator.newId(10);
        assertEquals(10, id.remaining());
        assertEquals(10, id2.remaining());
        assertNotEquals(id, id2);

        byte[] input = new byte[1024];
        ThreadLocalRandom.current().nextBytes(input);
        id = idGenerator.newId(ByteBuffer.wrap(input), 10);
        id2 = idGenerator.newId(ByteBuffer.wrap(input), 10);
        assertEquals(10, id.remaining());
        assertEquals(10, id2.remaining());
        assertNotEquals(id, id2);
    }

    @Test
    public void testThrowsIfInputTooBig() {
        QuicConnectionIdGenerator idGenerator = QuicConnectionIdGenerator.randomGenerator();
        assertThrows(IllegalArgumentException.class, () -> idGenerator.newId(Integer.MAX_VALUE));
    }

    @Test
    public void testThrowsIfInputTooBig2() {
        QuicConnectionIdGenerator idGenerator = QuicConnectionIdGenerator.randomGenerator();
        assertThrows(IllegalArgumentException.class, () ->
                idGenerator.newId(ByteBuffer.wrap(new byte[8]), Integer.MAX_VALUE));
    }

    @Test
    public void testSignIdGenerator() {
        QuicConnectionIdGenerator idGenerator = QuicConnectionIdGenerator.signGenerator();

        byte[] input = new byte[1024];
        byte[] input2 = new byte[1024];
        ThreadLocalRandom.current().nextBytes(input);
        ThreadLocalRandom.current().nextBytes(input2);
        ByteBuffer id = idGenerator.newId(ByteBuffer.wrap(input), 10);
        ByteBuffer id2 = idGenerator.newId(ByteBuffer.wrap(input), 10);
        ByteBuffer id3 = idGenerator.newId(ByteBuffer.wrap(input2), 10);
        assertEquals(10, id.remaining());
        assertEquals(10, id2.remaining());
        assertEquals(10, id3.remaining());
        assertEquals(id, id2);
        assertNotEquals(id, id3);

        assertThrows(UnsupportedOperationException.class, () -> idGenerator.newId(10));
        assertThrows(NullPointerException.class, () -> idGenerator.newId(null, 10));
        assertThrows(IllegalArgumentException.class, () -> idGenerator.newId(ByteBuffer.wrap(new byte[0]), 10));
        assertThrows(IllegalArgumentException.class, () ->
                idGenerator.newId(ByteBuffer.wrap(input), Integer.MAX_VALUE));
    }
}
