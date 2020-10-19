/*
 * Copyright 2015 The Netty Project
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
package io.netty.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class AttributeKeyTest {

    @Test
    public void testExists() {
        String name = "test";
        assertFalse(AttributeKey.exists(name));
        AttributeKey<String> attr = AttributeKey.valueOf(name);

        assertTrue(AttributeKey.exists(name));
        assertNotNull(attr);
    }

    @Test
    public void testValueOf() {
        String name = "test1";
        assertFalse(AttributeKey.exists(name));
        AttributeKey<String> attr = AttributeKey.valueOf(name);
        AttributeKey<String> attr2 = AttributeKey.valueOf(name);

        assertSame(attr, attr2);
    }

    @Test
    public void testNewInstance() {
        String name = "test2";
        assertFalse(AttributeKey.exists(name));
        AttributeKey<String> attr = AttributeKey.newInstance(name);
        assertTrue(AttributeKey.exists(name));
        assertNotNull(attr);

        try {
            AttributeKey.<String>newInstance(name);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }
}
