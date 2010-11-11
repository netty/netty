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

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Logger factory which creates an <a href="http://www.osgi.org/">OSGi</a>
 * {@link LogService} logger.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 *
 */
public class OsgiLoggerFactory extends InternalLoggerFactory {

    private final ServiceTracker logServiceTracker;
    private final InternalLoggerFactory fallback;
    volatile LogService logService;

    public OsgiLoggerFactory(BundleContext ctx) {
        this(ctx, null);
    }

    public OsgiLoggerFactory(BundleContext ctx, InternalLoggerFactory fallback) {
        if (ctx == null) {
            throw new NullPointerException("ctx");
        }
        if (fallback == null) {
            fallback = InternalLoggerFactory.getDefaultFactory();
            if (fallback instanceof OsgiLoggerFactory) {
                fallback = new JdkLoggerFactory();
            }
        }

        this.fallback = fallback;
        logServiceTracker = new ServiceTracker(
                ctx, "org.osgi.service.log.LogService", null) {
                    @Override
                    public Object addingService(ServiceReference reference) {
                        LogService service = (LogService) super.addingService(reference);
                        logService = service;
                        return service;
                    }

                    @Override
                    public void removedService(ServiceReference reference,
                            Object service) {
                        logService = null;
                    }
        };
        logServiceTracker.open();
    }

    public InternalLoggerFactory getFallback() {
        return fallback;
    }

    public LogService getLogService() {
        return logService;
    }

    public void destroy() {
        logService = null;
        logServiceTracker.close();
    }

    @Override
    public InternalLogger newInstance(String name) {
        return new OsgiLogger(this, name, fallback.newInstance(name));
    }
}
