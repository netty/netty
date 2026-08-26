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
package io.netty.handler.codec.http3;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static io.netty.handler.codec.http3.QpackUtil.decodePrefixedInteger;
import static io.netty.handler.codec.http3.QpackUtil.encodePrefixedInteger;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class QpackUtilTest {

    // A remainder of exactly 128 while encoding a multi-byte prefixed integer must still be encoded as a
    // continuation byte (it does not fit in the final, non-continuation byte, which can only hold 0-127).
    // For a 6-bit prefix (nbits = 63) this first happens at value 191 (63 + 128).
    @Test
    public void encodeDecodeRoundTripsAtContinuationByteBoundary() {
        ByteBuf buf = Unpooled.buffer();
        try {
            for (int prefixLength = 1; prefixLength <= 8; prefixLength++) {
                int nbits = (1 << prefixLength) - 1;
                long value = nbits + 128;
                assertRoundTrip(buf, prefixLength, value);
                buf.clear();
            }
        } finally {
            buf.release();
        }
    }

    @Test
    public void encodeDecodeRoundTripsOverWideRange() {
        ByteBuf buf = Unpooled.buffer();
        try {
            for (int prefixLength : new int[] { 4, 5, 6, 7, 8 }) {
                for (long value = 0; value <= 20_000; value++) {
                    assertRoundTrip(buf, prefixLength, value);
                    buf.clear();
                }
            }
        } finally {
            buf.release();
        }
    }

    private static void assertRoundTrip(ByteBuf buf, int prefixLength, long value) {
        encodePrefixedInteger(buf, (byte) 0, prefixLength, value);
        assertThat("Round trip failed for prefixLength=" + prefixLength + ", value=" + value,
            decodePrefixedInteger(buf, prefixLength), is(value));
    }
}
