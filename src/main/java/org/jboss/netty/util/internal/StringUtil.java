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

import java.util.Formatter;

/**
 * String utility class.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public class StringUtil {

    private StringUtil() {
        // Unused.
    }

    public static final String NEWLINE;

    static {
        String newLine = null;

        try {
            newLine = new Formatter().format("%n").toString();
        } catch (Exception e) {
            newLine = "\n";
        }

        NEWLINE = newLine;
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
