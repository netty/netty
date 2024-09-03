/*
 * Copyright 2024 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.testsuite_jpms.it;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Slf4jTest {

    @Test
    public void testLoggerFactoryResolution() {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = (Logger) LoggerFactory.getLogger("foo");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext(lc);
        logger.addAppender(appender);
        appender.start();
        logger.setLevel(Level.DEBUG);
        logger.setAdditive(false);
        InternalLoggerFactory factory = InternalLoggerFactory.getDefaultFactory();
        assertEquals(Slf4JLoggerFactory.class, factory.getClass());
        InternalLogger logg = InternalLoggerFactory.getInstance("foo");
        logg.info("the-msg");
        assertEquals(1, appender.list.size());
        assertEquals("the-msg", appender.list.get(0).getMessage());
        logger.detachAndStopAllAppenders();
    }
}
