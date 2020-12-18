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
package io.netty.incubator.codec.quic;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class QuicPacketTypeTest extends AbstractQuicTest {

    @Test
    public void testOfValidType() {
        for (QuicPacketType type: QuicPacketType.values()) {
            assertEquals(type, QuicPacketType.of(type.type));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOfInvalidType() {
        QuicPacketType.of((byte) -1);
    }
}
