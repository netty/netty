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
package io.netty.channel.socket.aio;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;

import java.nio.channels.CompletionHandler;

/**
 * Special {@link CompletionHandler} which makes sure that the callback methods gets executed in the {@link EventLoop}
 *
 *
 */
abstract class AioCompletionHandler<V, A extends Channel> implements CompletionHandler<V, A> {

    /**
     * See {@link CompletionHandler#completed(Object, Object)}
     */
    protected abstract void completed0(V result, A channel);

    /**
     * Set {@link CompletionHandler#failed(Throwable, Object)}
     */
    protected abstract void failed0(Throwable exc, A channel);

    private static final int MAX_STACK_DEPTH = 4;
    private static final ThreadLocal<Integer> STACK_DEPTH = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return 0;
        }
    };

    @Override
    public final void completed(final V result, final A channel) {
        EventLoop loop = channel.eventLoop();
        if (loop.inEventLoop()) {
            Integer d = STACK_DEPTH.get();
            if (d < MAX_STACK_DEPTH) {
                STACK_DEPTH.set(d + 1);
                try {
                    completed0(result, channel);
                } finally {
                    STACK_DEPTH.set(d);
                }
                return;
            }
        }

        loop.execute(new Runnable() {
            @Override
            public void run() {
                completed0(result, channel);
            }
        });
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
                return;
            }
        }

        loop.execute(new Runnable() {
            @Override
            public void run() {
                failed0(exc, channel);
            }
        });
    }
}
