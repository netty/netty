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
package io.netty5.resolver.dns;

import io.netty5.handler.codec.dns.DnsQuestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import static java.util.Objects.requireNonNull;

/**
 * A {@link DnsQueryLifecycleObserverFactory} that enables detailed logging in the {@link DnsNameResolver}.
 * <p>
 * When {@linkplain DnsNameResolverBuilder#dnsQueryLifecycleObserverFactory(DnsQueryLifecycleObserverFactory)
 * configured on the resolver}, detailed trace information will be generated so that it is easier to understand the
 * cause of resolution failure.
 */
public final class LoggingDnsQueryLifeCycleObserverFactory implements DnsQueryLifecycleObserverFactory {
    private static final Logger DEFAULT_LOGGER = LoggerFactory.getLogger(LoggingDnsQueryLifeCycleObserverFactory.class);
    private final Logger logger;
    private final Level level;

    /**
     * Create {@link DnsQueryLifecycleObserver} instances that log events at the default {@link Level#DEBUG} level.
     */
    public LoggingDnsQueryLifeCycleObserverFactory() {
        this(Level.DEBUG);
    }

    /**
     * Create {@link DnsQueryLifecycleObserver} instances that log events at the given log level.
     * @param level The log level to use for logging resolver events.
     */
    public LoggingDnsQueryLifeCycleObserverFactory(Level level) {
        this.level = requireNonNull(level, "level");
        logger = DEFAULT_LOGGER;
    }

    /**
     * Create {@link DnsQueryLifecycleObserver} instances that log events to a logger with the given class context,
     * at the given log level.
     * @param classContext The class context for the logger to use.
     * @param level The log level to use for logging resolver events.
     */
    public LoggingDnsQueryLifeCycleObserverFactory(Class<?> classContext, Level level) {
        this.level = requireNonNull(level, "level");
        logger = LoggerFactory.getLogger(requireNonNull(classContext, "classContext"));
    }

    /**
     * Create {@link DnsQueryLifecycleObserver} instances that log events to a logger with the given name context,
     * at the given log level.
     * @param name The name for the logger to use.
     * @param level The log level to use for logging resolver events.
     */
    public LoggingDnsQueryLifeCycleObserverFactory(String name, Level level) {
        this.level = requireNonNull(level, "level");
        logger = LoggerFactory.getLogger(requireNonNull(name, "name"));
    }

    @Override
    public DnsQueryLifecycleObserver newDnsQueryLifecycleObserver(DnsQuestion question) {
        return new LoggingDnsQueryLifecycleObserver(question, logger, level);
    }
}
