/*
 * Copyright 2016 The Netty Project
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
package io.netty.util.internal.logging;

import org.junit.Test;
import org.apache.logging.log4j.Logger;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class Log4J2LoggerTest {
    private static final Exception e = new Exception();

    @Test
    public void testIsTraceEnabled() {
        Logger mock = mock(Logger.class);

        when(mock.getName()).thenReturn("foo");
        when(mock.isTraceEnabled()).thenReturn(true);
        InternalLogger logger = new Log4J2Logger(mock);
        assertTrue(logger.isTraceEnabled());

        verify(mock).getName();
        verify(mock).isTraceEnabled();
    }

    @Test
    public void testIsDebugEnabled() {
        Logger mock = mock(Logger.class);

        when(mock.getName()).thenReturn("foo");
        when(mock.isDebugEnabled()).thenReturn(true);

        InternalLogger logger = new Log4J2Logger(mock);
        assertTrue(logger.isDebugEnabled());

        verify(mock).getName();
        verify(mock).isDebugEnabled();
    }

    @Test
    public void testIsInfoEnabled() {
        Logger mock = mock(Logger.class);

        when(mock.getName()).thenReturn("foo");
        when(mock.isInfoEnabled()).thenReturn(true);

        InternalLogger logger = new Log4J2Logger(mock);
        assertTrue(logger.isInfoEnabled());

        verify(mock).getName();
        verify(mock).isInfoEnabled();
    }

    @Test
    public void testIsWarnEnabled() {
        Logger mock = mock(Logger.class);

        when(mock.getName()).thenReturn("foo");
        when(mock.isWarnEnabled()).thenReturn(true);

        InternalLogger logger = new Log4J2Logger(mock);
        assertTrue(logger.isWarnEnabled());

        verify(mock).getName();
        verify(mock).isWarnEnabled();
    }

    @Test
    public void testIsErrorEnabled() {
        Logger mock = mock(Logger.class);

        when(mock.getName()).thenReturn("foo");
        when(mock.isErrorEnabled()).thenReturn(true);

        InternalLogger logger = new Log4J2Logger(mock);
        assertTrue(logger.isErrorEnabled());

        verify(mock).getName();
        verify(mock).isErrorEnabled();
    }

    @Test
    public void testTrace() {
        Logger mock = mock(Logger.class);

        when(mock.getName()).thenReturn("foo");

        InternalLogger logger = new Log4J2Logger(mock);
        logger.trace("a");

        verify(mock).getName();
        verify(mock).trace("a");
    }

    @Test
    public void testTraceWithException() {
        Logger mock = mock(Logger.class);

        when(mock.getName()).thenReturn("foo");

        InternalLogger logger = new Log4J2Logger(mock);
        logger.trace("a", e);

        verify(mock).getName();
        verify(mock).trace("a", e);
    }

    @Test
    public void testDebug() {
        Logger mock = mock(Logger.class);

        when(mock.getName()).thenReturn("foo");

        InternalLogger logger = new Log4J2Logger(mock);
        logger.debug("a");

        verify(mock).getName();
        verify(mock).debug("a");
    }

    @Test
    public void testDebugWithException() {
        Logger mock = mock(Logger.class);

        when(mock.getName()).thenReturn("foo");

        InternalLogger logger = new Log4J2Logger(mock);
        logger.debug("a", e);

        verify(mock).getName();
        verify(mock).debug("a", e);
    }

    @Test
    public void testInfo() {
        Logger mock = mock(Logger.class);

        when(mock.getName()).thenReturn("foo");

        InternalLogger logger = new Log4J2Logger(mock);
        logger.info("a");

        verify(mock).getName();
        verify(mock).info("a");
    }

    @Test
    public void testInfoWithException() {
        Logger mock = mock(Logger.class);

        when(mock.getName()).thenReturn("foo");

        InternalLogger logger = new Log4J2Logger(mock);
        logger.info("a", e);

        verify(mock).getName();
        verify(mock).info("a", e);
    }

    @Test
    public void testWarn() {
        Logger mock = mock(Logger.class);

        when(mock.getName()).thenReturn("foo");

        InternalLogger logger = new Log4J2Logger(mock);
        logger.warn("a");

        verify(mock).getName();
        verify(mock).warn("a");
    }

    @Test
    public void testWarnWithException() {
        Logger mock = mock(Logger.class);

        when(mock.getName()).thenReturn("foo");

        InternalLogger logger = new Log4J2Logger(mock);
        logger.warn("a", e);

        verify(mock).getName();
        verify(mock).warn("a", e);
    }

    @Test
    public void testError() {
        Logger mock = mock(Logger.class);

        when(mock.getName()).thenReturn("foo");

        InternalLogger logger = new Log4J2Logger(mock);
        logger.error("a");

        verify(mock).getName();
        verify(mock).error("a");
    }

    @Test
    public void testErrorWithException() {
        Logger mock = mock(Logger.class);

        when(mock.getName()).thenReturn("foo");

        InternalLogger logger = new Log4J2Logger(mock);
        logger.error("a", e);

        verify(mock).getName();
        verify(mock).error("a", e);
    }
}
