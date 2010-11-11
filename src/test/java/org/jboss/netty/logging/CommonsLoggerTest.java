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
public class CommonsLoggerTest {
    private static final Exception e = new Exception();

    @Test
    public void testIsDebugEnabled() {
        org.apache.commons.logging.Log mock =
            createStrictMock(org.apache.commons.logging.Log.class);

        expect(mock.isDebugEnabled()).andReturn(true);
        replay(mock);

        InternalLogger logger = new CommonsLogger(mock, "foo");
        assertTrue(logger.isDebugEnabled());
        verify(mock);
    }

    @Test
    public void testIsInfoEnabled() {
        org.apache.commons.logging.Log mock =
            createStrictMock(org.apache.commons.logging.Log.class);

        expect(mock.isInfoEnabled()).andReturn(true);
        replay(mock);

        InternalLogger logger = new CommonsLogger(mock, "foo");
        assertTrue(logger.isInfoEnabled());
        verify(mock);
    }

    @Test
    public void testIsWarnEnabled() {
        org.apache.commons.logging.Log mock =
            createStrictMock(org.apache.commons.logging.Log.class);

        expect(mock.isWarnEnabled()).andReturn(true);
        replay(mock);

        InternalLogger logger = new CommonsLogger(mock, "foo");
        assertTrue(logger.isWarnEnabled());
        verify(mock);
    }

    @Test
    public void testIsErrorEnabled() {
        org.apache.commons.logging.Log mock =
            createStrictMock(org.apache.commons.logging.Log.class);

        expect(mock.isErrorEnabled()).andReturn(true);
        replay(mock);

        InternalLogger logger = new CommonsLogger(mock, "foo");
        assertTrue(logger.isErrorEnabled());
        verify(mock);
    }

    @Test
    public void testDebug() {
        org.apache.commons.logging.Log mock =
            createStrictMock(org.apache.commons.logging.Log.class);

        mock.debug("a");
        replay(mock);

        InternalLogger logger = new CommonsLogger(mock, "foo");
        logger.debug("a");
        verify(mock);
    }

    @Test
    public void testDebugWithException() {
        org.apache.commons.logging.Log mock =
            createStrictMock(org.apache.commons.logging.Log.class);

        mock.debug("a", e);
        replay(mock);

        InternalLogger logger = new CommonsLogger(mock, "foo");
        logger.debug("a", e);
        verify(mock);
    }

    @Test
    public void testInfo() {
        org.apache.commons.logging.Log mock =
            createStrictMock(org.apache.commons.logging.Log.class);

        mock.info("a");
        replay(mock);

        InternalLogger logger = new CommonsLogger(mock, "foo");
        logger.info("a");
        verify(mock);
    }

    @Test
    public void testInfoWithException() {
        org.apache.commons.logging.Log mock =
            createStrictMock(org.apache.commons.logging.Log.class);

        mock.info("a", e);
        replay(mock);

        InternalLogger logger = new CommonsLogger(mock, "foo");
        logger.info("a", e);
        verify(mock);
    }

    @Test
    public void testWarn() {
        org.apache.commons.logging.Log mock =
            createStrictMock(org.apache.commons.logging.Log.class);

        mock.warn("a");
        replay(mock);

        InternalLogger logger = new CommonsLogger(mock, "foo");
        logger.warn("a");
        verify(mock);
    }

    @Test
    public void testWarnWithException() {
        org.apache.commons.logging.Log mock =
            createStrictMock(org.apache.commons.logging.Log.class);

        mock.warn("a", e);
        replay(mock);

        InternalLogger logger = new CommonsLogger(mock, "foo");
        logger.warn("a", e);
        verify(mock);
    }

    @Test
    public void testError() {
        org.apache.commons.logging.Log mock =
            createStrictMock(org.apache.commons.logging.Log.class);

        mock.error("a");
        replay(mock);

        InternalLogger logger = new CommonsLogger(mock, "foo");
        logger.error("a");
        verify(mock);
    }

    @Test
    public void testErrorWithException() {
        org.apache.commons.logging.Log mock =
            createStrictMock(org.apache.commons.logging.Log.class);

        mock.error("a", e);
        replay(mock);

        InternalLogger logger = new CommonsLogger(mock, "foo");
        logger.error("a", e);
        verify(mock);
    }
}
