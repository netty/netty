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

import org.apache.log4j.Logger;

/**
 * <a href="http://logging.apache.org/log4j/1.2/index.html">Apache Log4J</a>
 * logger.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 *
 */
class Log4JLogger extends AbstractInternalLogger {

    private final Logger logger;

    Log4JLogger(Logger logger) {
        this.logger = logger;
    }

    public void debug(String msg) {
        logger.debug(msg);
    }

    public void debug(String msg, Throwable cause) {
        logger.debug(msg, cause);
    }

    public void error(String msg) {
        logger.error(msg);
    }

    public void error(String msg, Throwable cause) {
        logger.error(msg, cause);
    }

    public void info(String msg) {
        logger.info(msg);
    }

    public void info(String msg, Throwable cause) {
        logger.info(msg, cause);
    }

    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    public boolean isErrorEnabled() {
        return true;
    }

    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    public boolean isWarnEnabled() {
        return true;
    }

    public void warn(String msg) {
        logger.warn(msg);
    }

    public void warn(String msg, Throwable cause) {
        logger.warn(msg, cause);
    }

    @Override
    public String toString() {
        return String.valueOf(logger.getName());
    }
}
