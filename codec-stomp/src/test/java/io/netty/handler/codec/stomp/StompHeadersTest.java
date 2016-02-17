/*
 * Copyright 2015 The Netty Project
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
import static org.junit.Assert.assertNull;

public class StompHeadersTest {
    @Test
    public void testHeadersCaseSensitive() {
        DefaultStompHeaders headers = new DefaultStompHeaders();
        AsciiString foo = new AsciiString("foo");
        AsciiString value = new AsciiString("value");
        headers.add(foo, value);
        assertNull(headers.get("Foo"));
        assertEquals(value, headers.get(foo));
        assertEquals(value, headers.get(foo.toString()));
    }
}
