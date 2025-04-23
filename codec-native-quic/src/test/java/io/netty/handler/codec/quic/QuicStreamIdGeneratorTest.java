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

import static org.junit.jupiter.api.Assertions.assertEquals;

public class QuicStreamIdGeneratorTest extends AbstractQuicTest {

    @Test
    public void testServerStreamIds() {
        QuicStreamIdGenerator generator = new QuicStreamIdGenerator(true);
        assertEquals(1, generator.nextStreamId(true));
        assertEquals(5, generator.nextStreamId(true));
        assertEquals(3, generator.nextStreamId(false));
        assertEquals(9, generator.nextStreamId(true));
        assertEquals(7, generator.nextStreamId(false));
    }

    @Test
    public void testClientStreamIds() {
        QuicStreamIdGenerator generator = new QuicStreamIdGenerator(false);
        assertEquals(0, generator.nextStreamId(true));
        assertEquals(4, generator.nextStreamId(true));
        assertEquals(2, generator.nextStreamId(false));
        assertEquals(8, generator.nextStreamId(true));
        assertEquals(6, generator.nextStreamId(false));
    }
}
