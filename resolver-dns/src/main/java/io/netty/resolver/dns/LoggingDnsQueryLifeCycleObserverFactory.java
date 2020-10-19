/*
 * Copyright 2020 The Netty Project
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
package io.netty.resolver.dns;

import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.logging.LogLevel;
import io.netty.util.internal.logging.InternalLogLevel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * A {@link DnsQueryLifecycleObserverFactory} that enables detailed logging in the {@link DnsNameResolver}.
 * <p>
 * When {@linkplain DnsNameResolverBuilder#dnsQueryLifecycleObserverFactory(DnsQueryLifecycleObserverFactory)
 * configured on the resolver}, detailed trace information will be generated so that it is easier to understand the
 * cause of resolution failure.
 */
public final class LoggingDnsQueryLifeCycleObserverFactory implements DnsQueryLifecycleObserverFactory {
    private static final InternalLogger DEFAULT_LOGGER =
            InternalLoggerFactory.getInstance(LoggingDnsQueryLifeCycleObserverFactory.class);
    private final InternalLogger logger;
    private final InternalLogLevel level;

    /**
     * Create {@link DnsQueryLifecycleObserver} instances that log events at the default {@link LogLevel#DEBUG} level.
     */
    public LoggingDnsQueryLifeCycleObserverFactory() {
        this(LogLevel.DEBUG);
    }

    /**
     * Create {@link DnsQueryLifecycleObserver} instances that log events at the given log level.
     * @param level The log level to use for logging resolver events.
     */
    public LoggingDnsQueryLifeCycleObserverFactory(LogLevel level) {
        this.level = checkAndConvertLevel(level);
        logger = DEFAULT_LOGGER;
    }

    /**
     * Create {@link DnsQueryLifecycleObserver} instances that log events to a logger with the given class context,
     * at the given log level.
     * @param classContext The class context for the logger to use.
     * @param level The log level to use for logging resolver events.
     */
    public LoggingDnsQueryLifeCycleObserverFactory(Class<?> classContext, LogLevel level) {
        this.level = checkAndConvertLevel(level);
        logger = InternalLoggerFactory.getInstance(checkNotNull(classContext, "classContext"));
    }

    /**
     * Create {@link DnsQueryLifecycleObserver} instances that log events to a logger with the given name context,
     * at the given log level.
     * @param name The name for the logger to use.
     * @param level The log level to use for logging resolver events.
     */
    public LoggingDnsQueryLifeCycleObserverFactory(String name, LogLevel level) {
        this.level = checkAndConvertLevel(level);
        logger = InternalLoggerFactory.getInstance(checkNotNull(name, "name"));
    }

    private static InternalLogLevel checkAndConvertLevel(LogLevel level) {
        return checkNotNull(level, "level").toInternalLevel();
    }

    @Override
    public DnsQueryLifecycleObserver newDnsQueryLifecycleObserver(DnsQuestion question) {
        return new LoggingDnsQueryLifecycleObserver(question, logger, level);
    }
}
