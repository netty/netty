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

package io.netty.util;

import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.reflect.Constructor;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * This static factory should be used to load {@link ResourceLeakDetector}s as needed
 */
public abstract class ResourceLeakDetectorFactory {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ResourceLeakDetectorFactory.class);

    private static volatile ResourceLeakDetectorFactory factoryInstance = new DefaultResourceLeakDetectorFactory();

    /**
     * Get the singleton instance of this factory class.
     *
     * @return - the current {@link ResourceLeakDetectorFactory}
     */
    public static ResourceLeakDetectorFactory instance() {
        return factoryInstance;
    }

    /**
     * Set the factory's singleton instance. This has to be called before the static initializer of the
     * {@link ResourceLeakDetector} is called by all the callers of this factory. That is, before initializing a
     * Netty Bootstrap.
     *
     * @param factory - the instance that will become the current {@link ResourceLeakDetectorFactory}'s singleton
     */
    public static void setResourceLeakDetectorFactory(ResourceLeakDetectorFactory factory) {
        factoryInstance = ObjectUtil.checkNotNull(factory, "factory");
    }

    /**
     * Returns a new instance of a {@link ResourceLeakDetector} with the given resource class.
     *
     * @param resource - the resource class used to initialize the {@link ResourceLeakDetector}
     * @param <T> - the type of the resource class
     * @return - a new instance of {@link ResourceLeakDetector}
     */
    public abstract <T> ResourceLeakDetector<T> newResourceLeakDetector(final Class<T> resource);

    /**
     * Default implementation that loads custom leak detector via system property
     */
    private static final class DefaultResourceLeakDetectorFactory extends ResourceLeakDetectorFactory {

        private final String customLeakDetector;
        private final Constructor customClassConstructor;

        public DefaultResourceLeakDetectorFactory() {
            this.customLeakDetector = AccessController.doPrivileged(new PrivilegedAction<String>() {
                @Override
                public String run() {
                    return SystemPropertyUtil.get("io.netty.customResourceLeakDetector");
                }
            });

            this.customClassConstructor = customClassConstructor();
        }

        private Constructor customClassConstructor() {
            try {
                if (customLeakDetector != null) {
                    final Class<?> detectorClass = Class.forName(customLeakDetector, true,
                            PlatformDependent.getSystemClassLoader());

                    if (ResourceLeakDetector.class.isAssignableFrom(detectorClass)) {
                        return detectorClass.getConstructor(Class.class);
                    } else {
                        logger.error("Class {} does not inherit from ResourceLeakDetector.", customLeakDetector);
                    }
                }
            } catch (Throwable t) {
                logger.error("Could not load custom resource leak detector class provided: " + customLeakDetector, t);
            }
            return null;
        }

        @Override
        public <T> ResourceLeakDetector<T> newResourceLeakDetector(final Class<T> resource) {
            try {
                if (customClassConstructor != null) {
                    ResourceLeakDetector<T> leakDetector =
                            (ResourceLeakDetector<T>) customClassConstructor.newInstance(resource);
                    logger.debug("Loaded custom ResourceLeakDetector: {}", customLeakDetector);
                    return leakDetector;
                }
            } catch (Throwable t) {
                logger.error("Could not load custom resource leak detector provided: {} with the given resource: {}",
                        customLeakDetector, resource, t);
            }

            ResourceLeakDetector<T> resourceLeakDetector = new ResourceLeakDetector<T>(resource);
            logger.debug("Loaded default ResourceLeakDetector: {}", resourceLeakDetector);
            return resourceLeakDetector;
        }
    }
}
