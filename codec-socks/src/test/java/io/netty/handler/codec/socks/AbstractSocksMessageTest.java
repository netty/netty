/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.socks;

import io.netty.buffer.ByteBuf;
import org.junit.Before;
import static org.junit.Assert.*;


public class AbstractSocksMessageTest {
    private int exceptionCounter;

    @Before
    public void cleanExceptionCounter() {
        exceptionCounter = 0;
    }

    protected void assertExceptionCounter(int expect) {
        assertTrue(exceptionCounter == expect);
    }

    protected void assertIllegalArgumentException(Exception e) {
        assertTrue(e instanceof IllegalArgumentException);
        exceptionCounter++;
    }

    protected void assertNullPointerException(Exception e) {
        assertTrue(e instanceof NullPointerException);
        exceptionCounter++;
    }

    protected static void assertByteBufEquals(byte[] expected, ByteBuf actual) {
        byte[] actualBytes = new byte[actual.readableBytes()];
        actual.readBytes(actualBytes);
        assertEquals("Generated response has incorrect length", expected.length, actualBytes.length);
        assertArrayEquals("Generated response differs from expected", expected, actualBytes);
    }
}
