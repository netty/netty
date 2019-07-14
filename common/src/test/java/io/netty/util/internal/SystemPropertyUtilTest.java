/*
 * Copyright 2017 The Netty Project
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
package io.netty.util.internal;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SystemPropertyUtilTest {

    @Before
    public void clearSystemPropertyBeforeEach() {
        System.clearProperty("key");
    }

    @Test(expected = NullPointerException.class)
    public void testGetWithKeyNull() {
        SystemPropertyUtil.get(null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetWithKeyEmpty() {
        SystemPropertyUtil.get("", null);
    }

    @Test
    public void testGetDefaultValueWithPropertyNull() {
        assertEquals("default", SystemPropertyUtil.get("key", "default"));
    }

    @Test
    public void testGetPropertyValue() {
        System.setProperty("key", "value");
        assertEquals("value", SystemPropertyUtil.get("key"));
    }

    @Test
    public void testGetBooleanDefaultValueWithPropertyNull() {
        assertTrue(SystemPropertyUtil.getBoolean("key", true));
        assertFalse(SystemPropertyUtil.getBoolean("key", false));
    }

    @Test
    public void testGetBooleanDefaultValueWithEmptyString() {
        System.setProperty("key", "");
        assertTrue(SystemPropertyUtil.getBoolean("key", true));
        assertFalse(SystemPropertyUtil.getBoolean("key", false));
    }

    @Test
    public void testGetBooleanWithTrueValue() {
        System.setProperty("key", "true");
        assertTrue(SystemPropertyUtil.getBoolean("key", false));
        System.setProperty("key", "yes");
        assertTrue(SystemPropertyUtil.getBoolean("key", false));
        System.setProperty("key", "1");
        assertTrue(SystemPropertyUtil.getBoolean("key", true));
    }

    @Test
    public void testGetBooleanWithFalseValue() {
        System.setProperty("key", "false");
        assertFalse(SystemPropertyUtil.getBoolean("key", true));
        System.setProperty("key", "no");
        assertFalse(SystemPropertyUtil.getBoolean("key", false));
        System.setProperty("key", "0");
        assertFalse(SystemPropertyUtil.getBoolean("key", true));
    }

    @Test
    public void testGetBooleanDefaultValueWithWrongValue() {
        System.setProperty("key", "abc");
        assertTrue(SystemPropertyUtil.getBoolean("key", true));
        System.setProperty("key", "123");
        assertFalse(SystemPropertyUtil.getBoolean("key", false));
    }

    @Test
    public void getIntDefaultValueWithPropertyNull() {
        assertEquals(1, SystemPropertyUtil.getInt("key", 1));
    }

    @Test
    public void getIntWithPropertValueIsInt() {
        System.setProperty("key", "123");
        assertEquals(123, SystemPropertyUtil.getInt("key", 1));
    }

    @Test
    public void getIntDefaultValueWithPropertValueIsNotInt() {
        System.setProperty("key", "NotInt");
        assertEquals(1, SystemPropertyUtil.getInt("key", 1));
    }

    @Test
    public void getLongDefaultValueWithPropertyNull() {
        assertEquals(1, SystemPropertyUtil.getLong("key", 1));
    }

    @Test
    public void getLongWithPropertValueIsLong() {
        System.setProperty("key", "123");
        assertEquals(123, SystemPropertyUtil.getLong("key", 1));
    }

    @Test
    public void getLongDefaultValueWithPropertValueIsNotLong() {
        System.setProperty("key", "NotInt");
        assertEquals(1, SystemPropertyUtil.getLong("key", 1));
    }

    @Test
    public void computePrefix() {
        // not relocated
        assertEquals("", SystemPropertyUtil
                .computePrefix("io.netty.util.internal.SystemPropertyUtil",
                               "io.netty.util.internal.SystemPropertyUtil"));
        // prepended suffix
        assertEquals("shaded.", SystemPropertyUtil
                .computePrefix("io.netty.util.internal.SystemPropertyUtil",
                               "shaded.io.netty.util.internal.SystemPropertyUtil"));
        // prepended suffix and changed root package name io.netty
        assertEquals("shadednetty.", SystemPropertyUtil
                .computePrefix("io.netty.util.internal.SystemPropertyUtil",
                               "shadednetty.util.internal.SystemPropertyUtil"));
    }

}
