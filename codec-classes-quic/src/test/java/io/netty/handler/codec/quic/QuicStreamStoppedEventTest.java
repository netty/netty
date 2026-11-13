/*
 * Copyright 2026 The Netty Project
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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class QuicStreamStoppedEventTest {

    @ParameterizedTest
    @ValueSource(longs = { -1, 0, 0x10c, Long.MAX_VALUE })
    public void testValidApplicationProtocolCode(long code) {
        assertEquals(code, new QuicStreamStoppedEvent(code).applicationProtocolCode());
    }

    @ParameterizedTest
    @ValueSource(longs = { -2, Long.MIN_VALUE })
    public void testInvalidApplicationProtocolCode(long code) {
        assertThrows(IllegalArgumentException.class, () -> new QuicStreamStoppedEvent(code));
    }
}
