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

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.OutputStreamAppender;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Log4j2Test {

    @Test
    public void testLoggerFactoryResolution() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration config = context.getConfiguration();
        PatternLayout layout = PatternLayout.newBuilder().withPattern("%m%n").build();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        OutputStreamAppender appender = OutputStreamAppender
                .newBuilder()
                .setLayout(layout)
                .setName("TEST_APPENDER")
                .setTarget(buffer)
                .build();
        appender.start();
        AppenderRef ref = AppenderRef.createAppenderRef("TEST_APPENDER", null, null);
        AppenderRef[] refs = new AppenderRef[] {ref};
        LoggerConfig loggerConfig = LoggerConfig.createLogger("false", Level.INFO,
                "CONSOLE_LOGGER", "foo", refs, null, config, null);
        loggerConfig.addAppender(appender, null, null);
        config.addAppender(appender);
        config.addLogger("foo", loggerConfig);
        context.updateLoggers(config);
        InternalLogger logg = InternalLoggerFactory.getInstance("foo");
        String expected = "the-msg";
        logg.info(expected);
        assertEquals(expected, buffer.toString().substring(0, expected.length()));
    }
}
