/*
 * Copyright 2019 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

import io.netty.buffer.DefaultByteBufHolder;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

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
}
