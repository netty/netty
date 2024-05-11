/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util.internal.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * We only need to test methods defined by {@link InternalLogger}.
 */
public abstract class AbstractInternalLoggerTest<T> {
    protected String loggerName = "foo";
    protected T mockLog;
    protected InternalLogger logger;
    protected final Map<String, Object> result = new HashMap<String, Object>();

    @SuppressWarnings("unchecked")
    protected <V> V getResult(String key) {
        return (V) result.get(key);
    }

    @Test
    public void testName() {
        assertEquals(loggerName, logger.name());
    }

    @Test
    public void testAllLevel() throws Exception {
        testLevel(InternalLogLevel.TRACE);
        testLevel(InternalLogLevel.DEBUG);
        testLevel(InternalLogLevel.INFO);
        testLevel(InternalLogLevel.WARN);
        testLevel(InternalLogLevel.ERROR);
    }

    protected void testLevel(InternalLogLevel level) throws Exception {
        result.clear();

        String format1 = "a={}", format2 = "a={}, b= {}", format3 = "a={}, b= {}, c= {}";
        String msg = "a test message from Junit";
        Exception ex = new Exception("a test Exception from Junit");

        Class<InternalLogger> clazz = InternalLogger.class;
        String levelName = level.name(), logMethod = levelName.toLowerCase();
        Method isXXEnabled = clazz
                .getMethod("is" + levelName.charAt(0) + levelName.substring(1).toLowerCase() + "Enabled");

        // when level log is disabled
        setLevelEnable(level, false);
        assertFalse((Boolean) isXXEnabled.invoke(logger));

        // test xx(msg)
        clazz.getMethod(logMethod, String.class).invoke(logger, msg);
        assertTrue(result.isEmpty());

        // test xx(format, arg)
        clazz.getMethod(logMethod, String.class, Object.class).invoke(logger, format1, msg);
        assertTrue(result.isEmpty());

        // test xx(format, argA, argB)
        clazz.getMethod(logMethod, String.class, Object.class, Object.class).invoke(logger, format2, msg, msg);
        assertTrue(result.isEmpty());

        // test xx(format, ...arguments)
        clazz.getMethod(logMethod, String.class, Object[].class).invoke(logger, format3,
                new Object[] { msg, msg, msg });
        assertTrue(result.isEmpty());

        // test xx(format, ...arguments), the last argument is Throwable
        clazz.getMethod(logMethod, String.class, Object[].class).invoke(logger, format3,
                new Object[] { msg, msg, msg, ex });
        assertTrue(result.isEmpty());

        // test xx(msg, Throwable)
        clazz.getMethod(logMethod, String.class, Throwable.class).invoke(logger, msg, ex);
        assertTrue(result.isEmpty());

        // test xx(Throwable)
        clazz.getMethod(logMethod, Throwable.class).invoke(logger, ex);
        assertTrue(result.isEmpty());

        // when level log is enabled
        setLevelEnable(level, true);
        assertTrue((Boolean) isXXEnabled.invoke(logger));

        // test xx(msg)
        result.clear();
        clazz.getMethod(logMethod, String.class).invoke(logger, msg);
        assertResult(level, null, null, msg);

        // test xx(format, arg)
        result.clear();
        clazz.getMethod(logMethod, String.class, Object.class).invoke(logger, format1, msg);
        assertResult(level, format1, null, msg);

        // test xx(format, argA, argB)
        result.clear();
        clazz.getMethod(logMethod, String.class, Object.class, Object.class).invoke(logger, format2, msg, msg);
        assertResult(level, format2, null, msg, msg);

        // test xx(format, ...arguments)
        result.clear();
        clazz.getMethod(logMethod, String.class, Object[].class).invoke(logger, format3,
                new Object[] { msg, msg, msg });
        assertResult(level, format3, null, msg, msg, msg);

        // test xx(format, ...arguments), the last argument is Throwable
        result.clear();
        clazz.getMethod(logMethod, String.class, Object[].class).invoke(logger, format3,
                new Object[] { msg, msg, msg, ex });
        assertResult(level, format3, ex, msg, msg, msg, ex);

        // test xx(msg, Throwable)
        result.clear();
        clazz.getMethod(logMethod, String.class, Throwable.class).invoke(logger, msg, ex);
        assertResult(level, null, ex, msg);

        // test xx(Throwable)
        result.clear();
        clazz.getMethod(logMethod, Throwable.class).invoke(logger, ex);
        assertResult(level, null, ex);
    }

    /** a just default code, you can override to fix {@linkplain #mockLog} */
    protected void assertResult(InternalLogLevel level, String format, Throwable t, Object... args) {
        assertFalse(result.isEmpty());
    }

    protected abstract void setLevelEnable(InternalLogLevel level, boolean enable) throws Exception;
}
