/*
 * Copyright 2014 The Netty Project
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
package io.netty.util.internal;

import io.netty.util.concurrent.EventExecutor;

/**
 * {@link java.lang.Runnable} which represent and IO-Task and should be executed by an {@link EventExecutor}.
 *
 * <strong>It is important this will not be reused. After submitted it is not allowed to get submitted again!</strong>
 */
public abstract class OneTimeTask implements Runnable {

    private static final long nextOffset;

    static {
        if (PlatformDependent0.hasUnsafe()) {
            try {
                nextOffset = PlatformDependent0.UNSAFE.objectFieldOffset(
                        OneTimeTask.class.getDeclaredField("tail"));
            } catch (Throwable t) {
                throw new ExceptionInInitializerError(t);
            }
        } else {
            nextOffset = -1;
        }
    }

    @SuppressWarnings("unused")
    private volatile OneTimeTask tail;

    // Only use from ConcurrentSCMPQueue and so we are sure Unsafe is present
    @SuppressWarnings("unchecked")
    final OneTimeTask next() {
        return (OneTimeTask) PlatformDependent0.UNSAFE.getObjectVolatile(this, nextOffset);
    }

    // Only use from ConcurrentSCMPQueue and so we are sure Unsafe is present
    final void setNext(final OneTimeTask newNext) {
        PlatformDependent0.UNSAFE.putOrderedObject(this, nextOffset, newNext);
    }
}
