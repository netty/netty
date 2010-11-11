/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.util.internal;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

/**
 * Warn when user creates too many instances to avoid {@link OutOfMemoryError}.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2234 $, $Date: 2010-04-06 18:23:25 +0900 (Tue, 06 Apr 2010) $
 */
public class SharedResourceMisuseDetector {

    private static final int MAX_ACTIVE_INSTANCES = 256;
    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(SharedResourceMisuseDetector.class);

    private final Class<?> type;
    private final AtomicLong activeInstances = new AtomicLong();
    private final AtomicBoolean logged = new AtomicBoolean();

    public SharedResourceMisuseDetector(Class<?> type) {
        if (type == null) {
            throw new NullPointerException("type");
        }
        this.type = type;
    }

    public void increase() {
        if (activeInstances.incrementAndGet() > MAX_ACTIVE_INSTANCES) {
            if (logged.compareAndSet(false, true)) {
                logger.warn(
                        "You are creating too many " + type.getSimpleName() +
                        " instances.  " + type.getSimpleName() +
                        " is a shared resource that must be reused across the" +
                        " application, so that only a few instances are created.");
            }
        }
    }

    public void decrease() {
        activeInstances.decrementAndGet();
    }
}
