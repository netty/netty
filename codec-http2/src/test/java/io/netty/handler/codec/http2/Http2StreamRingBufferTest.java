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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class Http2StreamRingBufferTest {

    @Test
    public void testOverride() {
        Http2StreamRingBuffer streams = new Http2StreamRingBuffer();
        assertAdd(true, streams, 0);
        assertAdd(false, streams, 0);
        assertAdd(true, streams, streams.capacity());
        assertAdd(false, streams, streams.capacity());
    }

    private static void assertAdd(boolean expected, Http2StreamRingBuffer streams, int increment) {
        for (int i = 0; i < streams.capacity(); i++) {
            assertEquals(expected, streams.add(i + increment));
        }
    }
}
