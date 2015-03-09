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

import io.netty.handler.codec.AsciiString;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DefaultHttp2HeadersTest {

    private static final AsciiString NAME = new AsciiString("Test");
    private static final AsciiString VALUE = new AsciiString("some value");

    @Test
    public void defaultLowercase() {
        Http2Headers headers = new DefaultHttp2Headers().set(NAME, VALUE);
        assertEquals(first(headers), NAME.toLowerCase());
    }

    @Test
    public void caseInsensitive() {
        Http2Headers headers = new DefaultHttp2Headers(false).set(NAME, VALUE);
        assertEquals(first(headers), NAME);
    }

    private static AsciiString first(Http2Headers headers) {
        return headers.names().iterator().next();
    }

}
