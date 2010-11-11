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
import static org.easymock.classextension.EasyMock.*;
import static org.junit.Assert.*;

import org.junit.Test;


/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 *
 */
public class JBossLoggerTest {
    private static final Exception e = new Exception();

    @Test
    @SuppressWarnings("deprecation")
    public void testIsDebugEnabled() {
        org.jboss.logging.Logger mock =
            createStrictMock(org.jboss.logging.Logger.class);

        expect(mock.isDebugEnabled()).andReturn(true);
        replay(mock);

        InternalLogger logger = new JBossLogger(mock);
        assertTrue(logger.isDebugEnabled());
        verify(mock);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testIsInfoEnabled() {
        org.jboss.logging.Logger mock =
            createStrictMock(org.jboss.logging.Logger.class);

        expect(mock.isInfoEnabled()).andReturn(true);
        replay(mock);

        InternalLogger logger = new JBossLogger(mock);
        assertTrue(logger.isInfoEnabled());
        verify(mock);
    }

    @Test
    public void testIsWarnEnabled() {
        org.jboss.logging.Logger mock =
            createStrictMock(org.jboss.logging.Logger.class);
        replay(mock);

        InternalLogger logger = new JBossLogger(mock);
        assertTrue(logger.isWarnEnabled());
        verify(mock);
    }

    @Test
    public void testIsErrorEnabled() {
        org.jboss.logging.Logger mock =
            createStrictMock(org.jboss.logging.Logger.class);
        replay(mock);

        InternalLogger logger = new JBossLogger(mock);
        assertTrue(logger.isErrorEnabled());
        verify(mock);
    }

    @Test
    public void testDebug() {
        org.jboss.logging.Logger mock =
            createStrictMock(org.jboss.logging.Logger.class);

        mock.debug("a");
        replay(mock);

        InternalLogger logger = new JBossLogger(mock);
        logger.debug("a");
        verify(mock);
    }

    @Test
    public void testDebugWithException() {
        org.jboss.logging.Logger mock =
            createStrictMock(org.jboss.logging.Logger.class);

        mock.debug("a", e);
        replay(mock);

        InternalLogger logger = new JBossLogger(mock);
        logger.debug("a", e);
        verify(mock);
    }

    @Test
    public void testInfo() {
        org.jboss.logging.Logger mock =
            createStrictMock(org.jboss.logging.Logger.class);

        mock.info("a");
        replay(mock);

        InternalLogger logger = new JBossLogger(mock);
        logger.info("a");
        verify(mock);
    }

    @Test
    public void testInfoWithException() {
        org.jboss.logging.Logger mock =
            createStrictMock(org.jboss.logging.Logger.class);

        mock.info("a", e);
        replay(mock);

        InternalLogger logger = new JBossLogger(mock);
        logger.info("a", e);
        verify(mock);
    }

    @Test
    public void testWarn() {
        org.jboss.logging.Logger mock =
            createStrictMock(org.jboss.logging.Logger.class);

        mock.warn("a");
        replay(mock);

        InternalLogger logger = new JBossLogger(mock);
        logger.warn("a");
        verify(mock);
    }

    @Test
    public void testWarnWithException() {
        org.jboss.logging.Logger mock =
            createStrictMock(org.jboss.logging.Logger.class);

        mock.warn("a", e);
        replay(mock);

        InternalLogger logger = new JBossLogger(mock);
        logger.warn("a", e);
        verify(mock);
    }

    @Test
    public void testError() {
        org.jboss.logging.Logger mock =
            createStrictMock(org.jboss.logging.Logger.class);

        mock.error("a");
        replay(mock);

        InternalLogger logger = new JBossLogger(mock);
        logger.error("a");
        verify(mock);
    }

    @Test
    public void testErrorWithException() {
        org.jboss.logging.Logger mock =
            createStrictMock(org.jboss.logging.Logger.class);

        mock.error("a", e);
        replay(mock);

        InternalLogger logger = new JBossLogger(mock);
        logger.error("a", e);
        verify(mock);
    }
}
