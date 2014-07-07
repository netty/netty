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

import org.junit.Assert;
import org.junit.Test;

public class HttpMethodTest {

    @Test
    public void testWithoutWhitespace() {
        char[] chars = "GET".toCharArray();
        Assert.assertSame(HttpMethod.GET, HttpMethod.valueOf(chars, 0, chars.length));
    }

    @Test
    public void testWithWhitespace() {
        char[] chars = "  GET    ".toCharArray();
        Assert.assertSame(HttpMethod.GET, HttpMethod.valueOf(chars, 1, chars.length - 1));
    }

    @Test
    public void testUnknown() {
        char[] chars = "  TEST    ".toCharArray();
        HttpMethod method = HttpMethod.valueOf(chars, 0, chars.length);
        Assert.assertEquals("TEST", method.name());
    }
}
