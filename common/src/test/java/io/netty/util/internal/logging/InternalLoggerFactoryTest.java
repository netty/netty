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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class InternalLoggerFactoryTest {
    private static final Exception e = new Exception();
    private InternalLoggerFactory oldLoggerFactory;
    private InternalLogger mockLogger;

    @Before
    public void init() {
        oldLoggerFactory = InternalLoggerFactory.getDefaultFactory();

        final InternalLoggerFactory mockFactory = mock(InternalLoggerFactory.class);
        mockLogger = mock(InternalLogger.class);
        when(mockFactory.newInstance("mock")).thenReturn(mockLogger);
        InternalLoggerFactory.setDefaultFactory(mockFactory);
    }

    @After
    public void destroy() {
        reset(mockLogger);
        InternalLoggerFactory.setDefaultFactory(oldLoggerFactory);
    }

    @Test(expected = NullPointerException.class)
    public void shouldNotAllowNullDefaultFactory() {
        InternalLoggerFactory.setDefaultFactory(null);
    }

    @Test
    public void shouldGetInstance() {
        InternalLoggerFactory.setDefaultFactory(oldLoggerFactory);

        String helloWorld = "Hello, world!";

        InternalLogger one = InternalLoggerFactory.getInstance("helloWorld");
        InternalLogger two = InternalLoggerFactory.getInstance(helloWorld.getClass());

        assertNotNull(one);
        assertNotNull(two);
        assertNotSame(one, two);
    }

    @Test
    public void testIsTraceEnabled() {
        when(mockLogger.isTraceEnabled()).thenReturn(true);

        InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        assertTrue(logger.isTraceEnabled());
        verify(mockLogger).isTraceEnabled();
    }

    @Test
    public void testIsDebugEnabled() {
        when(mockLogger.isDebugEnabled()).thenReturn(true);

        InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        assertTrue(logger.isDebugEnabled());
        verify(mockLogger).isDebugEnabled();
    }

    @Test
    public void testIsInfoEnabled() {
        when(mockLogger.isInfoEnabled()).thenReturn(true);

        InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        assertTrue(logger.isInfoEnabled());
        verify(mockLogger).isInfoEnabled();
    }

    @Test
    public void testIsWarnEnabled() {
        when(mockLogger.isWarnEnabled()).thenReturn(true);

        InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        assertTrue(logger.isWarnEnabled());
        verify(mockLogger).isWarnEnabled();
    }

    @Test
    public void testIsErrorEnabled() {
        when(mockLogger.isErrorEnabled()).thenReturn(true);

        InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        assertTrue(logger.isErrorEnabled());
        verify(mockLogger).isErrorEnabled();
    }

    @Test
    public void testTrace() {
        final InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.trace("a");
        verify(mockLogger).trace("a");
    }

    @Test
    public void testTraceWithException() {
        final InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.trace("a", e);
        verify(mockLogger).trace("a", e);
    }

    @Test
    public void testDebug() {
        final InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.debug("a");
        verify(mockLogger).debug("a");
    }

    @Test
    public void testDebugWithException() {
        final InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.debug("a", e);
        verify(mockLogger).debug("a", e);
    }

    @Test
    public void testInfo() {
        final InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.info("a");
        verify(mockLogger).info("a");
    }

    @Test
    public void testInfoWithException() {
        final InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.info("a", e);
        verify(mockLogger).info("a", e);
    }

    @Test
    public void testWarn() {
        final InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.warn("a");
        verify(mockLogger).warn("a");
    }

    @Test
    public void testWarnWithException() {
        final InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.warn("a", e);
        verify(mockLogger).warn("a", e);
    }

    @Test
    public void testError() {
        final InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.error("a");
        verify(mockLogger).error("a");
    }

    @Test
    public void testErrorWithException() {
        final InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.error("a", e);
        verify(mockLogger).error("a", e);
    }
}
