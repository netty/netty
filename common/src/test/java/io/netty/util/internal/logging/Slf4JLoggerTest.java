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

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

public class Slf4JLoggerTest {
    private static final Exception e = new Exception();

    @Test
    public void testName() {
        InternalLogger logger = new Slf4JLogger(new RecordingSlf4JLogger("foo"));
        assertThat(logger.name()).isEqualTo("foo");
    }

    @Test
    public void testIsTraceEnabled() {
        assertThat(loggerWithOnly(Level.TRACE).isTraceEnabled()).isTrue();
        assertThat(loggerWithAllBut(Level.TRACE).isTraceEnabled()).isFalse();
    }

    @Test
    public void testIsDebugEnabled() {
        assertThat(loggerWithOnly(Level.DEBUG).isDebugEnabled()).isTrue();
        assertThat(loggerWithAllBut(Level.DEBUG).isDebugEnabled()).isFalse();
    }

    @Test
    public void testIsInfoEnabled() {
        assertThat(loggerWithOnly(Level.INFO).isInfoEnabled()).isTrue();
        assertThat(loggerWithAllBut(Level.INFO).isInfoEnabled()).isFalse();
    }

    @Test
    public void testIsWarnEnabled() {
        assertThat(loggerWithOnly(Level.WARN).isWarnEnabled()).isTrue();
        assertThat(loggerWithAllBut(Level.WARN).isWarnEnabled()).isFalse();
    }

    @Test
    public void testIsErrorEnabled() {
        assertThat(loggerWithOnly(Level.ERROR).isErrorEnabled()).isTrue();
        assertThat(loggerWithAllBut(Level.ERROR).isErrorEnabled()).isFalse();
    }

    private static InternalLogger loggerWithOnly(Level level) {
        return new Slf4JLogger(new RecordingSlf4JLogger("foo", EnumSet.of(level)));
    }

    private static InternalLogger loggerWithAllBut(Level level) {
        return new Slf4JLogger(new RecordingSlf4JLogger("foo", EnumSet.complementOf(EnumSet.of(level))));
    }

    @Test
    public void testTrace() {
        RecordingSlf4JLogger recorder = new RecordingSlf4JLogger("foo", EnumSet.of(Level.TRACE));

        InternalLogger logger = new Slf4JLogger(recorder);
        logger.trace("a");

        verifyLogged(recorder, Level.TRACE, null);
    }

    @Test
    public void testTraceWithException() {
        RecordingSlf4JLogger recorder = new RecordingSlf4JLogger("foo", EnumSet.of(Level.TRACE));

        InternalLogger logger = new Slf4JLogger(recorder);
        logger.trace("a", e);

        verifyLogged(recorder, Level.TRACE, e);
    }

    @Test
    public void testDebug() {
        RecordingSlf4JLogger recorder = new RecordingSlf4JLogger("foo", EnumSet.of(Level.DEBUG));

        InternalLogger logger = new Slf4JLogger(recorder);
        logger.debug("a");

        verifyLogged(recorder, Level.DEBUG, null);
    }

    @Test
    public void testDebugWithException() {
        RecordingSlf4JLogger recorder = new RecordingSlf4JLogger("foo", EnumSet.of(Level.DEBUG));

        InternalLogger logger = new Slf4JLogger(recorder);
        logger.debug("a", e);

        verifyLogged(recorder, Level.DEBUG, e);
    }

    @Test
    public void testInfo() {
        RecordingSlf4JLogger recorder = new RecordingSlf4JLogger("foo", EnumSet.of(Level.INFO));

        InternalLogger logger = new Slf4JLogger(recorder);
        logger.info("a");

        verifyLogged(recorder, Level.INFO, null);
    }

    @Test
    public void testInfoWithException() {
        RecordingSlf4JLogger recorder = new RecordingSlf4JLogger("foo", EnumSet.of(Level.INFO));

        InternalLogger logger = new Slf4JLogger(recorder);
        logger.info("a", e);

        verifyLogged(recorder, Level.INFO, e);
    }

    @Test
    public void testWarn() {
        RecordingSlf4JLogger recorder = new RecordingSlf4JLogger("foo", EnumSet.of(Level.WARN));

        InternalLogger logger = new Slf4JLogger(recorder);
        logger.warn("a");

        verifyLogged(recorder, Level.WARN, null);
    }

    @Test
    public void testWarnWithException() {
        RecordingSlf4JLogger recorder = new RecordingSlf4JLogger("foo", EnumSet.of(Level.WARN));

        InternalLogger logger = new Slf4JLogger(recorder);
        logger.warn("a", e);

        verifyLogged(recorder, Level.WARN, e);
    }

    @Test
    public void testError() {
        RecordingSlf4JLogger recorder = new RecordingSlf4JLogger("foo", EnumSet.of(Level.ERROR));

        InternalLogger logger = new Slf4JLogger(recorder);
        logger.error("a");

        verifyLogged(recorder, Level.ERROR, null);
    }

    @Test
    public void testErrorWithException() {
        RecordingSlf4JLogger recorder = new RecordingSlf4JLogger("foo", EnumSet.of(Level.ERROR));

        InternalLogger logger = new Slf4JLogger(recorder);
        logger.error("a", e);

        verifyLogged(recorder, Level.ERROR, e);
    }

    private static void verifyLogged(RecordingSlf4JLogger recorder, Level level, Throwable cause) {
        assertThat(recorder.events()).hasSize(1);
        LoggedEvent event = recorder.events().get(0);
        assertThat(event.level).isEqualTo(level);
        assertThat(event.marker).isNull();
        assertThat(event.message).isEqualTo("a");
        assertThat(event.arguments).isNull();
        assertThat(event.throwable).isSameAs(cause);
    }
}
