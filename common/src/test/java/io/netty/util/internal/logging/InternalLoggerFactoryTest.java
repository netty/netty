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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class InternalLoggerFactoryTest {
    private static final Exception e = new Exception();
    private InternalLoggerFactory oldLoggerFactory;
    private RecordingSlf4JLogger recorder;
    private InternalLogger testLogger;

    @BeforeEach
    public void init() {
        oldLoggerFactory = InternalLoggerFactory.getDefaultFactory();

        recorder = new RecordingSlf4JLogger("mock");
        testLogger = new Slf4JLogger(recorder);
        InternalLoggerFactory.setDefaultFactory(new InternalLoggerFactory() {
            @Override
            protected InternalLogger newInstance(String name) {
                return "mock".equals(name) ? testLogger : null;
            }
        });
    }

    @AfterEach
    public void destroy() {
        InternalLoggerFactory.setDefaultFactory(oldLoggerFactory);
    }

    @Test
    public void shouldNotAllowNullDefaultFactory() {
        assertThrows(NullPointerException.class, () -> InternalLoggerFactory.setDefaultFactory(null));
    }

    @Test
    public void shouldGetInstance() {
        InternalLoggerFactory.setDefaultFactory(oldLoggerFactory);

        String helloWorld = "Hello, world!";

        InternalLogger one = InternalLoggerFactory.getInstance("helloWorld");
        InternalLogger two = InternalLoggerFactory.getInstance(helloWorld.getClass());

        assertThat(one).isNotNull();
        assertThat(two).isNotNull();
        assertThat(one).isNotSameAs(two);
    }

    @Test
    public void shouldGetInstanceFromDefaultFactory() {
        assertThat(InternalLoggerFactory.getInstance("mock")).isSameAs(testLogger);
    }

    @Test
    public void testIsTraceEnabled() {
        InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        assertThat(logger.isTraceEnabled()).isTrue();
    }

    @Test
    public void testIsDebugEnabled() {
        InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        assertThat(logger.isDebugEnabled()).isTrue();
    }

    @Test
    public void testIsInfoEnabled() {
        InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        assertThat(logger.isInfoEnabled()).isTrue();
    }

    @Test
    public void testIsWarnEnabled() {
        InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        assertThat(logger.isWarnEnabled()).isTrue();
    }

    @Test
    public void testIsErrorEnabled() {
        InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        assertThat(logger.isErrorEnabled()).isTrue();
    }

    @Test
    public void testTrace() {
        final InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.trace("a");
        verifyLogged(Level.TRACE, null);
    }

    @Test
    public void testTraceWithException() {
        final InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.trace("a", e);
        verifyLogged(Level.TRACE, e);
    }

    @Test
    public void testDebug() {
        final InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.debug("a");
        verifyLogged(Level.DEBUG, null);
    }

    @Test
    public void testDebugWithException() {
        final InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.debug("a", e);
        verifyLogged(Level.DEBUG, e);
    }

    @Test
    public void testInfo() {
        final InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.info("a");
        verifyLogged(Level.INFO, null);
    }

    @Test
    public void testInfoWithException() {
        final InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.info("a", e);
        verifyLogged(Level.INFO, e);
    }

    @Test
    public void testWarn() {
        final InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.warn("a");
        verifyLogged(Level.WARN, null);
    }

    @Test
    public void testWarnWithException() {
        final InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.warn("a", e);
        verifyLogged(Level.WARN, e);
    }

    @Test
    public void testError() {
        final InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.error("a");
        verifyLogged(Level.ERROR, null);
    }

    @Test
    public void testErrorWithException() {
        final InternalLogger logger = InternalLoggerFactory.getInstance("mock");
        logger.error("a", e);
        verifyLogged(Level.ERROR, e);
    }

    private void verifyLogged(Level level, Throwable cause) {
        assertThat(recorder.events()).hasSize(1);
        LoggedEvent event = recorder.events().get(0);
        assertThat(event.level).isEqualTo(level);
        assertThat(event.message).isEqualTo("a");
        assertThat(event.throwable).isSameAs(cause);
    }
}
