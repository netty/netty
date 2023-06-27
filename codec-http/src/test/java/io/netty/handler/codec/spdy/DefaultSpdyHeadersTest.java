/*
 * Copyright 2015 The Netty Project
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
package io.netty.handler.codec.spdy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DefaultSpdyHeadersTest {

    @Test
    public void testStringKeyRetrievedAsAsciiString() {
        final SpdyHeaders headers = new DefaultSpdyHeaders();

        // Test adding String key and retrieving it using a AsciiString key
        final String method = "GET";
        headers.add(":method", method);

        final String value = headers.getAsString(SpdyHeaders.HttpNames.METHOD.toString());
        assertNotNull(value);
        assertEquals(method, value);

        final String value2 = headers.getAsString(SpdyHeaders.HttpNames.METHOD);
        assertNotNull(value2);
        assertEquals(method, value2);
    }

    @Test
    public void testAsciiStringKeyRetrievedAsString() {
        final SpdyHeaders headers = new DefaultSpdyHeaders();

        // Test adding AsciiString key and retrieving it using a String key
        final String path = "/";
        headers.add(SpdyHeaders.HttpNames.PATH, path);

        final String value = headers.getAsString(SpdyHeaders.HttpNames.PATH);
        assertNotNull(value);
        assertEquals(path, value);

        final String value2 = headers.getAsString(SpdyHeaders.HttpNames.PATH.toString());
        assertNotNull(value2);
        assertEquals(path, value2);
    }
}
