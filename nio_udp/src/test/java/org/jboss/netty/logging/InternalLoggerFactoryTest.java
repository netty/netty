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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;


/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class InternalLoggerFactoryTest {
    private static final Exception e = new Exception();
    private InternalLoggerFactory oldLoggerFactory;
    private InternalLogger mock;

    @Before
    public void init() {
        oldLoggerFactory = InternalLoggerFactory.getDefaultFactory();
        InternalLoggerFactory mockFactory = createMock(InternalLoggerFactory.class);
        mock = createStrictMock(InternalLogger.class);
        expect(mockFactory.newInstance("mock")).andReturn(mock).anyTimes();
        replay(mockFactory);
        InternalLoggerFactory.setDefaultFactory(mockFactory);
    }

    @After
    public void destroy() {
        reset(mock);
        InternalLoggerFactory.setDefaultFactory(oldLoggerFactory);
    }


    @Test(expected = NullPointerException.class)
    public void shouldNotAllowNullDefaultFactory() {
        InternalLoggerFactory.setDefaultFactory(null);
    }

    @Test
    public void shouldReturnWrappedLogger() {
        assertNotSame(mock, InternalLoggerFactory.getInstance("mock"));
    }

    @Test
    public void testIsDebugEnabled() {
        expect(mock.isDebugEnabled()).andReturn(true);
        replay(mock);

        InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        assertTrue(logger.isDebugEnabled());
        verify(mock);
    }

    @Test
    public void testIsInfoEnabled() {
        expect(mock.isInfoEnabled()).andReturn(true);
        replay(mock);

        InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        assertTrue(logger.isInfoEnabled());
        verify(mock);
    }

    @Test
    public void testIsWarnEnabled() {
        expect(mock.isWarnEnabled()).andReturn(true);
        replay(mock);

        InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        assertTrue(logger.isWarnEnabled());
        verify(mock);
    }

    @Test
    public void testIsErrorEnabled() {
        expect(mock.isErrorEnabled()).andReturn(true);
        replay(mock);

        InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        assertTrue(logger.isErrorEnabled());
        verify(mock);
    }

    @Test
    public void testDebug() {
        mock.debug("a");
        replay(mock);

        InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.debug("a");
        verify(mock);
    }

    @Test
    public void testDebugWithException() {
        mock.debug("a", e);
        replay(mock);

        InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.debug("a", e);
        verify(mock);
    }

    @Test
    public void testInfo() {
        mock.info("a");
        replay(mock);

        InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.info("a");
        verify(mock);
    }

    @Test
    public void testInfoWithException() {
        mock.info("a", e);
        replay(mock);

        InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.info("a", e);
        verify(mock);
    }

    @Test
    public void testWarn() {
        mock.warn("a");
        replay(mock);

        InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.warn("a");
        verify(mock);
    }

    @Test
    public void testWarnWithException() {
        mock.warn("a", e);
        replay(mock);

        InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.warn("a", e);
        verify(mock);
    }

    @Test
    public void testError() {
        mock.error("a");
        replay(mock);

        InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.error("a");
        verify(mock);
    }

    @Test
    public void testErrorWithException() {
        mock.error("a", e);
        replay(mock);

        InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.error("a", e);
        verify(mock);
    }
}
