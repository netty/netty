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

package io.netty.util.concurrent;

import io.netty.util.metrics.EventExecutorMetrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Abstract base class for {@link EventExecutorScheduler} implementations.
 */
public abstract class AbstractEventExecutorScheduler<T1 extends EventExecutor, T2 extends EventExecutorMetrics>
        implements EventExecutorScheduler<T1, T2> {

    protected final ArrayList<T1> children = new ArrayList<T1>();
    private Set<T1> readonlyChildren;

    @Override
    public final void addChild(T1 executor, T2 metrics) {
        if (executor == null) {
            throw new NullPointerException("executor");
        }

        children.add(executor);
        readonlyChildren = null;
    }

    @Override
    public final Set<T1> children() {
        if (readonlyChildren == null) {
            Set<T1> childrenSet = new LinkedHashSet<T1>(children);
            readonlyChildren = Collections.unmodifiableSet(childrenSet);
        }

        return readonlyChildren;
    }
}
