/*
 * Copyright 2016 The Netty Project
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
package io.netty.util.concurrent;

import io.netty.util.concurrent.AutoScalingEventExecutorChooserFactory.AutoScalingUtilizationMetric;

import java.util.List;

/**
 * Factory that creates new {@link EventExecutorChooser}s.
 */
public interface EventExecutorChooserFactory {

    /**
     * Returns a new {@link EventExecutorChooser}.
     */
    EventExecutorChooser newChooser(EventExecutor[] executors);

    /**
     * Chooses the next {@link EventExecutor} to use.
     */
    interface EventExecutorChooser {

        /**
         * Returns the new {@link EventExecutor} to use.
         */
        EventExecutor next();
    }

    /**
     * An {@link EventExecutorChooser} that exposes metrics for observation.
     */
    interface ObservableEventExecutorChooser extends EventExecutorChooser {

        /**
         * Returns the current number of active {@link EventExecutor}s.
         * @return the number of active executors.
         */
        int activeExecutorCount();

        /**
         * Returns a list containing the last calculated utilization for each
         * {@link EventExecutor} in the group.
         *
         * @return an umodifiable view of the executor utilizations.
         */
        List<AutoScalingUtilizationMetric> executorUtilizations();
    }
}
