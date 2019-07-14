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

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * A collection of utility methods to retrieve and parse the values of the Java system properties.
 * This library takes into account the fact that Netty classes are usually repackaged and relocated.
 */
public final class SystemPropertyUtil {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SystemPropertyUtil.class);

    private static final String SYSTEM_PROPERTIES_PREFIX =
            computePrefix("io.netty.util.internal.SystemPropertyUtil", SystemPropertyUtil.class.getName());

    /**
     * Try to detect that Netty has been relocated to a different package name.
     * In that case we compute a prefix to prepend to every Netty system property.
     * When you are relocating Netty classes you want an isolated version of Netty
     * with its own set of settings. As Netty is configurable with System properties
     * we have to provide a way to shield the relocated version of Netty by system wide settings.
     *
     * @param originalName the original name of the class inside Netty codebase.
     * @param currentName the actual name of this class, that could have been repackaged with a different package name.
     * @return the prefix, empty string in case we are running the original version of Netty.
     */
    static String computePrefix(final String originalName, final String currentName) {
        if (originalName.equals(currentName)) {
            return "";
        }
        boolean usePrefix = Boolean.parseBoolean(get0("", "io.netty.internal.detectRelocation", "true"));
        if (!usePrefix) {
            logger.warn("Detected a relocated version of Netty ({}), "
                    + "but io.netty.internal.detectRelocation is disabled" , currentName);
            return "";
        }
        // strip the original name, and try to keep the prefix injected by the relocation
        // "io.netty.util.internal.SystemPropertyUtil" -> ""
        // "mylib.io.netty.util.internal.SystemPropertyUtil" -> "mylib."
        // "mylib.nettyshaded.util.internal.SystemPropertyUtil" -> "mylib.nettyshaded."
        String result = currentName
                .replace(originalName, "") // common case
                .replace("io.netty.", "")
                .replace("util.internal.", "")
                .replace("SystemPropertyUtil", "");
        if (!result.isEmpty()) {
            logger.warn("Detected a relocated version of netty, "
                    + "prefix '{}' will be prepended to every system property for this instance of Netty", result);
        }
        return result;
    }

    /**
     * Compute the actual name of a system property that is in use.
     * This method is used while logging system property values.
     *
     * @param key
     *
     * @return the key prepended by {@link #SYSTEM_PROPERTIES_PREFIX}
     * @see #computePrefix(java.lang.String, java.lang.String)
     */
    public static String propertyName(String key) {
        if (isNettyProperty(key)) {
            return SYSTEM_PROPERTIES_PREFIX + key;
        } else {
            return key;
        }
    }

    /**
     * Returns {@code true} if and only if the system property with the specified {@code key}
     * exists.
     */
    public static boolean contains(String key) {
        return get(key) != null;
    }

    /**
     * Returns the value of the Java system property with the specified
     * {@code key}, while falling back to {@code null} if the property access fails.
     *
     * @return the property value or {@code null}
     */
    public static String get(String key) {
        return get(key, null);
    }

    /**
     * Returns the value of the Java system property with the specified
     * {@code key}, while falling back to the specified default value if
     * the property access fails.
     * @param key the name of the property.
     * @param def the default value.
     * @return the property value.
     *         {@code def} if there's no such property or if an access to the
     *         specified property is not allowed.
     */
    public static String get(final String key, final String def) {
        return get0(SYSTEM_PROPERTIES_PREFIX, key, def);
    }

    private static boolean isNettyProperty(String key) {
        // we have to handle only Netty properties, not stuff like "java.net.preferIPv4Stack" or "os.name"
        return key.startsWith("io.netty");
    }

    private static String get0(final String prefix, final String key, String def) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        if (prefix == null) {
            throw new NullPointerException("prefix");
        }
        if (key.isEmpty()) {
            throw new IllegalArgumentException("key must not be empty.");
        }
        boolean isNettyProperty = isNettyProperty(key);
        final String prefixedKey = isNettyProperty ? prefix + key : key;
        String value = null;
        try {
            if (System.getSecurityManager() == null) {
                value = System.getProperty(prefixedKey);
            } else {
                value = AccessController.doPrivileged(new PrivilegedAction<String>() {
                    @Override
                    public String run() {
                        return System.getProperty(prefixedKey);
                    }
                });
            }
        } catch (SecurityException e) {
            logger.warn("Unable to retrieve a system property '{}'; default values will be used.", prefixedKey, e);
        }

        if (value == null) {
            return def;
        }

        return value;
    }

    /**
     * Returns the value of the Java system property with the specified
     * {@code key}, while falling back to the specified default value if
     * the property access fails.
     *
     * @return the property value.
     *         {@code def} if there's no such property or if an access to the
     *         specified property is not allowed.
     */
    public static boolean getBoolean(String key, boolean def) {
        String value = get(key);
        if (value == null) {
            return def;
        }

        value = value.trim().toLowerCase();
        if (value.isEmpty()) {
            return def;
        }

        if ("true".equals(value) || "yes".equals(value) || "1".equals(value)) {
            return true;
        }

        if ("false".equals(value) || "no".equals(value) || "0".equals(value)) {
            return false;
        }

        logger.warn(
                "Unable to parse the boolean system property '{}':{} - using the default value: {}",
                key, value, def
        );

        return def;
    }

    /**
     * Returns the value of the Java system property with the specified
     * {@code key}, while falling back to the specified default value if
     * the property access fails.
     *
     * @return the property value.
     *         {@code def} if there's no such property or if an access to the
     *         specified property is not allowed.
     */
    public static int getInt(String key, int def) {
        String value = get(key);
        if (value == null) {
            return def;
        }

        value = value.trim();
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            // Ignore
        }

        logger.warn(
                "Unable to parse the integer system property '{}':{} - using the default value: {}",
                key, value, def
        );

        return def;
    }

    /**
     * Returns the value of the Java system property with the specified
     * {@code key}, while falling back to the specified default value if
     * the property access fails.
     *
     * @return the property value.
     *         {@code def} if there's no such property or if an access to the
     *         specified property is not allowed.
     */
    public static long getLong(String key, long def) {
        String value = get(key);
        if (value == null) {
            return def;
        }

        value = value.trim();
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            // Ignore
        }

        logger.warn(
                "Unable to parse the long integer system property '{}':{} - using the default value: {}",
                key, value, def
        );

        return def;
    }

    private SystemPropertyUtil() {
        // Unused
    }
}
