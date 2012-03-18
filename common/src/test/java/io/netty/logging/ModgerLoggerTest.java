/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.logging;

import biz.massivedynamics.modger.Logger;
import biz.massivedynamics.modger.message.MessageType;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import org.junit.Test;

/**
 * A test class for a ModgerLogger
 */
public class ModgerLoggerTest {
    
    private static final Exception e = new Exception();
    
    @Test
    public void testIsDebugEnabled() {
        assertTrue(MessageType.DEBUG.isFiltered());
    }
    
    @Test
    public void testIsInfoEnabled() {
        assertFalse(MessageType.INFORMATION.isFiltered());
    }
    
    @Test
    public void testIsWarningEnabled() {
        assertFalse(MessageType.WARNING.isFiltered());
    }
    
    @Test
    public void testIsErrorEnabled() {
        assertFalse(MessageType.ERROR.isFiltered());
    }
    
    @Test
    public void testDebug() {
        Logger mock = createStrictMock(Logger.class);

        mock.submitDebug("a");
        replay(mock);

        InternalLogger logger = new ModgerLogger(mock);
        logger.debug("a");
        verify(mock);
    }
    
    @Test
    public void testInfo() {
        Logger mock = createStrictMock(Logger.class);

        mock.submitInformation("a");
        replay(mock);

        InternalLogger logger = new ModgerLogger(mock);
        logger.info("a");
        verify(mock);
    }
    
    @Test
    public void testWarn() {
        Logger mock = createStrictMock(Logger.class);

        mock.submitWarning("a");
        replay(mock);

        InternalLogger logger = new ModgerLogger(mock);
        logger.warn("a");
        verify(mock);
    }
    
    @Test
    public void testError() {
        Logger mock = createStrictMock(Logger.class);

        mock.submitError("a");
        replay(mock);

        InternalLogger logger = new ModgerLogger(mock);
        logger.error("a");
        verify(mock);
    }
    
    @Test
    public void testDebugWithException() {
        Logger mock = createStrictMock(Logger.class);

        mock.submitDebug("a", e);
        replay(mock);

        InternalLogger logger = new ModgerLogger(mock);
        logger.debug("a", e);
        verify(mock);
    }
    
    @Test
    public void testInfoWithException() {
        Logger mock = createStrictMock(Logger.class);

        mock.submitInformation("a", e);
        replay(mock);

        InternalLogger logger = new ModgerLogger(mock);
        logger.info("a", e);
        verify(mock);
    }
    
    @Test
    public void testWarnWithException() {
        Logger mock = createStrictMock(Logger.class);

        mock.submitWarning("a", e);
        replay(mock);

        InternalLogger logger = new ModgerLogger(mock);
        logger.warn("a", e);
        verify(mock);
    }
    
    @Test
    public void testErrorWithException() {
        Logger mock = createStrictMock(Logger.class);

        mock.submitError("a", e);
        replay(mock);

        InternalLogger logger = new ModgerLogger(mock);
        logger.error("a", e);
        verify(mock);
    }
    
    
    
}
