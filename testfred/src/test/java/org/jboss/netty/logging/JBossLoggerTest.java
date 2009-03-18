/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.logging;

import static org.easymock.EasyMock.*;
import static org.easymock.classextension.EasyMock.*;
import static org.junit.Assert.*;

import org.junit.Test;


/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
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
