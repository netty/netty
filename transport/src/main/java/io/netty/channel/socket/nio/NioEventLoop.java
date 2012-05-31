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

package io.netty.channel.socket.nio;

import io.netty.channel.EventLoopFactory;
import io.netty.channel.MultithreadEventLoop;

import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.ThreadFactory;

public class NioEventLoop extends MultithreadEventLoop {

    public NioEventLoop() {
        this(DEFAULT_POOL_SIZE);
    }

    public NioEventLoop(int nThreads) {
        this(nThreads, DEFAULT_THREAD_FACTORY);
    }

    public NioEventLoop(int nThreads, ThreadFactory threadFactory) {
        this(nThreads, threadFactory, SelectorProvider.provider());
    }

    public NioEventLoop(int nThreads, ThreadFactory threadFactory, final SelectorProvider selectorProvider) {
        super(new EventLoopFactory<NioChildEventLoop>() {
            @Override
            public NioChildEventLoop newEventLoop(ThreadFactory threadFactory) throws Exception {
                return new NioChildEventLoop(threadFactory, selectorProvider);
            }

        }, nThreads, threadFactory);
    }
}
