/*
 * Copyright 2017 The Netty Project
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
package io.netty.resolver.dns;

import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.util.internal.logging.InternalLogLevel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

final class TraceDnsQueryLifeCycleObserverFactory implements DnsQueryLifecycleObserverFactory {
    private static final InternalLogger DEFAULT_LOGGER =
            InternalLoggerFactory.getInstance(TraceDnsQueryLifeCycleObserverFactory.class);
    private static final InternalLogLevel DEFAULT_LEVEL = InternalLogLevel.DEBUG;
    private final InternalLogger logger;
    private final InternalLogLevel level;

    TraceDnsQueryLifeCycleObserverFactory() {
        this(DEFAULT_LOGGER, DEFAULT_LEVEL);
    }

    TraceDnsQueryLifeCycleObserverFactory(InternalLogger logger, InternalLogLevel level) {
        this.logger = checkNotNull(logger, "logger");
        this.level = checkNotNull(level, "level");
    }

    @Override
    public DnsQueryLifecycleObserver newDnsQueryLifecycleObserver(DnsQuestion question) {
        return new TraceDnsQueryLifecycleObserver(question, logger, level);
    }
}
