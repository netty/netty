/*
 * Copyright 2019 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

import io.netty.buffer.DefaultByteBufHolder;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class Http2DefaultFramesTest {

    @SuppressWarnings("SimplifiableJUnitAssertion")
    @Test
    public void testEqualOperation() {
        // in this case, 'goAwayFrame' and 'unknownFrame' will also have an EMPTY_BUFFER data
        // so we want to check that 'dflt' will not consider them equal.
        DefaultHttp2GoAwayFrame goAwayFrame = new DefaultHttp2GoAwayFrame(1);
        DefaultHttp2UnknownFrame unknownFrame = new DefaultHttp2UnknownFrame((byte) 1, new Http2Flags((short) 1));
        DefaultByteBufHolder dflt = new DefaultByteBufHolder(Unpooled.EMPTY_BUFFER);
        try {
            // not using 'assertNotEquals' to be explicit about which object we are calling .equals() on
            assertFalse(dflt.equals(goAwayFrame));
            assertFalse(dflt.equals(unknownFrame));
        } finally {
            goAwayFrame.release();
            unknownFrame.release();
            dflt.release();
        }
    }

    // Reproduces https://github.com/netty/netty/issues/13659
    // AbstractHttp2StreamFrame.equals() treats two frames with a null stream as equal, but
    // AbstractHttp2StreamFrame.hashCode() previously returned super.hashCode() (identity hash)
    // in that case, producing different hashes for equal instances and violating the
    // Object.hashCode contract.
    @Test
    public void testAbstractHttp2StreamFrameEqualInstancesHaveEqualHashCodes() {
        AbstractHttp2StreamFrame a = new TestStreamFrame();
        AbstractHttp2StreamFrame b = new TestStreamFrame();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    // Reproduces https://github.com/netty/netty/issues/13659 for DefaultHttp2PingFrame.
    // equals() compares ack and content; before the fix hashCode() folded in the identity
    // hash of Object, so two equal pings had different hash codes.
    @Test
    public void testDefaultHttp2PingFrameEqualInstancesHaveEqualHashCodes() {
        DefaultHttp2PingFrame a = new DefaultHttp2PingFrame(42L, true);
        DefaultHttp2PingFrame b = new DefaultHttp2PingFrame(42L, true);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    // Smoke test that the hash actually folds in content and ack. This is a regression guard
    // against the pre-fix implementation, which seeded the hash with Object identity and never
    // folded content into the result at all. Specific hashCode values are an implementation
    // detail; we only assert that the chosen widely-spread inputs do not collide.
    @Test
    public void testDefaultHttp2PingFrameHashCodeDistinguishesDifferentValues() {
        DefaultHttp2PingFrame a = new DefaultHttp2PingFrame(0L, false);
        DefaultHttp2PingFrame differentContent = new DefaultHttp2PingFrame(Long.MAX_VALUE, false);
        DefaultHttp2PingFrame differentAck = new DefaultHttp2PingFrame(0L, true);
        assertNotEquals(a.hashCode(), differentContent.hashCode());
        assertNotEquals(a.hashCode(), differentAck.hashCode());
    }

    private static final class TestStreamFrame extends AbstractHttp2StreamFrame {
        @Override
        public String name() {
            return "TEST";
        }
    }
}
