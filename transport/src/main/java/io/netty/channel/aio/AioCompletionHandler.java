/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel.aio;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;

import java.nio.channels.CompletionHandler;

/**
 * Special {@link CompletionHandler} which makes sure that the callback methods gets executed in the {@link EventLoop}
 */
public abstract class AioCompletionHandler<V, A extends Channel> implements CompletionHandler<V, A> {

    // According to JDK AIO documentation, the ExecutorService a user specified must not call the Runnable given by
    // JDK AIO implementation directly.  However, we violates that rull by calling Runnable.run() directly for
    // optimization purposes, and it can result in infinite recursion in combination with the fact that the JDK AIO
    // implementation often makes recursive invocations.  Therefore, we must make sure we don't go too deep in the
    // stack.
    private static final int MAX_STACK_DEPTH = 8;
    private static final ThreadLocal<Integer> STACK_DEPTH = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return 0;
        }
    };

    /**
     * See {@link CompletionHandler#completed(Object, Object)}
     */
    protected abstract void completed0(V result, A channel);

    /**
     * Set {@link CompletionHandler#failed(Throwable, Object)}
     */
    protected abstract void failed0(Throwable exc, A channel);

    @Override
    public final void completed(final V result, final A channel) {
        EventLoop loop = channel.eventLoop();
        if (loop.inEventLoop()) { // if the current thread is executed in the Event Loop
            Integer d = STACK_DEPTH.get();
            if (d < MAX_STACK_DEPTH) {
                STACK_DEPTH.set(d + 1);
                try {
                    completed0(result, channel);
                } finally {
                    STACK_DEPTH.set(d);
                }
            } else {
                // schedule it with a special runnable to make sure we keep the right
                // order and exist the recursive call to prevent stackoverflow
                loop.execute(new AioEventLoop.RecursionBreakingRunnable() {
                    @Override
                    public void run() {
                        completed0(result, channel);
                    }
                });
            }
        } else {
            loop.execute(new Runnable() {
                @Override
                public void run() {
                    completed0(result, channel);
                }
            });
        }
    }

    @Override
    public final void failed(final Throwable exc, final A channel) {
        EventLoop loop = channel.eventLoop();
        if (loop.inEventLoop()) {
            Integer d = STACK_DEPTH.get();
            if (d < MAX_STACK_DEPTH) {
                STACK_DEPTH.set(d + 1);
                try {
                    failed0(exc, channel);
                } finally {
                    STACK_DEPTH.set(d);
                }
            } else {
                // schedule it with a special runnable to make sure we keep the right
                // order and exist the recursive call to prevent stackoverflow
                loop.execute(new AioEventLoop.RecursionBreakingRunnable() {
                    @Override
                    public void run() {
                        failed0(exc, channel);
                    }
                });
            }
        } else {
            loop.execute(new Runnable() {
                @Override
                public void run() {
                    failed0(exc, channel);
                }
            });
        }
    }
}
