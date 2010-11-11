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
 * @version $Rev: 2302 $, $Date: 2010-06-14 20:07:44 +0900 (Mon, 14 Jun 2010) $
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

    private static void assertQueryString(String expected, String actual) {
        QueryStringDecoder ed = new QueryStringDecoder(expected, CharsetUtil.UTF_8);
        QueryStringDecoder ad = new QueryStringDecoder(actual, CharsetUtil.UTF_8);
        Assert.assertEquals(ed.getPath(), ad.getPath());
        Assert.assertEquals(ed.getParameters(), ad.getParameters());
    }
}
