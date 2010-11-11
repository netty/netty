/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.logging;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <a href="http://java.sun.com/javase/6/docs/technotes/guides/logging/index.html">java.util.logging</a>
 * logger.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 *
 */
class JdkLogger extends AbstractInternalLogger {

    private final Logger logger;
    private final String loggerName;

    JdkLogger(Logger logger, String loggerName) {
        this.logger = logger;
        this.loggerName = loggerName;
    }

    public void debug(String msg) {
        logger.logp(Level.FINE, loggerName, null, msg);
    }

    public void debug(String msg, Throwable cause) {
        logger.logp(Level.FINE, loggerName, null, msg, cause);
    }

    public void error(String msg) {
        logger.logp(Level.SEVERE, loggerName, null, msg);
    }

    public void error(String msg, Throwable cause) {
        logger.logp(Level.SEVERE, loggerName, null, msg, cause);
    }

    public void info(String msg) {
        logger.logp(Level.INFO, loggerName, null, msg);
    }

    public void info(String msg, Throwable cause) {
        logger.logp(Level.INFO, loggerName, null, msg, cause);
    }

    public boolean isDebugEnabled() {
        return logger.isLoggable(Level.FINE);
    }

    public boolean isErrorEnabled() {
        return logger.isLoggable(Level.SEVERE);
    }

    public boolean isInfoEnabled() {
        return logger.isLoggable(Level.INFO);
    }

    public boolean isWarnEnabled() {
        return logger.isLoggable(Level.WARNING);
    }

    public void warn(String msg) {
        logger.logp(Level.WARNING, loggerName, null, msg);
    }

    public void warn(String msg, Throwable cause) {
        logger.logp(Level.WARNING, loggerName, null, msg, cause);
    }

    @Override
    public String toString() {
        return loggerName;
    }
}
