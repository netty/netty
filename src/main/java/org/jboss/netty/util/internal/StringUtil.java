/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.netty.util.internal;

/**
 * String utility class.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public class StringUtil {

    private StringUtil() {
        // Unused.
    }

    /**
     * Strip an Object of it's ISO control characters.
     *
     * @param value
     *          The Object that should be stripped. This objects toString method will
     *          called and the result passed to {@link #stripControlCharacters(String)}.
     * @return {@code String}
     *          A new String instance with its hexadecimal control characters replaced
     *          by a space. Or the unmodified String if it does not contain any ISO
     *          control characters.
     */
    public static String stripControlCharacters(Object value) {
        if (value == null) {
            return null;
        }

        return stripControlCharacters(value.toString());
    }

    /**
     * Strip a String of it's ISO control characters.
     *
     * @param value
     *          The String that should be stripped.
     * @return {@code String}
     *          A new String instance with its hexadecimal control characters replaced
     *          by a space. Or the unmodified String if it does not contain any ISO
     *          control characters.
     */
    public static String stripControlCharacters(String value) {
        if (value == null) {
            return null;
        }

        boolean hasControlChars = false;
        for (int i = value.length() - 1; i >= 0; i --) {
            if (Character.isISOControl(value.charAt(i))) {
                hasControlChars = true;
                break;
            }
        }

        if (!hasControlChars) {
            return value;
        }

        StringBuilder buf = new StringBuilder(value.length());
        int i = 0;

        // Skip initial control characters (i.e. left trim)
        for (; i < value.length(); i ++) {
            if (!Character.isISOControl(value.charAt(i))) {
                break;
            }
        }

        // Copy non control characters and substitute control characters with
        // a space.  The last control characters are trimmed.
        boolean suppressingControlChars = false;
        for (; i < value.length(); i ++) {
            if (Character.isISOControl(value.charAt(i))) {
                suppressingControlChars = true;
                continue;
            } else {
                if (suppressingControlChars) {
                    suppressingControlChars = false;
                    buf.append(' ');
                }
                buf.append(value.charAt(i));
            }
        }

        return buf.toString();
    }
}
