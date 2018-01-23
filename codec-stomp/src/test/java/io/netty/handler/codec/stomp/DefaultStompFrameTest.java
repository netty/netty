/*
 * Copyright 2018 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.stomp;

import io.netty.util.AsciiString;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class DefaultStompFrameTest {

    @Test
    public void testStompFrameCopy() {
        StompFrame sourceFrame = new DefaultStompFrame(StompCommand.CONNECT);

        assertTrue(sourceFrame.headers().isEmpty());

        sourceFrame.headers().set(StompHeaders.HOST, "localhost");

        StompFrame copyFrame = sourceFrame.copy();

        assertEquals(sourceFrame.headers(), copyFrame.headers());
        assertEquals(sourceFrame.content(), copyFrame.content());

        AsciiString copyHeaderName = new AsciiString("foo");
        AsciiString copyHeaderValue = new AsciiString("bar");
        copyFrame.headers().set(copyHeaderName, copyHeaderValue);

        assertFalse(sourceFrame.headers().contains(copyHeaderName, copyHeaderValue));
        assertTrue(copyFrame.headers().contains(copyHeaderName, copyHeaderValue));

        assertEquals(1, sourceFrame.headers().size());
        assertEquals(2, copyFrame.headers().size());
        assertNotEquals(sourceFrame.headers(), copyFrame.headers());

        assertTrue(sourceFrame.release());
        assertTrue(copyFrame.release());
    }
}
