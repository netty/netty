/*
 * Copyright 2021 The Netty Project
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
package io.netty.channel;

import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.QueueFactory;
import io.netty.util.internal.UnstableApi;
import io.netty.util.internal.telemetry.CycleBufferQueueEliminationStrategy;
import io.netty.util.internal.telemetry.EliminationHookQueue;

import java.util.Collection;
import java.util.Collections;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@UnstableApi
public class EventLoopTasksLatencyMonitorFactory implements EventLoopTaskQueueFactory {
    private final int bufferSize;
    private final Queue<EliminationHookQueue<Runnable>> instances =
            new ConcurrentLinkedQueue<EliminationHookQueue<Runnable>>();

    public EventLoopTasksLatencyMonitorFactory(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    @Override
    public EliminationHookQueue<Runnable> newTaskQueue(final int maxCapacity) {
        final EliminationHookQueue<Runnable> queue = new EliminationHookQueue<Runnable>(
                new QueueFactory() {
                    @Override
                    public <E> Queue<E> newQueue() {
                        return maxCapacity == Integer.MAX_VALUE? PlatformDependent.<E>newMpscQueue()
                                : PlatformDependent.<E>newMpscQueue(maxCapacity);
                    }
                },
                new CycleBufferQueueEliminationStrategy(bufferSize)
        );
        instances.add(queue);
        return queue;
    }

    public Collection<EliminationHookQueue<Runnable>> getInstances() {
        return Collections.unmodifiableCollection(instances);
    }
}
