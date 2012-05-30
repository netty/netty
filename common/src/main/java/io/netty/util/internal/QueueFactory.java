/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util.internal;

import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.util.concurrent.BlockingQueue;

/**
 * This factory should be used to create the "optimal" {@link BlockingQueue}
 * instance for the running JVM.
 */
public final class QueueFactory {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(QueueFactory.class);

    private static final boolean USE_LTQ;

    static {
        boolean useLTQ = false;
        try {
            if (DetectionUtil.hasUnsafe()) {
                new LinkedTransferQueue<Object>();
                useLTQ = true;
            }
            logger.debug(
                    "No access to the Unsafe - using " +
                    LegacyLinkedTransferQueue.class.getSimpleName() + " instead.");
        } catch (Throwable t) {
            logger.debug(
                    "Failed to initialize a " + LinkedTransferQueue.class.getSimpleName() + " - " +
                    "using " + LegacyLinkedTransferQueue.class.getSimpleName() + " instead.", t);
        }

        USE_LTQ = useLTQ;
    }

    /**
     * Create a new unbound {@link BlockingQueue}
     *
     * @return queue     the {@link BlockingQueue} implementation
     */
    public static <T> BlockingQueue<T> createQueue() {
        if (USE_LTQ) {
            return new LinkedTransferQueue<T>();
        } else {
            return new LegacyLinkedTransferQueue<T>();
        }
    }

    private QueueFactory() {
        // only use static methods!
    }
}
