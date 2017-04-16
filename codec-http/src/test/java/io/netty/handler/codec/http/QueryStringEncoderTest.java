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
package io.netty.handler.codec.http;

import java.net.URI;
import java.nio.charset.Charset;

import org.junit.Assert;
import org.junit.Test;

public class QueryStringEncoderTest {

    @Test
    public void testDefaultEncoding() throws Exception {
        QueryStringEncoder e;

        e = new QueryStringEncoder("/foo");
        e.addParam("a", "b=c");
        Assert.assertEquals("/foo?a=b%3Dc", e.toString());
        Assert.assertEquals(new URI("/foo?a=b%3Dc"), e.toUri());

        e = new QueryStringEncoder("/foo/\u00A5");
        e.addParam("a", "\u00A5");
        Assert.assertEquals("/foo/\u00A5?a=%C2%A5", e.toString());
        Assert.assertEquals(new URI("/foo/\u00A5?a=%C2%A5"), e.toUri());

        e = new QueryStringEncoder("/foo");
        e.addParam("a", "1");
        e.addParam("b", "2");
        Assert.assertEquals("/foo?a=1&b=2", e.toString());
        Assert.assertEquals(new URI("/foo?a=1&b=2"), e.toUri());

        e = new QueryStringEncoder("/foo");
        e.addParam("a", "1");
        e.addParam("b", "");
        e.addParam("c", null);
        e.addParam("d", null);
        Assert.assertEquals("/foo?a=1&b=&c&d", e.toString());
        Assert.assertEquals(new URI("/foo?a=1&b=&c&d"), e.toUri());
    }

    @Test
    public void testNonDefaultEncoding() throws Exception {
        QueryStringEncoder e = new QueryStringEncoder("/foo/\u00A5", Charset.forName("UTF-16"));
        e.addParam("a", "\u00A5");
        Assert.assertEquals("/foo/\u00A5?a=%FE%FF%00%A5", e.toString());
        Assert.assertEquals(new URI("/foo/\u00A5?a=%FE%FF%00%A5"), e.toUri());
    }

    @Test
    public void testWhitespaceEncoding() throws Exception {
        QueryStringEncoder e = new QueryStringEncoder("/foo");
        e.addParam("a", "b c");
        Assert.assertEquals("/foo?a=b%20c", e.toString());
        Assert.assertEquals(new URI("/foo?a=b%20c"), e.toUri());
    }
}
