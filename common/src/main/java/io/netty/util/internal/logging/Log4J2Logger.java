/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util.internal.logging;


import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.spi.ExtendedLogger;
import org.apache.logging.log4j.spi.ExtendedLoggerWrapper;

class Log4J2Logger extends ExtendedLoggerWrapper implements InternalLogger {

    private static final long serialVersionUID = 5485418394879791397L;

    /** {@linkplain AbstractInternalLogger#EXCEPTION_MESSAGE} */
    private static final String EXCEPTION_MESSAGE = "Unexpected exception:";

    Log4J2Logger(Logger logger) {
        super((ExtendedLogger) logger, logger.getName(), logger.getMessageFactory());
    }

    @Override
    public String name() {
        return getName();
    }

    @Override
    public void trace(Throwable t) {
        log(Level.TRACE, EXCEPTION_MESSAGE, t);
    }

    @Override
    public void debug(Throwable t) {
        log(Level.DEBUG, EXCEPTION_MESSAGE, t);
    }

    @Override
    public void info(Throwable t) {
        log(Level.INFO, EXCEPTION_MESSAGE, t);
    }

    @Override
    public void warn(Throwable t) {
        log(Level.WARN, EXCEPTION_MESSAGE, t);
    }

    @Override
    public void error(Throwable t) {
        log(Level.ERROR, EXCEPTION_MESSAGE, t);
    }

    @Override
    public boolean isEnabled(InternalLogLevel level) {
        return isEnabled(toLevel(level));
    }

    @Override
    public void log(InternalLogLevel level, String msg) {
        log(toLevel(level), msg);
    }

    @Override
    public void log(InternalLogLevel level, String format, Object arg) {
        log(toLevel(level), format, arg);
    }

    @Override
    public void log(InternalLogLevel level, String format, Object argA, Object argB) {
        log(toLevel(level), format, argA, argB);
    }

    @Override
    public void log(InternalLogLevel level, String format, Object... arguments) {
        log(toLevel(level), format, arguments);
    }

    @Override
    public void log(InternalLogLevel level, String msg, Throwable t) {
        log(toLevel(level), msg, t);
    }

    @Override
    public void log(InternalLogLevel level, Throwable t) {
        log(toLevel(level), EXCEPTION_MESSAGE, t);
    }

    protected Level toLevel(InternalLogLevel level) {
        switch (level) {
            case INFO:
                return Level.INFO;
            case DEBUG:
                return Level.DEBUG;
            case WARN:
                return Level.WARN;
            case ERROR:
                return Level.ERROR;
            case TRACE:
                return Level.TRACE;
            default:
                throw new Error();
        }
    }
}
