/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import io.netty.util.AsciiString;
import io.netty.util.ByteString;
import io.netty.util.CharsetUtil;

import org.junit.Test;

public class DefaultHttp2HeadersTest {

    private static final byte[] NAME_BYTES = { 'T', 'E', 's', 'T' };
    private static final byte[] NAME_BYTES_LOWERCASE = { 't', 'e', 's', 't' };
    private static final ByteString NAME_BYTESTRING = new ByteString(NAME_BYTES);
    private static final AsciiString NAME_ASCIISTRING = new AsciiString("Test");
    private static final ByteString VALUE = new ByteString("some value", CharsetUtil.UTF_8);

    @Test
    public void defaultLowercase() {
        Http2Headers headers = new DefaultHttp2Headers().set(NAME_BYTESTRING, VALUE);
        assertArrayEquals(NAME_BYTES_LOWERCASE, first(headers).toByteArray());
    }

    @Test
    public void defaultLowercaseAsciiString() {
        Http2Headers headers = new DefaultHttp2Headers().set(NAME_ASCIISTRING, VALUE);
        assertEquals(NAME_ASCIISTRING.toLowerCase(), first(headers));
    }

    @Test
    public void caseInsensitive() {
        Http2Headers headers = new DefaultHttp2Headers(false).set(NAME_BYTESTRING, VALUE);
        assertArrayEquals(NAME_BYTES, first(headers).toByteArray());
    }

    private static ByteString first(Http2Headers headers) {
        return headers.names().iterator().next();
    }
}
