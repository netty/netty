/*
 * Copyright 2015 The Netty Project
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

import org.junit.Test;

import java.util.List;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

public class DefaultHttpHeadersTest {

    @Test
    public void keysShouldBeCaseInsensitive() {
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        headers.add("Name", "value1");
        headers.add("name", "value2");
        headers.add("NAME", "value3");
        assertEquals(3, headers.size());

        List<String> values = asList("value1", "value2", "value3");

        assertEquals(values, headers.getAll("NAME"));
        assertEquals(values, headers.getAll("name"));
        assertEquals(values, headers.getAll("Name"));
        assertEquals(values, headers.getAll("nAmE"));
    }

    @Test
    public void keysShouldBeCaseInsensitiveInHeadersEquals() {
        DefaultHttpHeaders headers1 = new DefaultHttpHeaders();
        headers1.add("name1", "value1", "value2", "value3");
        headers1.add("nAmE2", "value4");

        DefaultHttpHeaders headers2 = new DefaultHttpHeaders();
        headers2.add("naMe1", "value1", "value2", "value3");
        headers2.add("NAME2", "value4");

        assertEquals(headers1, headers1);
        assertEquals(headers2, headers2);
        assertEquals(headers1, headers2);
        assertEquals(headers2, headers1);
        assertEquals(headers1.hashCode(), headers2.hashCode());
    }
}
