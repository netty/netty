/*
 * Copyright 2012 The Netty Project
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
package io.netty.logging;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <a href="http://java.sun.com/javase/6/docs/technotes/guides/logging/index.html">java.util.logging</a>
 * logger.
 */
class JdkLogger extends AbstractInternalLogger {

    private final Logger logger;
    private final String loggerName;

    JdkLogger(Logger logger, String loggerName) {
        this.logger = logger;
        this.loggerName = loggerName;
    }

    @Override
    public void debug(String msg) {
        logger.logp(Level.FINE, loggerName, null, msg);
    }

    @Override
    public void debug(String msg, Throwable cause) {
        logger.logp(Level.FINE, loggerName, null, msg, cause);
    }

    @Override
    public void error(String msg) {
        logger.logp(Level.SEVERE, loggerName, null, msg);
    }

    @Override
    public void error(String msg, Throwable cause) {
        logger.logp(Level.SEVERE, loggerName, null, msg, cause);
    }

    @Override
    public void info(String msg) {
        logger.logp(Level.INFO, loggerName, null, msg);
    }

    @Override
    public void info(String msg, Throwable cause) {
        logger.logp(Level.INFO, loggerName, null, msg, cause);
    }

    @Override
    public boolean isDebugEnabled() {
        return logger.isLoggable(Level.FINE);
    }

    @Override
    public boolean isErrorEnabled() {
        return logger.isLoggable(Level.SEVERE);
    }

    @Override
    public boolean isInfoEnabled() {
        return logger.isLoggable(Level.INFO);
    }

    @Override
    public boolean isWarnEnabled() {
        return logger.isLoggable(Level.WARNING);
    }

    @Override
    public void warn(String msg) {
        logger.logp(Level.WARNING, loggerName, null, msg);
    }

    @Override
    public void warn(String msg, Throwable cause) {
        logger.logp(Level.WARNING, loggerName, null, msg, cause);
    }

    @Override
    public String toString() {
        return loggerName;
    }
}
