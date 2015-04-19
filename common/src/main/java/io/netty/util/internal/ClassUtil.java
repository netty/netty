/*
 * Copyright 2012 The Netty Project
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Operates on classes without using reflection.
 *
 * This class handles invalid {@code null} inputs as best it can.
 * Each method documents its behaviour in more detail.
 *
 * The notion of a {@code canonical name} includes the human
 * readable name for the type, for example {@code int[]}. The
 * non-canonical method variants work with the JVM names, such as
 * {@code [I}.
 */
public final class ClassUtil {

    /**
     * The package separator character: <code>'&#x2e;' == {@value}.
     */
    public static final char PACKAGE_SEPARATOR_CHAR = '.';

    /**
     * The inner class separator character: <code>'$' == {@value}.
     */
    public static final char INNER_CLASS_SEPARATOR_CHAR = '$';

    /**
     * Maps an abbreviation used in array class names to corresponding primitive class name.
     */
    private static final Map<String, String> reverseAbbreviationMap;

    /**
     * Feed abbreviation maps
     */
    static {
        final Map<String, String> r = new HashMap<String, String>();
        r.put("I", "int");
        r.put("Z", "boolean");
        r.put("F", "float");
        r.put("J", "long");
        r.put("S", "short");
        r.put("B", "byte");
        r.put("D", "double");
        r.put("C", "char");
        r.put("V", "void");
        reverseAbbreviationMap = Collections.unmodifiableMap(r);
    }

    /**
     * Gets the canonical name minus the package name from a String.
     * The string passed in is assumed to be a canonical name - it is not checked.
     */
    public static String getShortCanonicalName(final String canonicalName) {
        return ClassUtil.getShortClassName(getCanonicalName(canonicalName));
    }

    /**
     * Gets the class name minus the package name from a String.
     * The string passed in is assumed to be a class name - it is not checked.
     * Note that this method differs from Class.getSimpleName() in that this will
     * return {@code "Map.Entry"} whilst the {@code java.lang.Class} variant will simply
     * return {@code "Entry"}.
     */
    public static String getShortClassName(String className) {
        if (StringUtil.isEmpty(className)) {
            return StringUtil.EMPTY_STRING;
        }

        final StringBuilder arrayPrefix = new StringBuilder();

        // Handle array encoding
        if (className.startsWith("[")) {
            while (className.charAt(0) == '[') {
                className = className.substring(1);
                arrayPrefix.append("[]");
            }
            // Strip Object type encoding
            if (className.charAt(0) == 'L' && className.charAt(className.length() - 1) == ';') {
                className = className.substring(1, className.length() - 1);
            }

            if (reverseAbbreviationMap.containsKey(className)) {
                className = reverseAbbreviationMap.get(className);
            }
        }

        final int lastDotIdx = className.lastIndexOf(PACKAGE_SEPARATOR_CHAR);
        final int innerIdx = className.indexOf(
                INNER_CLASS_SEPARATOR_CHAR, lastDotIdx == -1 ? 0 : lastDotIdx + 1);
        String out = className.substring(lastDotIdx + 1);
        if (innerIdx != -1) {
            out = out.replace(INNER_CLASS_SEPARATOR_CHAR, PACKAGE_SEPARATOR_CHAR);
        }
        return out + arrayPrefix;
    }

    /**
     * Converts a given name of class into canonical format.
     * If name of class is not a name of array class it returns
     * unchanged name.
     * Example:
     * {@code getCanonicalName("[I") = "int[]"}
     * {@code getCanonicalName("[Ljava.lang.String;") = "java.lang.String[]"}
     * {@code getCanonicalName("java.lang.String") = "java.lang.String"}
     */
    private static String getCanonicalName(String className) {
        className = StringUtil.deleteWhitespace(className);
        if (className == null) {
            return null;
        }
        int dim = 0;
        while (className.startsWith("[")) {
            dim++;
            className = className.substring(1);
        }
        if (dim < 1) {
            return className;
        }
        if (className.startsWith("L")) {
            className = className.substring(
                1,
                className.endsWith(";")
                    ? className.length() - 1
                    : className.length());
        } else {
            if (className.length() > 0) {
                className = reverseAbbreviationMap.get(className.substring(0, 1));
            }
        }
        final StringBuilder canonicalClassNameBuffer = new StringBuilder(className);
        for (int i = 0; i < dim; i++) {
            canonicalClassNameBuffer.append("[]");
        }
        return canonicalClassNameBuffer.toString();
    }

    private ClassUtil() {
        // Unused.
    }
}
