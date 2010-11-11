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
package org.jboss.netty.logging;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import org.junit.Test;


/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 *
 */
public class Slf4JLoggerTest {
    private static final Exception e = new Exception();

    @Test
    public void testIsDebugEnabled() {
        org.slf4j.Logger mock =
            createStrictMock(org.slf4j.Logger.class);

        expect(mock.isDebugEnabled()).andReturn(true);
        replay(mock);

        InternalLogger logger = new Slf4JLogger(mock);
        assertTrue(logger.isDebugEnabled());
        verify(mock);
    }

    @Test
    public void testIsInfoEnabled() {
        org.slf4j.Logger mock =
            createStrictMock(org.slf4j.Logger.class);

        expect(mock.isInfoEnabled()).andReturn(true);
        replay(mock);

        InternalLogger logger = new Slf4JLogger(mock);
        assertTrue(logger.isInfoEnabled());
        verify(mock);
    }

    @Test
    public void testIsWarnEnabled() {
        org.slf4j.Logger mock =
            createStrictMock(org.slf4j.Logger.class);

        expect(mock.isWarnEnabled()).andReturn(true);
        replay(mock);

        InternalLogger logger = new Slf4JLogger(mock);
        assertTrue(logger.isWarnEnabled());
        verify(mock);
    }

    @Test
    public void testIsErrorEnabled() {
        org.slf4j.Logger mock =
            createStrictMock(org.slf4j.Logger.class);

        expect(mock.isErrorEnabled()).andReturn(true);
        replay(mock);

        InternalLogger logger = new Slf4JLogger(mock);
        assertTrue(logger.isErrorEnabled());
        verify(mock);
    }

    @Test
    public void testDebug() {
        org.slf4j.Logger mock =
            createStrictMock(org.slf4j.Logger.class);

        mock.debug("a");
        replay(mock);

        InternalLogger logger = new Slf4JLogger(mock);
        logger.debug("a");
        verify(mock);
    }

    @Test
    public void testDebugWithException() {
        org.slf4j.Logger mock =
            createStrictMock(org.slf4j.Logger.class);

        mock.debug("a", e);
        replay(mock);

        InternalLogger logger = new Slf4JLogger(mock);
        logger.debug("a", e);
        verify(mock);
    }

    @Test
    public void testInfo() {
        org.slf4j.Logger mock =
            createStrictMock(org.slf4j.Logger.class);

        mock.info("a");
        replay(mock);

        InternalLogger logger = new Slf4JLogger(mock);
        logger.info("a");
        verify(mock);
    }

    @Test
    public void testInfoWithException() {
        org.slf4j.Logger mock =
            createStrictMock(org.slf4j.Logger.class);

        mock.info("a", e);
        replay(mock);

        InternalLogger logger = new Slf4JLogger(mock);
        logger.info("a", e);
        verify(mock);
    }

    @Test
    public void testWarn() {
        org.slf4j.Logger mock =
            createStrictMock(org.slf4j.Logger.class);

        mock.warn("a");
        replay(mock);

        InternalLogger logger = new Slf4JLogger(mock);
        logger.warn("a");
        verify(mock);
    }

    @Test
    public void testWarnWithException() {
        org.slf4j.Logger mock =
            createStrictMock(org.slf4j.Logger.class);

        mock.warn("a", e);
        replay(mock);

        InternalLogger logger = new Slf4JLogger(mock);
        logger.warn("a", e);
        verify(mock);
    }

    @Test
    public void testError() {
        org.slf4j.Logger mock =
            createStrictMock(org.slf4j.Logger.class);

        mock.error("a");
        replay(mock);

        InternalLogger logger = new Slf4JLogger(mock);
        logger.error("a");
        verify(mock);
    }

    @Test
    public void testErrorWithException() {
        org.slf4j.Logger mock =
            createStrictMock(org.slf4j.Logger.class);

        mock.error("a", e);
        replay(mock);

        InternalLogger logger = new Slf4JLogger(mock);
        logger.error("a", e);
        verify(mock);
    }
}
