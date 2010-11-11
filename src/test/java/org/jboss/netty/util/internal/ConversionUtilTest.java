/*
 * Copyright 2009 Red Hat, Inc.
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
package org.jboss.netty.util.internal;

import static org.junit.Assert.*;

import org.junit.Test;


/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 *
 */
public class ConversionUtilTest {

    @Test
    public void testNumberToInt() {
        assertEquals(42, ConversionUtil.toInt(Long.valueOf(42)));
    }

    @Test
    public void testStringToInt() {
        assertEquals(42, ConversionUtil.toInt("42"));
    }

    @Test
    public void testBooleanToBoolean() {
        assertTrue(ConversionUtil.toBoolean(Boolean.TRUE));
        assertFalse(ConversionUtil.toBoolean(Boolean.FALSE));
    }

    @Test
    public void testNumberToBoolean() {
        assertTrue(ConversionUtil.toBoolean(Integer.valueOf(42)));
        assertFalse(ConversionUtil.toBoolean(Integer.valueOf(0)));
    }

    @Test
    public void testStringToBoolean() {
        assertTrue(ConversionUtil.toBoolean("y"));
        assertTrue(ConversionUtil.toBoolean("Y"));
        assertTrue(ConversionUtil.toBoolean("yes"));
        assertTrue(ConversionUtil.toBoolean("YES"));
        assertTrue(ConversionUtil.toBoolean("yeah"));
        assertTrue(ConversionUtil.toBoolean("YEAH"));
        assertTrue(ConversionUtil.toBoolean("t"));
        assertTrue(ConversionUtil.toBoolean("T"));
        assertTrue(ConversionUtil.toBoolean("true"));
        assertTrue(ConversionUtil.toBoolean("TRUE"));
        assertTrue(ConversionUtil.toBoolean("42"));

        assertFalse(ConversionUtil.toBoolean(""));
        assertFalse(ConversionUtil.toBoolean("n"));
        assertFalse(ConversionUtil.toBoolean("no"));
        assertFalse(ConversionUtil.toBoolean("NO"));
        assertFalse(ConversionUtil.toBoolean("f"));
        assertFalse(ConversionUtil.toBoolean("false"));
        assertFalse(ConversionUtil.toBoolean("FALSE"));
        assertFalse(ConversionUtil.toBoolean("0"));
    }
}
