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
package io.netty.logging;

import org.jboss.logging.Logger;

/**
 * <a href="http://anonsvn.jboss.org/repos/common/common-logging-spi/">JBoss Logging</a>
 * logger.
 *
 * @author <a href="http://netty.io/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 */
class JBossLogger extends AbstractInternalLogger {

    private final Logger logger;

    JBossLogger(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void debug(String msg) {
        logger.debug(msg);
    }

    @Override
    public void debug(String msg, Throwable cause) {
        logger.debug(msg, cause);
    }

    @Override
    public void error(String msg) {
        logger.error(msg);
    }

    @Override
    public void error(String msg, Throwable cause) {
        logger.error(msg, cause);
    }

    @Override
    public void info(String msg) {
        logger.info(msg);
    }

    @Override
    public void info(String msg, Throwable cause) {
        logger.info(msg, cause);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    @Override
    public boolean isErrorEnabled() {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    @Override
    public void warn(String msg) {
        logger.warn(msg);
    }

    @Override
    public void warn(String msg, Throwable cause) {
        logger.warn(msg, cause);
    }

    @Override
    public String toString() {
        return String.valueOf(logger.getName());
    }
}
