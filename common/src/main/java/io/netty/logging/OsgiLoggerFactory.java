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

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;

/**
 * A logger factory which creates <a href="http://www.osgi.org/">OSGi</a>
 * {@link LogService} loggers.
 */
public class OsgiLoggerFactory extends InternalLoggerFactory<OsgiLogger> {

    /**
     * The log service tracker
     */
    private final ServiceTracker logServiceTracker;

    /**
     * The fallback logger factory
     */
    private final InternalLoggerFactory fallback;

    /**
     * The {@link LogService} to forward log messages to
     */
    volatile LogService logService;

    /**
     * Creates a new {@link OsgiLoggerFactory} without a manual fallback
     *
     * @param context the {@link BundleContext} being used
     */
    public OsgiLoggerFactory(BundleContext context) {
        this(context, null);
    }

    /**
     * Creates a new {@link OsgiLoggerFactory}
     *
     * @param context the {@link BundleContext} being used
     * @param fallback The fallback logger factory
     */
    public OsgiLoggerFactory(BundleContext context, InternalLoggerFactory fallback) {
        //Check to make sure the context isn't null
        if (context == null) {
            throw new NullPointerException("Context can not be null");
        }

        //See if the fallback is null
        if (fallback == null) {
            //Get the default fallback
            fallback = InternalLoggerFactory.getDefaultFactory();
            //But wait! What if the fallback is another Osgi logger factory?
            if (fallback instanceof OsgiLoggerFactory) {
                //Force a JDK logger factory if it is. Problem solved.
                fallback = new JdkLoggerFactory();
            }
        }

        //Set the fallback
        this.fallback = fallback;
        //Set and start the log service tracker
        logServiceTracker = new ServiceTracker(
                context, "org.osgi.service.log.LogService", null) {
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
        //Open the service tracker
        logServiceTracker.open();
    }

    /**
     * Gets the fallback {@link InternalLoggerFactory}
     *
     * @return the fallback factory
     */
    public InternalLoggerFactory getFallback() {
        return fallback;
    }

    /**
     * Gets the {@link LogService} used to log messages
     *
     * @return the {@link LogService}
     */
    public LogService getLogService() {
        return logService;
    }

    /**
     * Destroys all managed resources
     */
    public void destroy() {
        logService = null;
        logServiceTracker.close();
    }

    /**
     * Creates a new {@link OsgiLogger} instance
     *
     * @param name the name of the new {@link OsgiLogger} instance
     * @return a new {@link OsgiLogger} instance
     */
    @Override
    public OsgiLogger newInstance(String name) {
        return new OsgiLogger(this, name, fallback.newInstance(name));
    }
}
