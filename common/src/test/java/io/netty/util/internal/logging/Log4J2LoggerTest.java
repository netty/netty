/*
 * Copyright 2016 The Netty Project
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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumMap;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.spi.ExtendedLoggerWrapper;

/**
 * {@linkplain Log4J2Logger} extends {@linkplain ExtendedLoggerWrapper} implements {@linkplain InternalLogger}.<br>
 * {@linkplain ExtendedLoggerWrapper} is Log4j2 wrapper class to support wrapped loggers,
 * so There is no need to test it's method.<br>
 * We only need to test the netty's {@linkplain InternalLogger} interface method.<br>
 * It's meaning that we only need to test the Override method in the {@linkplain Log4J2Logger}.
 */
public class Log4J2LoggerTest extends AbstractInternalLoggerTest<Logger> {

    {
        mockLog = LogManager.getLogger(loggerName);
        logger = new Log4J2Logger(mockLog) {
            private static final long serialVersionUID = 1L;

            @Override
            public void logMessage(String fqcn, Level level, Marker marker, Message message, Throwable t) {
                result.put("level", level.name());
                result.put("t", t);
                super.logMessage(fqcn, level, marker, message, t);
            }
        };
    }

    private static final EnumMap<InternalLogLevel, Level> DISABLING_LEVEL = new EnumMap<>(InternalLogLevel.class);

    static {
        DISABLING_LEVEL.put(InternalLogLevel.TRACE, Level.DEBUG);
        DISABLING_LEVEL.put(InternalLogLevel.DEBUG, Level.INFO);
        DISABLING_LEVEL.put(InternalLogLevel.INFO, Level.WARN);
        DISABLING_LEVEL.put(InternalLogLevel.WARN, Level.ERROR);
        DISABLING_LEVEL.put(InternalLogLevel.ERROR, Level.FATAL);
    }

    @Override
    protected void setLevelEnable(InternalLogLevel level, boolean enable) {
        Level targetLevel = Level.valueOf(level.name());
        if (!enable) {
            targetLevel = DISABLING_LEVEL.get(level);
        }

        ((org.apache.logging.log4j.core.Logger) mockLog).setLevel(targetLevel);
    }

    @Override
    protected void assertResult(InternalLogLevel level, String format, Throwable t, Object... args) {
        super.assertResult(level, format, t, args);
        assertEquals(t, result.get("t"));
        assertEquals(level.name(), result.get("level"));
    }
}
