/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.logging;

import org.osgi.service.log.LogService;

/**
 * <a href="http://www.osgi.org/">OSGi</a> {@link LogService} logger.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
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