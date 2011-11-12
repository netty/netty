/*
 * Copyright 2010 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.http;

import org.jboss.netty.util.CharsetUtil;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @author <a href="http://tsunanet.net/">Benoit Sigoure</a>
 * @version $Rev$, $Date$
 */
public class QueryStringDecoderTest {

    @Test
    public void testBasic() throws Exception {
        QueryStringDecoder d;

        d = new QueryStringDecoder("/foo?a=b=c");
        Assert.assertEquals("/foo", d.getPath());
        Assert.assertEquals(1, d.getParameters().size());
        Assert.assertEquals(1, d.getParameters().get("a").size());
        Assert.assertEquals("b=c", d.getParameters().get("a").get(0));

        d = new QueryStringDecoder("/foo?a=1&a=2");
        Assert.assertEquals("/foo", d.getPath());
        Assert.assertEquals(1, d.getParameters().size());
        Assert.assertEquals(2, d.getParameters().get("a").size());
        Assert.assertEquals("1", d.getParameters().get("a").get(0));
        Assert.assertEquals("2", d.getParameters().get("a").get(1));

        d = new QueryStringDecoder("/foo?a=&a=2");
        Assert.assertEquals("/foo", d.getPath());
        Assert.assertEquals(1, d.getParameters().size());
        Assert.assertEquals(2, d.getParameters().get("a").size());
        Assert.assertEquals("", d.getParameters().get("a").get(0));
        Assert.assertEquals("2", d.getParameters().get("a").get(1));

        d = new QueryStringDecoder("/foo?a=1&a=");
        Assert.assertEquals("/foo", d.getPath());
        Assert.assertEquals(1, d.getParameters().size());
        Assert.assertEquals(2, d.getParameters().get("a").size());
        Assert.assertEquals("1", d.getParameters().get("a").get(0));
        Assert.assertEquals("", d.getParameters().get("a").get(1));

        d = new QueryStringDecoder("/foo?a=1&a=&a=");
        Assert.assertEquals("/foo", d.getPath());
        Assert.assertEquals(1, d.getParameters().size());
        Assert.assertEquals(3, d.getParameters().get("a").size());
        Assert.assertEquals("1", d.getParameters().get("a").get(0));
        Assert.assertEquals("", d.getParameters().get("a").get(1));
        Assert.assertEquals("", d.getParameters().get("a").get(2));

        d = new QueryStringDecoder("/foo?a=1=&a==2");
        Assert.assertEquals("/foo", d.getPath());
        Assert.assertEquals(1, d.getParameters().size());
        Assert.assertEquals(2, d.getParameters().get("a").size());
        Assert.assertEquals("1=", d.getParameters().get("a").get(0));
        Assert.assertEquals("=2", d.getParameters().get("a").get(1));
    }

    @Test
    public void testExotic() throws Exception {
        assertQueryString("", "");
        assertQueryString("foo", "foo");
        assertQueryString("/foo", "/foo");
        assertQueryString("?a=", "?a");
        assertQueryString("foo?a=", "foo?a");
        assertQueryString("/foo?a=", "/foo?a");
        assertQueryString("/foo?a=", "/foo?a&");
        assertQueryString("/foo?a=", "/foo?&a");
        assertQueryString("/foo?a=", "/foo?&a&");
        assertQueryString("/foo?a=", "/foo?&=a");
        assertQueryString("/foo?a=", "/foo?=a&");
        assertQueryString("/foo?a=", "/foo?a=&");
        assertQueryString("/foo?a=b&c=d", "/foo?a=b&&c=d");
        assertQueryString("/foo?a=b&c=d", "/foo?a=b&=&c=d");
        assertQueryString("/foo?a=b&c=d", "/foo?a=b&==&c=d");
        assertQueryString("/foo?a=b&c=&x=y", "/foo?a=b&c&x=y");
        assertQueryString("/foo?a=", "/foo?a=");
        assertQueryString("/foo?a=", "/foo?&a=");
        assertQueryString("/foo?a=b&c=d", "/foo?a=b&c=d");
        assertQueryString("/foo?a=1&a=&a=", "/foo?a=1&a&a=");
    }

    @Test
    public void testUrlDecoding() throws Exception {
        final String caffe = new String(
                // "Caffé" but instead of putting the literal E-acute in the
                // source file, we directly use the UTF-8 encoding so as to
                // not rely on the platform's default encoding (not portable).
                new byte[] {'C', 'a', 'f', 'f', (byte) 0xC3, (byte) 0xA9},
                "UTF-8");
        final String[] tests = {
            // Encoded   ->   Decoded or error message substring
            "",               "",
            "foo",            "foo",
            "f%%b",           "f%b",
            "f+o",            "f o",
            "f++",            "f  ",
            "fo%",            "unterminated escape sequence",
            "%42",            "B",
            "%5f",            "_",
            "f%4",            "partial escape sequence",
            "%x2",            "invalid escape sequence `%x2' at index 0 of: %x2",
            "%4x",            "invalid escape sequence `%4x' at index 0 of: %4x",
            "Caff%C3%A9",     caffe,
        };
        for (int i = 0; i < tests.length; i += 2) {
            final String encoded = tests[i];
            final String expected = tests[i + 1];
            try {
                final String decoded = QueryStringDecoder.decodeComponent(encoded);
                Assert.assertEquals(expected, decoded);
            } catch (IllegalArgumentException e) {
                Assert.assertTrue("String \"" + e.getMessage() + "\" does"
                                  + " not contain \"" + expected + '"',
                                  e.getMessage().contains(expected));
            }
        }
    }

    private static void assertQueryString(String expected, String actual) {
        QueryStringDecoder ed = new QueryStringDecoder(expected, CharsetUtil.UTF_8);
        QueryStringDecoder ad = new QueryStringDecoder(actual, CharsetUtil.UTF_8);
        Assert.assertEquals(ed.getPath(), ad.getPath());
        Assert.assertEquals(ed.getParameters(), ad.getParameters());
    }
}
