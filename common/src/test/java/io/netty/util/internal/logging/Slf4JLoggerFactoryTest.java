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
package io.netty.util.internal.logging;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.spi.LocationAwareLogger;

import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class Slf4JLoggerFactoryTest {

    @Test
    public void testCreation() {
        InternalLogger logger = Slf4JLoggerFactory.INSTANCE.newInstance("foo");
        assertTrue(logger instanceof Slf4JLogger || logger instanceof LocationAwareSlf4JLogger);
        assertEquals("foo", logger.name());
    }

    @Test
    public void testCreationLogger() {
        Logger logger = mock(Logger.class);
        when(logger.getName()).thenReturn("testlogger");
        InternalLogger internalLogger = Slf4JLoggerFactory.wrapLogger(logger);
        assertTrue(internalLogger instanceof Slf4JLogger);
        assertEquals("testlogger", internalLogger.name());
    }

    @Test
    public void testCreationLocationAwareLogger() {
        Logger logger = mock(LocationAwareLogger.class);
        when(logger.getName()).thenReturn("testlogger");
        InternalLogger internalLogger = Slf4JLoggerFactory.wrapLogger(logger);
        assertTrue(internalLogger instanceof LocationAwareSlf4JLogger);
        assertEquals("testlogger", internalLogger.name());
    }

    @Test
    public void testFormatMessage() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        LocationAwareLogger logger = mock(LocationAwareLogger.class);
        when(logger.isDebugEnabled()).thenReturn(true);
        when(logger.isErrorEnabled()).thenReturn(true);
        when(logger.isInfoEnabled()).thenReturn(true);
        when(logger.isTraceEnabled()).thenReturn(true);
        when(logger.isWarnEnabled()).thenReturn(true);
        when(logger.getName()).thenReturn("testlogger");

        InternalLogger internalLogger = Slf4JLoggerFactory.wrapLogger(logger);
        internalLogger.debug("{}", "debug");
        internalLogger.debug("{} {}", "debug1", "debug2");

        internalLogger.error("{}", "error");
        internalLogger.error("{} {}", "error1", "error2");

        internalLogger.info("{}", "info");
        internalLogger.info("{} {}", "info1", "info2");

        internalLogger.trace("{}", "trace");
        internalLogger.trace("{} {}", "trace1", "trace2");

        internalLogger.warn("{}", "warn");
        internalLogger.warn("{} {}", "warn1", "warn2");

        verify(logger, times(2)).log(ArgumentMatchers.<Marker>isNull(), eq(LocationAwareSlf4JLogger.FQCN),
                eq(LocationAwareLogger.DEBUG_INT), captor.capture(), any(Object[].class),
                ArgumentMatchers.<Throwable>isNull());
        verify(logger, times(2)).log(ArgumentMatchers.<Marker>isNull(), eq(LocationAwareSlf4JLogger.FQCN),
                eq(LocationAwareLogger.ERROR_INT), captor.capture(), any(Object[].class),
                ArgumentMatchers.<Throwable>isNull());
        verify(logger, times(2)).log(ArgumentMatchers.<Marker>isNull(), eq(LocationAwareSlf4JLogger.FQCN),
                eq(LocationAwareLogger.INFO_INT), captor.capture(), any(Object[].class),
                ArgumentMatchers.<Throwable>isNull());
        verify(logger, times(2)).log(ArgumentMatchers.<Marker>isNull(), eq(LocationAwareSlf4JLogger.FQCN),
                eq(LocationAwareLogger.TRACE_INT), captor.capture(), any(Object[].class),
                ArgumentMatchers.<Throwable>isNull());
        verify(logger, times(2)).log(ArgumentMatchers.<Marker>isNull(), eq(LocationAwareSlf4JLogger.FQCN),
                eq(LocationAwareLogger.WARN_INT), captor.capture(), any(Object[].class),
                ArgumentMatchers.<Throwable>isNull());

        Iterator<String> logMessages = captor.getAllValues().iterator();
        assertEquals("debug", logMessages.next());
        assertEquals("debug1 debug2", logMessages.next());
        assertEquals("error", logMessages.next());
        assertEquals("error1 error2", logMessages.next());
        assertEquals("info", logMessages.next());
        assertEquals("info1 info2", logMessages.next());
        assertEquals("trace", logMessages.next());
        assertEquals("trace1 trace2", logMessages.next());
        assertEquals("warn", logMessages.next());
        assertEquals("warn1 warn2", logMessages.next());
        assertFalse(logMessages.hasNext());
    }
}
