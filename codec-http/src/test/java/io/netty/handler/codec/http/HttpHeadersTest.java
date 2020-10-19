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
package io.netty.handler.codec.http;

import io.netty.util.AsciiString;
import org.junit.Test;

import java.util.List;

import static io.netty.handler.codec.http.HttpHeadersTestUtils.of;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.add(of("Foo"), of("1"));
        headers.add(of("Foo"), of("2"));

        assertEquals("1", headers.get(of("Foo")));

        List<String> values = headers.getAll(of("Foo"));
        assertEquals(2, values.size());
        assertEquals("1", values.get(0));
        assertEquals("2", values.get(1));
    }

    @Test
    public void testEqualsIgnoreCase() {
        assertThat(AsciiString.contentEqualsIgnoreCase(null, null), is(true));
        assertThat(AsciiString.contentEqualsIgnoreCase(null, "foo"), is(false));
        assertThat(AsciiString.contentEqualsIgnoreCase("bar", null), is(false));
        assertThat(AsciiString.contentEqualsIgnoreCase("FoO", "fOo"), is(true));
    }

    @Test(expected = NullPointerException.class)
    public void testSetNullHeaderValueValidate() {
        HttpHeaders headers = new DefaultHttpHeaders(true);
        headers.set(of("test"), (CharSequence) null);
    }

    @Test(expected = NullPointerException.class)
    public void testSetNullHeaderValueNotValidate() {
        HttpHeaders headers = new DefaultHttpHeaders(false);
        headers.set(of("test"), (CharSequence) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddSelf() {
        HttpHeaders headers = new DefaultHttpHeaders(false);
        headers.add(headers);
    }

    @Test
    public void testSetSelfIsNoOp() {
        HttpHeaders headers = new DefaultHttpHeaders(false);
        headers.add("name", "value");
        headers.set(headers);
        assertEquals(1, headers.size());
    }
}
