/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.util;

import static org.junit.Assert.*;

import org.junit.Test;


/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
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
