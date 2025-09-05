/*
 * Copyright 2025 The Netty Project
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

package io.netty.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.Closeable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertThrows;

class LeakPresenceDetectorTest {
    private ResourceLeakDetectorFactory oldFactory;

    private void setUp(Function<Class<?>, LeakPresenceDetector<?>> factory) {
        oldFactory = ResourceLeakDetectorFactory.instance();
        ResourceLeakDetectorFactory.setResourceLeakDetectorFactory(new ResourceLeakDetectorFactory() {
            @SuppressWarnings("unchecked")
            @Override
            public <T> ResourceLeakDetector<T> newResourceLeakDetector(
                    Class<T> resource, int samplingInterval, long maxActive) {
                return (ResourceLeakDetector<T>) factory.apply(resource);
            }
        });
    }

    @AfterEach
    public void tearDown() {
        ResourceLeakDetectorFactory.setResourceLeakDetectorFactory(oldFactory);
    }

    static List<Function<Class<?>, LeakPresenceDetector<?>>> factories() {
        return Collections.singletonList(LeakPresenceDetector::new);
    }

    @ParameterizedTest
    @MethodSource("factories")
    public void noExceptionInNormalOperation(Function<Class<?>, LeakPresenceDetector<?>> factory) {
        setUp(factory);

        Resource resource = new Resource();
        resource.close();
        resource.close();

        LeakPresenceDetector.check();
    }

    @SuppressWarnings("resource")
    @ParameterizedTest
    @MethodSource("factories")
    public void exceptionOnUnclosed(Function<Class<?>, LeakPresenceDetector<?>> factory) {
        setUp(factory);

        new Resource();

        assertThrows(IllegalStateException.class, LeakPresenceDetector::check);
    }

    @Test
    public void noExceptionInStaticInitializerGlobal() {
        setUp(LeakPresenceDetector::new);

        ResourceInStaticVariable1.init();

        LeakPresenceDetector.check();
    }

    private static class Resource implements Closeable {
        private final ResourceLeakTracker<Resource> leak;

        Resource() {
            leak = ResourceLeakDetectorFactory.instance().newResourceLeakDetector(Resource.class).track(this);
        }

        @Override
        public void close() {
            leak.close(this);
        }
    }

    @SuppressWarnings("unused")
    private static class ResourceInStaticVariable1 {
        private static final Resource RESOURCE = LeakPresenceDetector.staticInitializer(Resource::new);

        static void init() {
        }
    }
}
