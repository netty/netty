/*
 * Copyright 2012 The Netty Project
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
package io.netty.util.internal.logging;

import io.netty.util.internal.logging.RecordingSlf4JLogger.LoggedEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Slf4JLoggerFactoryTest {

    @Test
    public void testCreation() {
        InternalLogger logger = Slf4JLoggerFactory.INSTANCE.newInstance("foo");
        assertTrue(logger instanceof Slf4JLogger || logger instanceof LocationAwareSlf4JLogger);
        assertEquals("foo", logger.name());
    }

    @Test
    public void testCreationLogger() {
        InternalLogger internalLogger = Slf4JLoggerFactory.wrapLogger(new RecordingSlf4JLogger("testlogger"));
        assertInstanceOf(Slf4JLogger.class, internalLogger);
        assertEquals("testlogger", internalLogger.name());
    }

    @Test
    public void testCreationLocationAwareLogger() {
        InternalLogger internalLogger = Slf4JLoggerFactory.wrapLogger(
                new RecordingSlf4JLogger.LocationAware("testlogger"));
        assertInstanceOf(LocationAwareSlf4JLogger.class, internalLogger);
        assertEquals("testlogger", internalLogger.name());
    }

    @Test
    public void testFormatMessage() {
        RecordingSlf4JLogger.LocationAware logger = new RecordingSlf4JLogger.LocationAware("testlogger");

        InternalLogger internalLogger = Slf4JLoggerFactory.wrapLogger(logger);
        internalLogger.debug("{}", "debug");
        internalLogger.debug("{} {}", "debug1", "debug2");
        internalLogger.debug("{} {} {}", "debug1", "debug2", "debug3");

        internalLogger.error("{}", "error");
        internalLogger.error("{} {}", "error1", "error2");
        internalLogger.error("{} {} {}", "error1", "error2", "error3");

        internalLogger.info("{}", "info");
        internalLogger.info("{} {}", "info1", "info2");
        internalLogger.info("{} {} {}", "info1", "info2", "info3");

        internalLogger.trace("{}", "trace");
        internalLogger.trace("{} {}", "trace1", "trace2");
        internalLogger.trace("{} {} {}", "trace1", "trace2", "trace3");

        internalLogger.warn("{}", "warn");
        internalLogger.warn("{} {}", "warn1", "warn2");
        internalLogger.warn("{} {} {}", "warn1", "warn2", "warn3");

        List<Level> levels = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        for (LoggedEvent event : logger.events()) {
            assertThat(event.marker).isNull();
            assertThat(event.fqcn).isEqualTo(LocationAwareSlf4JLogger.FQCN);
            assertThat(event.arguments).isNull();
            assertThat(event.throwable).isNull();
            levels.add(event.level);
            messages.add(event.message);
        }

        assertThat(levels).containsExactly(
                Level.DEBUG, Level.DEBUG, Level.DEBUG,
                Level.ERROR, Level.ERROR, Level.ERROR,
                Level.INFO, Level.INFO, Level.INFO,
                Level.TRACE, Level.TRACE, Level.TRACE,
                Level.WARN, Level.WARN, Level.WARN);
        assertThat(messages).containsExactly(
                "debug", "debug1 debug2", "debug1 debug2 debug3",
                "error", "error1 error2", "error1 error2 error3",
                "info", "info1 info2", "info1 info2 info3",
                "trace", "trace1 trace2", "trace1 trace2 trace3",
                "warn", "warn1 warn2", "warn1 warn2 warn3");
    }
}
