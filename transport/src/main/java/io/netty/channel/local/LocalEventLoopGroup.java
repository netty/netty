/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel.local;

import io.netty.channel.DefaultEventLoopGroup;

import java.util.concurrent.ThreadFactory;

/**
 * @deprecated Use {@link DefaultEventLoopGroup} instead.
 */
@Deprecated
public class LocalEventLoopGroup extends DefaultEventLoopGroup {

    /**
     * Create a new instance with the default number of threads.
     */
    public LocalEventLoopGroup() { }

    /**
     * Create a new instance
     *
     * @param nThreads          the number of threads to use
     */
    public LocalEventLoopGroup(int nThreads) {
        super(nThreads);
    }

    /**
     * Create a new instance with the default number of threads and the given {@link ThreadFactory}.
     *
     * @param threadFactory     the {@link ThreadFactory} or {@code null} to use the default
     */
    public LocalEventLoopGroup(ThreadFactory threadFactory) {
        super(0, threadFactory);
    }

    /**
     * Create a new instance
     *
     * @param nThreads          the number of threads to use
     * @param threadFactory     the {@link ThreadFactory} or {@code null} to use the default
     */
    public LocalEventLoopGroup(int nThreads, ThreadFactory threadFactory) {
        super(nThreads, threadFactory);
    }
}
