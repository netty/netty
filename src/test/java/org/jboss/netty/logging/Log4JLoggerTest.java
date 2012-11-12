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
package org.jboss.netty.logging;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import org.apache.log4j.Logger;
import org.junit.Test;

public class Log4JLoggerTest {
    private static final Exception e = new Exception();

    @Test
    public void testIsDebugEnabled() {

        Logger mock =
            createStrictMock(Logger.class);

        expect(mock.isDebugEnabled()).andReturn(true);
        replay(mock);

        InternalLogger logger = new Log4JLogger(mock);
        assertTrue(logger.isDebugEnabled());
        verify(mock);
    }

    @Test
    public void testIsInfoEnabled() {
        Logger mock =
            createStrictMock(Logger.class);

        expect(mock.isInfoEnabled()).andReturn(true);
        replay(mock);

        InternalLogger logger = new Log4JLogger(mock);
        assertTrue(logger.isInfoEnabled());
        verify(mock);
    }

    @Test
    public void testIsWarnEnabled() {
        Logger mock =
            createStrictMock(Logger.class);

        replay(mock);

        InternalLogger logger = new Log4JLogger(mock);
        assertTrue(logger.isWarnEnabled());
        verify(mock);
    }

    @Test
    public void testIsErrorEnabled() {
        Logger mock =
            createStrictMock(Logger.class);
        replay(mock);

        InternalLogger logger = new Log4JLogger(mock);
        assertTrue(logger.isErrorEnabled());
        verify(mock);
    }

    @Test
    public void testDebug() {
        Logger mock =
            createStrictMock(Logger.class);

        mock.debug("a");
        replay(mock);

        InternalLogger logger = new Log4JLogger(mock);
        logger.debug("a");
        verify(mock);
    }

    @Test
    public void testDebugWithException() {
        Logger mock =
            createStrictMock(Logger.class);

        mock.debug("a", e);
        replay(mock);

        InternalLogger logger = new Log4JLogger(mock);
        logger.debug("a", e);
        verify(mock);
    }

    @Test
    public void testInfo() {
        Logger mock =
            createStrictMock(Logger.class);

        mock.info("a");
        replay(mock);

        InternalLogger logger = new Log4JLogger(mock);
        logger.info("a");
        verify(mock);
    }

    @Test
    public void testInfoWithException() {
        Logger mock =
            createStrictMock(Logger.class);

        mock.info("a", e);
        replay(mock);

        InternalLogger logger = new Log4JLogger(mock);
        logger.info("a", e);
        verify(mock);
    }

    @Test
    public void testWarn() {
        Logger mock =
            createStrictMock(Logger.class);

        mock.warn("a");
        replay(mock);

        InternalLogger logger = new Log4JLogger(mock);
        logger.warn("a");
        verify(mock);
    }

    @Test
    public void testWarnWithException() {
        Logger mock =
            createStrictMock(Logger.class);

        mock.warn("a", e);
        replay(mock);

        InternalLogger logger = new Log4JLogger(mock);
        logger.warn("a", e);
        verify(mock);
    }

    @Test
    public void testError() {
        Logger mock =
            createStrictMock(Logger.class);

        mock.error("a");
        replay(mock);

        InternalLogger logger = new Log4JLogger(mock);
        logger.error("a");
        verify(mock);
    }

    @Test
    public void testErrorWithException() {
        Logger mock =
            createStrictMock(Logger.class);

        mock.error("a", e);
        replay(mock);

        InternalLogger logger = new Log4JLogger(mock);
        logger.error("a", e);
        verify(mock);
    }
}
