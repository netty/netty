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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class QpackUtilTest {

    // A remainder of exactly 128 while encoding a multi-byte prefixed integer must still be encoded as a
    // continuation byte (it does not fit in the final, non-continuation byte, which can only hold 0-127).
    // For a 6-bit prefix (nbits = 63) this first happens at value 191 (63 + 128).
    @Test
    public void encodeDecodeRoundTripsAtContinuationByteBoundary() throws Exception {
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
    public void decodesValueEncodedOnPrefixOnly() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] { 10 });
        try {
            assertEquals(10, QpackUtil.decodePrefixedInteger(in, 5));
            assertEquals(0, in.readableBytes());
        } finally {
            in.release();
        }
    }

    @Test
    public void encodeDecodeRoundTripsOverWideRange() throws Exception {
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

    private static void assertRoundTrip(ByteBuf buf, int prefixLength, long value) throws Exception {
        encodePrefixedInteger(buf, (byte) 0, prefixLength, value);
        assertThat("Round trip failed for prefixLength=" + prefixLength + ", value=" + value,
            decodePrefixedInteger(buf, prefixLength), is(value));
    }

    @Test
    public void decodesValueEncodedWithContinuationBytes() throws Exception {
        // 5-bit prefix (nbits = 31), followed by continuation bytes encoding 1337 - 31 = 1306.
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] { 0x1f, (byte) 0x9a, 0x0a });
        try {
            assertEquals(1337, QpackUtil.decodePrefixedInteger(in, 5));
            assertEquals(0, in.readableBytes());
        } finally {
            in.release();
        }
    }

    @Test
    public void returnsMinusOneWhenNotEnoughReadableBytes() throws Exception {
        // Prefix is all-ones (needs continuation), but no continuation byte is available yet.
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] { 0x1f });
        try {
            int readerIndex = in.readerIndex();
            assertEquals(-1, QpackUtil.decodePrefixedInteger(in, 5));
            // readerIndex must be reset so a subsequent decode attempt re-reads the same bytes.
            assertEquals(readerIndex, in.readerIndex());
        } finally {
            in.release();
        }
    }

    @Test
    public void rejectsUnboundedContinuationByteRun() {
        // First byte with all prefix bits set, followed by a very long run of 0x80 continuation
        // bytes that never terminates (high bit never clear). Without a cap this would previously
        // make decodePrefixedInteger keep rescanning the buffer indefinitely, returning -1 forever
        // and letting the caller's cumulator grow without bound.
        byte[] bytes = new byte[4096];
        bytes[0] = 0x7f; // 7-bit prefix, all bits set.
        for (int i = 1; i < bytes.length; i++) {
            bytes[i] = (byte) 0x80;
        }
        ByteBuf in = Unpooled.wrappedBuffer(bytes);
        try {
            assertThrows(QpackException.class, () -> QpackUtil.decodePrefixedInteger(in, 7));
        } finally {
            in.release();
        }
    }

    @Test
    public void rejectsContinuationRunThatWouldOverflowLong() {
        // 9 continuation bytes (factor reaches 56) with the high bit always set is already enough
        // to trip the guard, even though the buffer is finite and would otherwise terminate.
        byte[] bytes = new byte[11];
        bytes[0] = 0x0f; // 4-bit prefix, all bits set.
        for (int i = 1; i < bytes.length - 1; i++) {
            bytes[i] = (byte) 0x80;
        }
        bytes[bytes.length - 1] = 0x01; // terminator, never reached because the guard fires first.
        ByteBuf in = Unpooled.wrappedBuffer(bytes);
        try {
            assertThrows(QpackException.class, () -> QpackUtil.decodePrefixedInteger(in, 4));
        } finally {
            in.release();
        }
    }
}
