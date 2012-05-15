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
package io.netty.handler.execution;


import java.util.concurrent.Executor;

import io.netty.util.ExternalResourceReleasable;
import io.netty.util.internal.ExecutorUtil;

/**
 * A special {@link Executor} which allows to chain a series of
 * {@link Executor}s and {@link ChannelEventRunnableFilter}.
 */
public class ChainedExecutor implements Executor, ExternalResourceReleasable {

    private final Executor cur;
    private final Executor next;
    private final ChannelEventRunnableFilter filter;
    
    /**
     * Create a new {@link ChainedExecutor} which will used the given {@link ChannelEventRunnableFilter} to see if the {@link #cur} {@link Executor} should get used.
     * Otherwise it will pass the work to the {@link #next} {@link Executor} 
     * 
     * @param filter  the {@link ChannelEventRunnableFilter} which will be used to check if the {@link ChannelEventRunnable} should be passed to the cur or next {@link Executor}
     * @param cur     the {@link Executor} to use if the {@link ChannelEventRunnableFilter} match
     * @param next    the {@link Executor} to use if the {@link ChannelEventRunnableFilter} does not match
     */
    public ChainedExecutor(ChannelEventRunnableFilter filter, Executor cur, Executor next) {
        if (filter == null) {
            throw new NullPointerException("filter");
        }
        if (cur == null) {
            throw new NullPointerException("cur");
        }
        if (next == null) {
            throw new NullPointerException("next");
        }

        this.filter = filter;
        this.cur = cur;
        this.next = next;
    }
    
    /**
     * Execute the passed {@link ChannelEventRunnable} with the current {@link Executor} if the {@link ChannelEventRunnableFilter} match. 
     * Otherwise pass it to the next {@link Executor} in the chain.
     */
    @Override
    public void execute(Runnable command) {
        assert command instanceof ChannelEventRunnable;
        if (filter.filter((ChannelEventRunnable) command)) {
            cur.execute(command);
        } else {
            next.execute(command);
        }
    }

    @Override
    public void releaseExternalResources() {
        ExecutorUtil.terminate(cur, next);
        releaseExternal(cur);
        releaseExternal(next);
    }

    
    private static void releaseExternal(Executor executor) {
        if (executor instanceof ExternalResourceReleasable) {
            ((ExternalResourceReleasable) executor).releaseExternalResources();
        }
    }
}
