/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.http;

import org.junit.Test;

import java.util.List;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class HttpHeadersTest {

    @Test
    public void testRemoveTransferEncodingIgnoreCase() {
        HttpMessage message = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        message.headers().set(HttpHeaders.Names.TRANSFER_ENCODING, "Chunked");
        assertFalse(message.headers().isEmpty());
        HttpHeaders.removeTransferEncodingChunked(message);
        assertTrue(message.headers().isEmpty());
    }

    // Test for https://github.com/netty/netty/issues/1690
    @Test
    public void testGetOperations() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.add("Foo", "1");
        headers.add("Foo", "2");

        assertEquals("1", headers.get("Foo"));

        List<String> values = headers.getAll("Foo");
        assertEquals(2, values.size());
        assertEquals("1", values.get(0));
        assertEquals("2", values.get(1));
    }

    @Test
    public void testEquansIgnoreCase() {
        assertThat(HttpHeaders.equalsIgnoreCase(null, null), is(true));
        assertThat(HttpHeaders.equalsIgnoreCase(null, "foo"), is(false));
        assertThat(HttpHeaders.equalsIgnoreCase("bar", null), is(false));
        assertThat(HttpHeaders.equalsIgnoreCase("FoO", "fOo"), is(true));
    }
}
