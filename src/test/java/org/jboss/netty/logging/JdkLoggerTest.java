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

import java.util.logging.Level;

import org.junit.Test;


/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
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

        mock.logp(Level.FINE, "foo", null, "a");
        replay(mock);

        InternalLogger logger = new JdkLogger(mock, "foo");
        logger.debug("a");
        verify(mock);
    }

    @Test
    public void testDebugWithException() {
        java.util.logging.Logger mock =
            createStrictMock(java.util.logging.Logger.class);

        mock.logp(Level.FINE, "foo", null, "a", e);
        replay(mock);

        InternalLogger logger = new JdkLogger(mock, "foo");
        logger.debug("a", e);
        verify(mock);
    }

    @Test
    public void testInfo() {
        java.util.logging.Logger mock =
            createStrictMock(java.util.logging.Logger.class);

        mock.logp(Level.INFO, "foo", null, "a");
        replay(mock);

        InternalLogger logger = new JdkLogger(mock, "foo");
        logger.info("a");
        verify(mock);
    }

    @Test
    public void testInfoWithException() {
        java.util.logging.Logger mock =
            createStrictMock(java.util.logging.Logger.class);

        mock.logp(Level.INFO, "foo", null, "a", e);
        replay(mock);

        InternalLogger logger = new JdkLogger(mock, "foo");
        logger.info("a", e);
        verify(mock);
    }

    @Test
    public void testWarn() {
        java.util.logging.Logger mock =
            createStrictMock(java.util.logging.Logger.class);

        mock.logp(Level.WARNING, "foo", null, "a");
        replay(mock);

        InternalLogger logger = new JdkLogger(mock, "foo");
        logger.warn("a");
        verify(mock);
    }

    @Test
    public void testWarnWithException() {
        java.util.logging.Logger mock =
            createStrictMock(java.util.logging.Logger.class);

        mock.logp(Level.WARNING, "foo", null, "a", e);
        replay(mock);

        InternalLogger logger = new JdkLogger(mock, "foo");
        logger.warn("a", e);
        verify(mock);
    }

    @Test
    public void testError() {
        java.util.logging.Logger mock =
            createStrictMock(java.util.logging.Logger.class);

        mock.logp(Level.SEVERE, "foo", null, "a");
        replay(mock);

        InternalLogger logger = new JdkLogger(mock, "foo");
        logger.error("a");
        verify(mock);
    }

    @Test
    public void testErrorWithException() {
        java.util.logging.Logger mock =
            createStrictMock(java.util.logging.Logger.class);

        mock.logp(Level.SEVERE, "foo", null, "a", e);
        replay(mock);

        InternalLogger logger = new JdkLogger(mock, "foo");
        logger.error("a", e);
        verify(mock);
    }
}
