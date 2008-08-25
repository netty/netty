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

import java.util.logging.Level;

import org.junit.Test;


/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class JdkLoggerTest {
    private static final Exception e = new Exception();

    @Test
    public void testIsDebugEnabled() {

        java.util.logging.Logger mock =
            createStrictMock(java.util.logging.Logger.class);

        expect(mock.isLoggable(Level.FINE)).andReturn(true);
        replay(mock);

        InternalLogger logger = new JdkLogger(mock, "foo");
        assertTrue(logger.isDebugEnabled());
        verify(mock);
    }

    @Test
    public void testIsInfoEnabled() {
        java.util.logging.Logger mock =
            createStrictMock(java.util.logging.Logger.class);

        expect(mock.isLoggable(Level.INFO)).andReturn(true);
        replay(mock);

        InternalLogger logger = new JdkLogger(mock, "foo");
        assertTrue(logger.isInfoEnabled());
        verify(mock);
    }

    @Test
    public void testIsWarnEnabled() {
        java.util.logging.Logger mock =
            createStrictMock(java.util.logging.Logger.class);

        expect(mock.isLoggable(Level.WARNING)).andReturn(true);
        replay(mock);

        InternalLogger logger = new JdkLogger(mock, "foo");
        assertTrue(logger.isWarnEnabled());
        verify(mock);
    }

    @Test
    public void testIsErrorEnabled() {
        java.util.logging.Logger mock =
            createStrictMock(java.util.logging.Logger.class);

        expect(mock.isLoggable(Level.SEVERE)).andReturn(true);
        replay(mock);

        InternalLogger logger = new JdkLogger(mock, "foo");
        assertTrue(logger.isErrorEnabled());
        verify(mock);
    }

    @Test
    public void testDebug() {
        java.util.logging.Logger mock =
            createStrictMock(java.util.logging.Logger.class);

        mock.logp(Level.FINE, "foo", "-", "a");
        replay(mock);

        InternalLogger logger = new JdkLogger(mock, "foo");
        logger.debug("a");
        verify(mock);
    }

    @Test
    public void testDebugWithException() {
        java.util.logging.Logger mock =
            createStrictMock(java.util.logging.Logger.class);

        mock.logp(Level.FINE, "foo", "-", "a", e);
        replay(mock);

        InternalLogger logger = new JdkLogger(mock, "foo");
        logger.debug("a", e);
        verify(mock);
    }

    @Test
    public void testInfo() {
        java.util.logging.Logger mock =
            createStrictMock(java.util.logging.Logger.class);

        mock.logp(Level.INFO, "foo", "-", "a");
        replay(mock);

        InternalLogger logger = new JdkLogger(mock, "foo");
        logger.info("a");
        verify(mock);
    }

    @Test
    public void testInfoWithException() {
        java.util.logging.Logger mock =
            createStrictMock(java.util.logging.Logger.class);

        mock.logp(Level.INFO, "foo", "-", "a", e);
        replay(mock);

        InternalLogger logger = new JdkLogger(mock, "foo");
        logger.info("a", e);
        verify(mock);
    }

    @Test
    public void testWarn() {
        java.util.logging.Logger mock =
            createStrictMock(java.util.logging.Logger.class);

        mock.logp(Level.WARNING, "foo", "-", "a");
        replay(mock);

        InternalLogger logger = new JdkLogger(mock, "foo");
        logger.warn("a");
        verify(mock);
    }

    @Test
    public void testWarnWithException() {
        java.util.logging.Logger mock =
            createStrictMock(java.util.logging.Logger.class);

        mock.logp(Level.WARNING, "foo", "-", "a", e);
        replay(mock);

        InternalLogger logger = new JdkLogger(mock, "foo");
        logger.warn("a", e);
        verify(mock);
    }

    @Test
    public void testError() {
        java.util.logging.Logger mock =
            createStrictMock(java.util.logging.Logger.class);

        mock.logp(Level.SEVERE, "foo", "-", "a");
        replay(mock);

        InternalLogger logger = new JdkLogger(mock, "foo");
        logger.error("a");
        verify(mock);
    }

    @Test
    public void testErrorWithException() {
        java.util.logging.Logger mock =
            createStrictMock(java.util.logging.Logger.class);

        mock.logp(Level.SEVERE, "foo", "-", "a", e);
        replay(mock);

        InternalLogger logger = new JdkLogger(mock, "foo");
        logger.error("a", e);
        verify(mock);
    }
}
