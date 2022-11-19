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
package io.netty.channel;

import io.netty.util.concurrent.OrderedEventExecutor;

/**
 * Reactor 线程模型的事件处理引擎，每个 EventLoop 线程维护一个 Selector 选择器和任务队列 TaskQueue，主要负责 IO 事件、普通任务和定时任务
 *
 * 1. 一个 EventLoopGroup 包含一个或者多个 EventLoop；
 * 2. 一个 EventLoop 在它的生命周期内只和一个 Thread 绑定；
 * 3. 所有由 EventLoop 处理的 IO 事件都将在它专有的 Thread 上被处理；
 * 4. 一个 Channel 在它的生命周期内只注册于一个EventLoop；
 * 5. 一个 EventLoop 可能会被分配给一个或多个 Channel；
 *
 * Will handle all the I/O operations for a {@link Channel} once registered.
 *
 * One {@link EventLoop} instance will usually handle more than one {@link Channel} but this may depend on
 * implementation details and internals.
 *
 */
public interface EventLoop extends OrderedEventExecutor, EventLoopGroup {
    @Override
    EventLoopGroup parent();
}
