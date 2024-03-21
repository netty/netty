/*
 * Copyright 2023 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.bootstrap;

import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

/**
 * The configurable facade that decides what {@link ChannelInitializerExtension}s to load and where to find them.
 */
abstract class ChannelInitializerExtensions {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ChannelInitializerExtensions.class);
    private static volatile ChannelInitializerExtensions implementation;

    private ChannelInitializerExtensions() {
    }

    /**
     * Get the configuration extensions, which is a no-op implementation by default,
     * or a service-loading implementation if the {@code io.netty.bootstrap.extensions} system property is
     * {@code serviceload}.
     */
    static ChannelInitializerExtensions getExtensions() {
        ChannelInitializerExtensions impl = implementation;
        if (impl == null) {
            synchronized (ChannelInitializerExtensions.class) {
                impl = implementation;
                if (impl != null) {
                    return impl;
                }
                String extensionProp = SystemPropertyUtil.get(ChannelInitializerExtension.EXTENSIONS_SYSTEM_PROPERTY);
                logger.debug("-Dio.netty.bootstrap.extensions: {}", extensionProp);
                if ("serviceload".equalsIgnoreCase(extensionProp)) {
                    impl = new ServiceLoadingExtensions(true);
                } else if ("log".equalsIgnoreCase(extensionProp)) {
                    impl = new ServiceLoadingExtensions(false);
                } else {
                    impl = new EmptyExtensions();
                }
                implementation = impl;
            }
        }
        return impl;
    }

    /**
     * Get the list of available extensions. The list is unmodifiable.
     */
    abstract Collection<ChannelInitializerExtension> extensions(ClassLoader cl);

    private static final class EmptyExtensions extends ChannelInitializerExtensions {
        @Override
        Collection<ChannelInitializerExtension> extensions(ClassLoader cl) {
            return Collections.emptyList();
        }
    }

    private static final class ServiceLoadingExtensions extends ChannelInitializerExtensions {
        private final boolean loadAndCache;

        private WeakReference<ClassLoader> classLoader;
        private Collection<ChannelInitializerExtension> extensions;

        ServiceLoadingExtensions(boolean loadAndCache) {
            this.loadAndCache = loadAndCache;
        }

        @SuppressWarnings("AssignmentOrReturnOfFieldWithMutableType")
        @Override
        synchronized Collection<ChannelInitializerExtension> extensions(ClassLoader cl) {
            ClassLoader configured = classLoader == null ? null : classLoader.get();
            if (configured == null || configured != cl) {
                Collection<ChannelInitializerExtension> loaded = serviceLoadExtensions(loadAndCache, cl);
                classLoader = new WeakReference<ClassLoader>(cl);
                extensions = loadAndCache ? loaded : Collections.<ChannelInitializerExtension>emptyList();
            }
            return extensions;
        }

        private static Collection<ChannelInitializerExtension> serviceLoadExtensions(boolean load, ClassLoader cl) {
            List<ChannelInitializerExtension> extensions = new ArrayList<ChannelInitializerExtension>();

            ServiceLoader<ChannelInitializerExtension> loader = ServiceLoader.load(
                    ChannelInitializerExtension.class, cl);
            for (ChannelInitializerExtension extension : loader) {
                extensions.add(extension);
            }

            if (!extensions.isEmpty()) {
                Collections.sort(extensions, new Comparator<ChannelInitializerExtension>() {
                    @Override
                    public int compare(ChannelInitializerExtension a, ChannelInitializerExtension b) {
                        return Double.compare(a.priority(), b.priority());
                    }
                });
                logger.info("ServiceLoader {}(s) {}: {}", ChannelInitializerExtension.class.getSimpleName(),
                        load ? "registered" : "detected", extensions);
                return Collections.unmodifiableList(extensions);
            }
            logger.debug("ServiceLoader {}(s) {}: []", ChannelInitializerExtension.class.getSimpleName(),
                    load ? "registered" : "detected");
            return Collections.emptyList();
        }
    }
}
