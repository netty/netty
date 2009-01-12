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

import org.osgi.framework.ServiceReference;
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
class OsgiLogger implements InternalLogger {

    private final LogService logService;
    private final ServiceReference serviceRef;
    private final String name;
    private final String prefix;

    OsgiLogger(LogService logService, ServiceReference serviceRef, String name) {
        if (logService == null) {
            throw new NullPointerException("logService");
        }
        if (serviceRef == null) {
            throw new NullPointerException("serviceRef");
        }
        if (name == null) {
            throw new NullPointerException("name");
        }
        this.logService = logService;
        this.serviceRef = serviceRef;
        this.name = name;
        prefix = "[" + name + "] ";
    }

    public void debug(String msg) {
        logService.log(serviceRef, LogService.LOG_DEBUG, prefix + msg);
    }

    public void debug(String msg, Throwable cause) {
        logService.log(serviceRef, LogService.LOG_DEBUG, prefix + msg, cause);
    }

    public void error(String msg) {
        logService.log(serviceRef, LogService.LOG_ERROR, prefix + msg);
    }

    public void error(String msg, Throwable cause) {
        logService.log(serviceRef, LogService.LOG_ERROR, prefix + msg, cause);
    }

    public void info(String msg) {
        logService.log(serviceRef, LogService.LOG_INFO, prefix + msg);
    }

    public void info(String msg, Throwable cause) {
        logService.log(serviceRef, LogService.LOG_INFO, prefix + msg, cause);
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
        logService.log(serviceRef, LogService.LOG_WARNING, prefix + msg);
    }

    public void warn(String msg, Throwable cause) {
        logService.log(serviceRef, LogService.LOG_WARNING, prefix + msg, cause);
    }

    @Override
    public String toString() {
        return name;
    }
}