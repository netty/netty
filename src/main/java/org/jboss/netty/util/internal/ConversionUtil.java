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

import java.util.ArrayList;
import java.util.List;

/**
 * Conversion utility class to parse a property represented as a string or
 * an object.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 *
 */
public class ConversionUtil {

    /**
     * Converts the specified object into an integer.
     */
    public static int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else {
            return Integer.parseInt(String.valueOf(value));
        }
    }

    /**
     * Converts the specified object into a boolean.
     */
    public static boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        } else {
            String s = String.valueOf(value);
            if (s.length() == 0) {
                return false;
            }

            try {
                return Integer.parseInt(s) != 0;
            } catch (NumberFormatException e) {
                // Proceed
            }

            switch (Character.toUpperCase(s.charAt(0))) {
            case 'T': case 'Y':
                return true;
            }
            return false;
        }
    }

    /**
     * Converts the specified object into an array of strings.
     */
    public static String[] toStringArray(Object value) {
        if (value instanceof String[]) {
            return (String[]) value;
        }

        if (value instanceof Iterable<?>) {
            List<String> answer = new ArrayList<String>();
            for (Object v: (Iterable<?>) value) {
                if (v == null) {
                    answer.add(null);
                } else {
                    answer.add(String.valueOf(v));
                }
            }
            return answer.toArray(new String[answer.size()]);
        }

        return String.valueOf(value).split("[, \\t\\n\\r\\f\\e\\a]");
    }

    private static final String[] INTEGERS = {
        "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
        "10","11","12","13","14","15",
    };

    public static String toString(int value) {
        if (value >= 0 && value < INTEGERS.length) {
            return INTEGERS[value];
        } else {
            return Integer.toString(value);
        }
    }

    private ConversionUtil() {
        // Unused
    }
}
