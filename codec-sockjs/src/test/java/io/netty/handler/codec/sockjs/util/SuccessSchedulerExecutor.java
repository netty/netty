/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.sockjs.util;

import io.netty.handler.codec.sockjs.util.StubEmbeddedEventLoop.SchedulerExecutor;
import io.netty.util.concurrent.ScheduledFuture;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A concrete {@link SchedulerExecutor} that will not execute the tasks but
 * instead simply return a successful {@link ScheduledFuture}.
 */
public class SuccessSchedulerExecutor implements SchedulerExecutor {

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return success();
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return success();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return success();
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return success();
    }

    @SuppressWarnings("unchecked")
    private static <V> ScheduledFuture<V> success() {
        final ScheduledFuture<V> future = mock(ScheduledFuture.class);
        when(future.isSuccess()).thenReturn(Boolean.TRUE);
        when(future.isDone()).thenReturn(Boolean.TRUE);
        when(future.isCancelled()).thenReturn(Boolean.FALSE);
        return future;
    }
}

