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

import org.apache.commons.logging.Log;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static io.netty.util.internal.logging.InternalLogLevel.DEBUG;
import static io.netty.util.internal.logging.InternalLogLevel.ERROR;
import static io.netty.util.internal.logging.InternalLogLevel.INFO;
import static io.netty.util.internal.logging.InternalLogLevel.TRACE;
import static io.netty.util.internal.logging.InternalLogLevel.WARN;
import static org.assertj.core.api.Assertions.assertThat;

public class CommonsLoggerTest {
    private static final Exception e = new Exception();

    @Test
    public void testIsTraceEnabled() {
        assertThat(loggerWithOnly(TRACE).isTraceEnabled()).isTrue();
        assertThat(loggerWithAllBut(TRACE).isTraceEnabled()).isFalse();
    }

    @Test
    public void testIsDebugEnabled() {
        assertThat(loggerWithOnly(DEBUG).isDebugEnabled()).isTrue();
        assertThat(loggerWithAllBut(DEBUG).isDebugEnabled()).isFalse();
    }

    @Test
    public void testIsInfoEnabled() {
        assertThat(loggerWithOnly(INFO).isInfoEnabled()).isTrue();
        assertThat(loggerWithAllBut(INFO).isInfoEnabled()).isFalse();
    }

    @Test
    public void testIsWarnEnabled() {
        assertThat(loggerWithOnly(WARN).isWarnEnabled()).isTrue();
        assertThat(loggerWithAllBut(WARN).isWarnEnabled()).isFalse();
    }

    @Test
    public void testIsErrorEnabled() {
        assertThat(loggerWithOnly(ERROR).isErrorEnabled()).isTrue();
        assertThat(loggerWithAllBut(ERROR).isErrorEnabled()).isFalse();
    }

    private static InternalLogger loggerWithOnly(InternalLogLevel level) {
        return new CommonsLogger(new RecordingLog(EnumSet.of(level)), "foo");
    }

    private static InternalLogger loggerWithAllBut(InternalLogLevel level) {
        return new CommonsLogger(new RecordingLog(EnumSet.complementOf(EnumSet.of(level))), "foo");
    }

    @Test
    public void testTrace() {
        RecordingLog log = new RecordingLog(EnumSet.of(TRACE));

        InternalLogger logger = new CommonsLogger(log, "foo");
        logger.trace("a");

        verifyLogged(log, "trace", null);
    }

    @Test
    public void testTraceWithException() {
        RecordingLog log = new RecordingLog(EnumSet.of(TRACE));

        InternalLogger logger = new CommonsLogger(log, "foo");
        logger.trace("a", e);

        verifyLogged(log, "trace", e);
    }

    @Test
    public void testDebug() {
        RecordingLog log = new RecordingLog(EnumSet.of(DEBUG));

        InternalLogger logger = new CommonsLogger(log, "foo");
        logger.debug("a");

        verifyLogged(log, "debug", null);
    }

    @Test
    public void testDebugWithException() {
        RecordingLog log = new RecordingLog(EnumSet.of(DEBUG));

        InternalLogger logger = new CommonsLogger(log, "foo");
        logger.debug("a", e);

        verifyLogged(log, "debug", e);
    }

    @Test
    public void testInfo() {
        RecordingLog log = new RecordingLog(EnumSet.of(INFO));

        InternalLogger logger = new CommonsLogger(log, "foo");
        logger.info("a");

        verifyLogged(log, "info", null);
    }

    @Test
    public void testInfoWithException() {
        RecordingLog log = new RecordingLog(EnumSet.of(INFO));

        InternalLogger logger = new CommonsLogger(log, "foo");
        logger.info("a", e);

        verifyLogged(log, "info", e);
    }

    @Test
    public void testWarn() {
        RecordingLog log = new RecordingLog(EnumSet.of(WARN));

        InternalLogger logger = new CommonsLogger(log, "foo");
        logger.warn("a");

        verifyLogged(log, "warn", null);
    }

    @Test
    public void testWarnWithException() {
        RecordingLog log = new RecordingLog(EnumSet.of(WARN));

        InternalLogger logger = new CommonsLogger(log, "foo");
        logger.warn("a", e);

        verifyLogged(log, "warn", e);
    }

    @Test
    public void testError() {
        RecordingLog log = new RecordingLog(EnumSet.of(ERROR));

        InternalLogger logger = new CommonsLogger(log, "foo");
        logger.error("a");

        verifyLogged(log, "error", null);
    }

    @Test
    public void testErrorWithException() {
        RecordingLog log = new RecordingLog(EnumSet.of(ERROR));

        InternalLogger logger = new CommonsLogger(log, "foo");
        logger.error("a", e);

        verifyLogged(log, "error", e);
    }

    private static void verifyLogged(RecordingLog log, String level, Throwable cause) {
        assertThat(log.events).hasSize(1);
        LoggedEvent event = log.events.get(0);
        assertThat(event.level).isEqualTo(level);
        assertThat(event.message).isEqualTo("a");
        assertThat(event.cause).isSameAs(cause);
    }

    private static final class LoggedEvent {
        final String level;
        final Object message;
        final Throwable cause;

        LoggedEvent(String level, Object message, Throwable cause) {
            this.level = level;
            this.message = message;
            this.cause = cause;
        }
    }

    /**
     * A commons-logging {@link Log} which records every logging call it receives.
     */
    private static final class RecordingLog implements Log {
        final List<LoggedEvent> events = new ArrayList<>();
        private final Set<InternalLogLevel> enabledLevels;

        RecordingLog(Set<InternalLogLevel> enabledLevels) {
            this.enabledLevels = enabledLevels;
        }

        private void record(String level, Object message, Throwable cause) {
            events.add(new LoggedEvent(level, message, cause));
        }

        @Override
        public boolean isTraceEnabled() {
            return enabledLevels.contains(TRACE);
        }

        @Override
        public boolean isDebugEnabled() {
            return enabledLevels.contains(DEBUG);
        }

        @Override
        public boolean isInfoEnabled() {
            return enabledLevels.contains(INFO);
        }

        @Override
        public boolean isWarnEnabled() {
            return enabledLevels.contains(WARN);
        }

        @Override
        public boolean isErrorEnabled() {
            return enabledLevels.contains(ERROR);
        }

        @Override
        public boolean isFatalEnabled() {
            // InternalLogLevel has no FATAL level, so treat it like ERROR.
            return enabledLevels.contains(ERROR);
        }

        @Override
        public void trace(Object message) {
            record("trace", message, null);
        }

        @Override
        public void trace(Object message, Throwable t) {
            record("trace", message, t);
        }

        @Override
        public void debug(Object message) {
            record("debug", message, null);
        }

        @Override
        public void debug(Object message, Throwable t) {
            record("debug", message, t);
        }

        @Override
        public void info(Object message) {
            record("info", message, null);
        }

        @Override
        public void info(Object message, Throwable t) {
            record("info", message, t);
        }

        @Override
        public void warn(Object message) {
            record("warn", message, null);
        }

        @Override
        public void warn(Object message, Throwable t) {
            record("warn", message, t);
        }

        @Override
        public void error(Object message) {
            record("error", message, null);
        }

        @Override
        public void error(Object message, Throwable t) {
            record("error", message, t);
        }

        @Override
        public void fatal(Object message) {
            record("fatal", message, null);
        }

        @Override
        public void fatal(Object message, Throwable t) {
            record("fatal", message, t);
        }
    }
}
