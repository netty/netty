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

import org.osgi.service.log.LogService;

/**
 * <a href="http://www.osgi.org/">OSGi</a> {@link LogService} logger.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 *
 */
class OsgiLogger extends AbstractInternalLogger {

    private final OsgiLoggerFactory parent;
    private final InternalLogger fallback;
    private final String name;
    private final String prefix;

    OsgiLogger(OsgiLoggerFactory parent, String name, InternalLogger fallback) {
        this.parent = parent;
        this.name = name;
        this.fallback = fallback;
        prefix = "[" + name + "] ";
    }

    public void debug(String msg) {
        LogService logService = parent.getLogService();
        if (logService != null) {
            logService.log(LogService.LOG_DEBUG, prefix + msg);
        } else {
            fallback.debug(msg);
        }
    }

    public void debug(String msg, Throwable cause) {
        LogService logService = parent.getLogService();
        if (logService != null) {
            logService.log(LogService.LOG_DEBUG, prefix + msg, cause);
        } else {
            fallback.debug(msg, cause);
        }
    }

    public void error(String msg) {
        LogService logService = parent.getLogService();
        if (logService != null) {
            logService.log(LogService.LOG_ERROR, prefix + msg);
        } else {
            fallback.error(msg);
        }
    }

    public void error(String msg, Throwable cause) {
        LogService logService = parent.getLogService();
        if (logService != null) {
            logService.log(LogService.LOG_ERROR, prefix + msg, cause);
        } else {
            fallback.error(msg, cause);
        }
    }

    public void info(String msg) {
        LogService logService = parent.getLogService();
        if (logService != null) {
            logService.log(LogService.LOG_INFO, prefix + msg);
        } else {
            fallback.info(msg);
        }
    }

    public void info(String msg, Throwable cause) {
        LogService logService = parent.getLogService();
        if (logService != null) {
            logService.log(LogService.LOG_INFO, prefix + msg, cause);
        } else {
            fallback.info(msg, cause);
        }
    }

    public boolean isDebugEnabled() {
        return true;
    }

    public boolean isErrorEnabled() {
        return true;
    }

    public boolean isInfoEnabled() {
        return true;
    }

    public boolean isWarnEnabled() {
        return true;
    }

    public void warn(String msg) {
        LogService logService = parent.getLogService();
        if (logService != null) {
            logService.log(LogService.LOG_WARNING, prefix + msg);
        } else {
            fallback.warn(msg);
        }
    }

    public void warn(String msg, Throwable cause) {
        LogService logService = parent.getLogService();
        if (logService != null) {
            logService.log(LogService.LOG_WARNING, prefix + msg, cause);
        } else {
            fallback.warn(msg, cause);
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
