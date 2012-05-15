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

import java.util.Collection;
import java.util.concurrent.BlockingQueue;

/**
 * This factory should be used to create the "optimal" {@link BlockingQueue}
 * instance for the running JVM.
 */
public final class QueueFactory {
    
    private static final boolean useUnsafe = DetectionUtil.hasUnsafe();
    
    private QueueFactory() {
        // only use static methods!
    }
    
    
    /**
     * Create a new unbound {@link BlockingQueue} 
     * 
     * @param itemClass  the {@link Class} type which will be used as {@link BlockingQueue} items
     * @return queue     the {@link BlockingQueue} implementation
     */
    public static <T> BlockingQueue<T> createQueue(Class<T> itemClass) {
        if (useUnsafe) {
            return new LinkedTransferQueue<T>();
        } else {
            return new LegacyLinkedTransferQueue<T>();
        }
    }
    
    /**
     * Create a new unbound {@link BlockingQueue} 
     * 
     * @param collection  the collection which should get copied to the newly created {@link BlockingQueue}
     * @param itemClass   the {@link Class} type which will be used as {@link BlockingQueue} items
     * @return queue      the {@link BlockingQueue} implementation
     */
    public static <T> BlockingQueue<T> createQueue(Collection<? extends T> collection, Class<T> itemClass) {
        if (useUnsafe) {
            return new LinkedTransferQueue<T>(collection);
        } else {
            return new LegacyLinkedTransferQueue<T>(collection);
        }
    }
}
