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
import io.netty.util.internal.logging.InternalLogLevel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
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
                final Collection<ChannelInitializerExtension> extensions;
                if ("serviceload".equalsIgnoreCase(extensionProp)) {
                    extensions = serviceLoadExtensions(InternalLogLevel.DEBUG);
                } else if ("log".equalsIgnoreCase(extensionProp)) {
                    serviceLoadExtensions(InternalLogLevel.INFO);
                    extensions = Collections.emptyList();
                } else {
                    extensions = Collections.emptyList();
                }
                implementation = impl = new LoadedExtensions(extensions);
            }
        }
        return impl;
    }

    private static Collection<ChannelInitializerExtension> serviceLoadExtensions(InternalLogLevel logLevel) {
        ServiceLoader<ChannelInitializerExtension> loader = ServiceLoader.load(ChannelInitializerExtension.class);
        ArrayList<ChannelInitializerExtension> extensions = new ArrayList<ChannelInitializerExtension>();
        for (ChannelInitializerExtension extension : loader) {
            logger.log(logLevel, "Loaded extension: {}", extension.getClass());
            extensions.add(extension);
        }
        if (!extensions.isEmpty()) {
            Collections.sort(extensions, new Comparator<ChannelInitializerExtension>() {
                @Override
                public int compare(ChannelInitializerExtension a, ChannelInitializerExtension b) {
                    return Double.compare(a.priority(), b.priority());
                }
            });
            return Collections.unmodifiableList(extensions);
        }
        return Collections.emptyList();
    }

    /**
     * @return {@code true} if there are no extensions, otherwise {@code false}.
     */
    abstract boolean isEmpty();

    /**
     * Get the list of available extensions. The list is unmodifiable.
     */
    abstract Collection<ChannelInitializerExtension> extensions();

    private static final class LoadedExtensions extends ChannelInitializerExtensions {
        private final Collection<ChannelInitializerExtension> extensions;

        private LoadedExtensions(Collection<ChannelInitializerExtension> extensions) {
            this.extensions = extensions;
        }

        @Override
        boolean isEmpty() {
            return extensions.isEmpty();
        }

        @SuppressWarnings("AssignmentOrReturnOfFieldWithMutableType")
        @Override
        Collection<ChannelInitializerExtension> extensions() {
            return extensions;
        }
    }
}
