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

package io.netty.channel;

import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadFactory;

/**
 * {@link EventLoopGroup} utilities.
 */
public final class EventLoopGroups {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(EventLoopGroups.class);

    protected static final String CHANNEL_PACKAGE = SystemPropertyUtil.get(
            "io.netty.packagePrefix", EventLoopGroups.class.getPackage().getName());

    private static final List<String> STANDARD_IMPLEMENTATIONS = Collections.unmodifiableList(Arrays.asList(
            "nio", "oio"));

    private static final List<String> NATIVE_IMPLEMENTATIONS = Collections.unmodifiableList(Arrays.asList(
            "epoll"
    ));

    private static final List<Constructor<? extends EventLoopGroup>> EVENT_LOOP_CONSTRUCTORS =
            createEventLoopGroupConstructors();

    private EventLoopGroups() {
    }

    /**
     * Returns list of standard netty event loop group implementations.
     *
     * @return list of standard implementations.
     */
    protected static List<String> standardImplementations() {
        return STANDARD_IMPLEMENTATIONS;
    }

    /**
     * Returns list of native implementations.
     *
     * @return list of native implementations.
     */
    protected static List<String> nativeImplementations() {
        return NATIVE_IMPLEMENTATIONS;
    }

    /**
     * Returns fastest non-blocking {@link EventLoopGroup} instance for running platform. Tries to initialize native
     * implementation for running platform (epoll on Linux for example) first and fallbacks to
     * {@link io.netty.channel.nio.NioEventLoopGroup} if native implementation for running platform is not available.
     *
     * @return event loop group
     */
    public static EventLoopGroup fastestNonBlocking() {
        return fastestNonBlocking(0);
    }

    /**
     * Returns fastest non-blocking {@link EventLoopGroup} instance for running platform. Tries to initialize native
     * implementation for running platform (epoll on Linux for example) first and fallbacks to
     * {@link io.netty.channel.nio.NioEventLoopGroup} if native implementation for running platform is not available.
     *
     * @param nThreads number of threads
     * @return event loop group
     */
    public static EventLoopGroup fastestNonBlocking(int nThreads) {
        return fastestNonBlocking(nThreads, null);
    }

    /**
     * Returns fastest non-blocking {@link EventLoopGroup} instance for running platform. Tries to initialize native
     * implementation for running platform (epoll on Linux for example) first and fallbacks to
     * {@link io.netty.channel.nio.NioEventLoopGroup} if native implementation for running platform is not available.
     *
     * @param nThreads      number of threads
     * @param threadFactory thread factory
     * @return event loop group
     */
    public static EventLoopGroup fastestNonBlocking(int nThreads, ThreadFactory threadFactory) {
        for (Constructor<? extends EventLoopGroup> constructor : EVENT_LOOP_CONSTRUCTORS) {
            try {
                return constructor.newInstance(nThreads, threadFactory);
            } catch (Exception e) {
                logger.debug("Cannot invoke event loop group constructor: {}", constructor, e);
            }
        }
        throw new IllegalStateException(
                "Cannot successfully invoke any of discovered createEventLoopGroupConstructors: " +
                        EVENT_LOOP_CONSTRUCTORS);
    }

    /**
     * Tries to load {@link EventLoopGroup} class.
     *
     * @param fqcn event loop group fully qualified class name.
     * @return event loop group class on success, otherwise <code>null</code>.
     * @see #loadClass(String, Class)
     */
    protected static Class<? extends EventLoopGroup> loadEventLoopGroupClass(String fqcn) {
        return loadClass(fqcn, EventLoopGroup.class);
    }

    /**
     * Tries to load class with specified fully qualified class name.
     *
     * @param fqcn      fully qualified class name.
     * @param classType class into which loaded class should be cast.
     * @param <T>       class type.
     * @return class on success, otherwise <code>null</code>.
     */
    protected static <T> Class<T> loadClass(String fqcn, Class<T> classType) {
        try {
            Class<?> clazz = Class.forName(fqcn);
            return (Class<T>) clazz.asSubclass(classType);
        } catch (ClassNotFoundException e) {
            logger.trace("Unable to load class {}: class is not present on classpath.", fqcn);
            return null;
        }
    }

    protected static String ucFirst(String s) {
        char[] chars = s.toCharArray();
        chars[0] = Character.toUpperCase(chars[0]);
        return new String(chars);
    }

    private static List<Constructor<? extends EventLoopGroup>> createEventLoopGroupConstructors() {
        List<Constructor<? extends EventLoopGroup>> res = new ArrayList<Constructor<? extends EventLoopGroup>>(4);
        createEventLoopGroupConstructors(nativeImplementations(), res);
        createEventLoopGroupConstructors(standardImplementations(), res);
        return Collections.unmodifiableList(res);
    }

    private static void createEventLoopGroupConstructors(List<String> implementations,
                                                         List<Constructor<? extends EventLoopGroup>> dst) {
        for (String impl : implementations) {
            try {
                Class<? extends EventLoopGroup> clazz = loadEventLoopGroupClass(eventLoopGroupClass(impl));
                Constructor<? extends EventLoopGroup> constructor =
                        clazz.getConstructor(int.class, ThreadFactory.class);
                dst.add(constructor);
            } catch (Exception e) {
                // no one cares
            }
        }
    }

    protected static String eventLoopGroupClass(String implementation) {
        String pkg = EventLoopGroups.CHANNEL_PACKAGE + "." + implementation;
        return pkg + "." + ucFirst(implementation) + "EventLoopGroup";
    }

    protected static String fqcn(String s) {
        return EventLoopGroups.CHANNEL_PACKAGE + "." + s;
    }
}
