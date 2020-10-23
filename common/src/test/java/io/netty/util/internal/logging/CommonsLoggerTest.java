/*
 * Copyright 2012 The Netty Project
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

import org.apache.commons.logging.Log;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CommonsLoggerTest {
    private static final Exception e = new Exception();

    @Test
    public void testIsTraceEnabled() {
        Log mockLog = mock(Log.class);

        when(mockLog.isTraceEnabled()).thenReturn(true);

        InternalLogger logger = new CommonsLogger(mockLog, "foo");
        assertTrue(logger.isTraceEnabled());

        verify(mockLog).isTraceEnabled();
    }

    @Test
    public void testIsDebugEnabled() {
        Log mockLog = mock(Log.class);

        when(mockLog.isDebugEnabled()).thenReturn(true);

        InternalLogger logger = new CommonsLogger(mockLog, "foo");
        assertTrue(logger.isDebugEnabled());

        verify(mockLog).isDebugEnabled();
    }

    @Test
    public void testIsInfoEnabled() {
        Log mockLog = mock(Log.class);

        when(mockLog.isInfoEnabled()).thenReturn(true);

        InternalLogger logger = new CommonsLogger(mockLog, "foo");
        assertTrue(logger.isInfoEnabled());

        verify(mockLog).isInfoEnabled();
    }

    @Test
    public void testIsWarnEnabled() {
        Log mockLog = mock(Log.class);

        when(mockLog.isWarnEnabled()).thenReturn(true);

        InternalLogger logger = new CommonsLogger(mockLog, "foo");
        assertTrue(logger.isWarnEnabled());

        verify(mockLog).isWarnEnabled();
    }

    @Test
    public void testIsErrorEnabled() {
        Log mockLog = mock(Log.class);

        when(mockLog.isErrorEnabled()).thenReturn(true);

        InternalLogger logger = new CommonsLogger(mockLog, "foo");
        assertTrue(logger.isErrorEnabled());

        verify(mockLog).isErrorEnabled();
    }

    @Test
    public void testTrace() {
        Log mockLog = mock(Log.class);

        InternalLogger logger = new CommonsLogger(mockLog, "foo");
        logger.trace("a");

        verify(mockLog).trace("a");
    }

    @Test
    public void testTraceWithException() {
        Log mockLog = mock(Log.class);

        InternalLogger logger = new CommonsLogger(mockLog, "foo");
        logger.trace("a", e);

        verify(mockLog).trace("a", e);
    }

    @Test
    public void testDebug() {
        Log mockLog = mock(Log.class);

        InternalLogger logger = new CommonsLogger(mockLog, "foo");
        logger.debug("a");

        verify(mockLog).debug("a");
    }

    @Test
    public void testDebugWithException() {
        Log mockLog = mock(Log.class);

        InternalLogger logger = new CommonsLogger(mockLog, "foo");
        logger.debug("a", e);

        verify(mockLog).debug("a", e);
    }

    @Test
    public void testInfo() {
        Log mockLog = mock(Log.class);

        InternalLogger logger = new CommonsLogger(mockLog, "foo");
        logger.info("a");

        verify(mockLog).info("a");
    }

    @Test
    public void testInfoWithException() {
        Log mockLog = mock(Log.class);

        InternalLogger logger = new CommonsLogger(mockLog, "foo");
        logger.info("a", e);

        verify(mockLog).info("a", e);
    }

    @Test
    public void testWarn() {
        Log mockLog = mock(Log.class);

        InternalLogger logger = new CommonsLogger(mockLog, "foo");
        logger.warn("a");

        verify(mockLog).warn("a");
    }

    @Test
    public void testWarnWithException() {
        Log mockLog = mock(Log.class);

        InternalLogger logger = new CommonsLogger(mockLog, "foo");
        logger.warn("a", e);

        verify(mockLog).warn("a", e);
    }

    @Test
    public void testError() {
        Log mockLog = mock(Log.class);

        InternalLogger logger = new CommonsLogger(mockLog, "foo");
        logger.error("a");

        verify(mockLog).error("a");
    }

    @Test
    public void testErrorWithException() {
        Log mockLog = mock(Log.class);

        InternalLogger logger = new CommonsLogger(mockLog, "foo");
        logger.error("a", e);

        verify(mockLog).error("a", e);
    }
}
