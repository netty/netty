/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
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
package org.jboss.netty.util.internal;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Unit test for {@link StringUtil}.
 * <p/>
 * 
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Daniel Bevenius (dbevenius@jboss.com)
 * @version $Rev$, $Date$
 */
public class StringUtilTest {

    @Test
    public void stripControlCharactersObjectNull() {
        assertNull(StringUtil.stripControlCharacters(null));
    }

    @Test
    public void stripControlCharactersNull() {
        assertNull(StringUtil.stripControlCharacters((String) null));
    }

    @Test
    public void stripControlCharactersRightTrim() {
        final char controlCode = 0x0000;
        final Object object = "abbb" + controlCode;
        assertEquals(5, ((String) object).length());

        final String stripped = StringUtil.stripControlCharacters(object);
        assertFalse(object.equals(stripped));
        assertEquals(4, stripped.length());
    }

    @Test
    public void stripControlCharactersLeftTrim() {
        final char controlCode = 0x0000;
        final String string = controlCode + "abbb";
        assertEquals(5, string.length());

        final String stripped = StringUtil.stripControlCharacters(string);
        assertFalse(string.equals(stripped));
        assertEquals(4, stripped.length());
    }

    @Test
    public void stripControlCharacters() {
        for (char i = 0x0000; i <= 0x001F; i ++) {
            assertStripped(i);
        }
        for (char i = 0x007F; i <= 0x009F; i ++) {
            assertStripped(i);
        }
    }

    private void assertStripped(final char controlCode) {
        final Object object = "aaa" + controlCode + "bbb";
        final String stripped = StringUtil.stripControlCharacters(object);
        assertEquals("aaa bbb", stripped);
    }

    @Test
    public void stripNonControlCharacter() {
        final char controlCode = 0x002F;
        final String string = controlCode + "abbb";
        final String stripped = StringUtil.stripControlCharacters(string);
        assertEquals("The string should be unchanged", string, stripped);
    }
}
