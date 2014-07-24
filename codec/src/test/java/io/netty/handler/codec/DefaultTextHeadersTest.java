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
package io.netty.handler.codec;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

import static org.junit.Assert.*;

public class DefaultTextHeadersTest {

    @Test
    public void testEqualsMultipleHeaders() {
        DefaultTextHeaders h1 = new DefaultTextHeaders();
        h1.set("foo", "goo");
        h1.set("foo2", "goo2");

        DefaultTextHeaders h2 = new DefaultTextHeaders();
        h2.set("foo", "goo");
        h2.set("foo2", "goo2");

        assertTrue(h1.equals(h2));
        assertTrue(h2.equals(h1));
        assertTrue(h2.equals(h2));
        assertTrue(h1.equals(h1));
    }

    @Test
    public void testEqualsDuplicateMultipleHeaders() {
        DefaultTextHeaders h1 = new DefaultTextHeaders();
        h1.set("foo", "goo");
        h1.set("foo2", "goo2");
        h1.add("foo2", "goo3");
        h1.add("foo", "goo4");

        DefaultTextHeaders h2 = new DefaultTextHeaders();
        h2.set("foo", "goo");
        h2.set("foo2", "goo2");
        h2.add("foo", "goo4");
        h2.add("foo2", "goo3");

        assertTrue(h1.equals(h2));
        assertTrue(h2.equals(h1));
        assertTrue(h2.equals(h2));
        assertTrue(h1.equals(h1));
    }

    @Test
    public void testNotEqualsDuplicateMultipleHeaders() {
        DefaultTextHeaders h1 = new DefaultTextHeaders();
        h1.set("foo", "goo");
        h1.set("foo2", "goo2");
        h1.add("foo2", "goo3");
        h1.add("foo", "goo4");

        DefaultTextHeaders h2 = new DefaultTextHeaders();
        h2.set("foo", "goo");
        h2.set("foo2", "goo2");
        h2.add("foo", "goo4");

        assertFalse(h1.equals(h2));
        assertFalse(h2.equals(h1));
        assertTrue(h2.equals(h2));
        assertTrue(h1.equals(h1));
    }
}
