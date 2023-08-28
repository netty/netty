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
                String extensionProp = SystemPropertyUtil.get("io.netty.bootstrap.extensions");
                logger.debug("-Dio.netty.bootstrap.extensions: {}", extensionProp);
                if ("serviceload".equalsIgnoreCase(extensionProp)) {
                    impl = new ServiceLoadingExtensions();
                } else {
                    impl = EmptyExtensions.EMPTY;
                }
                implementation = impl;
            }
        }
        return impl;
    }

    /**
     * @return {@code true} if there are no extensions, otherwise {@code false}.
     */
    abstract boolean isEmpty();

    /**
     * Get the list of available extensions. The list is unmodifiable.
     */
    abstract Collection<ChannelInitializerExtension> extensions(ChannelInitializerExtension.ApplicableInfo info);

    private static final class EmptyExtensions extends ChannelInitializerExtensions {
        private static final EmptyExtensions EMPTY = new EmptyExtensions();

        @Override
        boolean isEmpty() {
            return true;
        }

        @Override
        Collection<ChannelInitializerExtension> extensions(ChannelInitializerExtension.ApplicableInfo info) {
            return Collections.emptyList();
        }
    }

    private static final class ServiceLoadingExtensions extends ChannelInitializerExtensions {
        private final List<ChannelInitializerExtension> extensionList;

        private ServiceLoadingExtensions() {
            ServiceLoader<ChannelInitializerExtension> loader = ServiceLoader.load(ChannelInitializerExtension.class);
            ArrayList<ChannelInitializerExtension> extensions = new ArrayList<ChannelInitializerExtension>();
            for (ChannelInitializerExtension extension : loader) {
                logger.debug("Loaded extension: {}", extension.getClass());
                extensions.add(extension);
            }
            if (!extensions.isEmpty()) {
                Collections.sort(extensions, new Comparator<ChannelInitializerExtension>() {
                    @Override
                    public int compare(ChannelInitializerExtension a, ChannelInitializerExtension b) {
                        return Double.compare(a.priority(), b.priority());
                    }
                });
                extensionList = Collections.unmodifiableList(extensions);
            } else {
                extensionList = Collections.emptyList();
            }
        }

        @Override
        boolean isEmpty() {
            return extensionList.isEmpty();
        }

        @Override
        Collection<ChannelInitializerExtension> extensions(final ChannelInitializerExtension.ApplicableInfo info) {
            List<ChannelInitializerExtension> filteredExtensions = null;
            for (int i = 0, len = extensionList.size(); i < len; i++) {
                ChannelInitializerExtension extension = extensionList.get(i);
                boolean applicable = extension.isApplicable(info);
                if (filteredExtensions == null && !applicable) {
                    filteredExtensions = new ArrayList<ChannelInitializerExtension>();
                    for (int j = 0; j < i; j++) {
                        filteredExtensions.add(extensionList.get(j));
                    }
                } else if (filteredExtensions != null && applicable) {
                    filteredExtensions.add(extension);
                }
            }
            return filteredExtensions != null ? filteredExtensions : extensionList;
        }
    }
}
