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

package io.netty.handler.codec.http2.draft10;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.junit.Test;


/**
 * Tests for {@link Http2Headers}.
 */
public class Http2HeadersTest {

    @Test
    public void duplicateKeysShouldStoreAllValues() {
        Http2Headers headers =
                Http2Headers.newBuilder().addHeader("a", "1").addHeader("a", "2")
                        .addHeader("a", "3").build();
        Collection<String> aValues = headers.getHeaders("a");
        assertEquals(3, aValues.size());
        Iterator<String> aValue = aValues.iterator();
        assertEquals("1", aValue.next());
        assertEquals("2", aValue.next());
        assertEquals("3", aValue.next());
    }

    @Test(expected = NoSuchElementException.class)
    public void iterateEmptyHeadersShouldThrow() {
        Iterator<Map.Entry<String, String>> iterator = Http2Headers.newBuilder().build().iterator();
        assertFalse(iterator.hasNext());
        iterator.next();
    }

    @Test
    public void iterateHeadersShouldReturnAllValues() {
        Set<String> headers = new HashSet<String>();
        headers.add("a:1");
        headers.add("a:2");
        headers.add("a:3");
        headers.add("b:1");
        headers.add("b:2");
        headers.add("c:1");

        // Build the headers from the input set.
        Http2Headers.Builder builder = Http2Headers.newBuilder();
        for (String header : headers) {
            String[] parts = header.split(":");
            builder.addHeader(parts[0], parts[1]);
        }

        // Now iterate through the headers, removing them from the original set.
        for (Map.Entry<String, String> entry : builder.build()) {
            assertTrue(headers.remove(entry.getKey() + ":" + entry.getValue()));
        }

        // Make sure we removed them all.
        assertTrue(headers.isEmpty());
    }
}
