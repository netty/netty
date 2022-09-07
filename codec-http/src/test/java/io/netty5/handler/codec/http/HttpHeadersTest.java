/*
 * Copyright 2013 The Netty Project
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
package io.netty5.handler.codec.http;

import io.netty5.handler.codec.http.headers.HttpHeaders;
import io.netty5.util.AsciiString;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpHeadersTest {

    @Test
    public void testRemoveTransferEncodingIgnoreCase() {
        HttpMessage message = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        message.headers().set(HttpHeaderNames.TRANSFER_ENCODING, "Chunked");
        assertFalse(message.headers().isEmpty());
        HttpUtil.setTransferEncodingChunked(message, false);
        assertTrue(message.headers().isEmpty());
    }

    // Test for https://github.com/netty/netty/issues/1690
    @Test
    public void testGetOperations() {
        HttpHeaders headers = HttpHeaders.newHeaders();
        headers.add("Foo", "1");
        headers.add("Foo", "2");

        assertEquals("1", headers.get("Foo"));

        Iterable<CharSequence> values = headers.values("Foo");
        assertThat(values).containsExactly("1", "2");
    }

    @Test
    public void testEqualsIgnoreCase() {
        assertTrue(AsciiString.contentEqualsIgnoreCase(null, null));
        assertFalse(AsciiString.contentEqualsIgnoreCase(null, "foo"));
        assertFalse(AsciiString.contentEqualsIgnoreCase("bar", null));
        assertTrue(AsciiString.contentEqualsIgnoreCase("FoO", "fOo"));
    }

    @Test
    public void testSetNullHeaderValueValidate() {
        HttpHeaders headers = HttpHeaders.newHeaders(true);
        assertThrows(NullPointerException.class, () -> headers.set("test", (CharSequence) null));
    }

    @Test
    public void testSetNullHeaderValueNotValidate() {
        HttpHeaders headers = HttpHeaders.newHeaders(false);
        assertThrows(NullPointerException.class, () -> headers.set("test", (CharSequence) null));
    }

    @Test
    public void testAddSelf() {
        HttpHeaders headers = HttpHeaders.newHeaders(false);
        assertThrows(IllegalArgumentException.class, () -> headers.add(headers));
    }

    @Test
    public void testSetSelfIsNoOp() {
        HttpHeaders headers = HttpHeaders.newHeaders(false);
        headers.add("name", "value");
        headers.set(headers);
        assertEquals(1, headers.size());
    }
}
