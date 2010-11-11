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
 * Unit test for {@link StringUtil}.
 * <p/>
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author Daniel Bevenius (dbevenius@jboss.com)
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
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
